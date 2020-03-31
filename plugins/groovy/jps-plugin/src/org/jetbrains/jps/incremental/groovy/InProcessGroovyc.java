// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.ClassDependencyLoader;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * @author peter
 */
final class InProcessGroovyc implements GroovycFlavor {
  private static final Logger LOG = Logger.getInstance(InProcessGroovyc.class);
  private static final Pattern GROOVY_ALL_JAR_PATTERN = Pattern.compile("groovy-all(-(.*))?\\.jar");
  private static final Pattern GROOVY_JAR_PATTERN = Pattern.compile("groovy(-(.*))?\\.jar");
  private static final Pattern GROOVY_ECLIPSE_BATCH_PATTERN = Pattern.compile("groovy-eclipse-batch-(.*)\\.jar");
  private static final Pattern GROOVY_JPS_PLUGIN_JARS_PATTERN = Pattern.compile("groovy-((jps-)|(rt-)|(constants-rt-)).*\\.jar");
  private static final ThreadPoolExecutor ourExecutor = ConcurrencyUtil.newSingleThreadExecutor("Groovyc");
  private static final String GROOVYC_FINISHED = "Groovyc finished";
  private static SoftReference<Pair<String, ClassLoader>> ourParentLoaderCache;
  private static final UrlClassLoader.CachePool ourLoaderCachePool = UrlClassLoader.createCachePool();
  private final Collection<String> myOutputs;
  private final boolean myHasStubExcludes;
  private final boolean mySharedPool;

  InProcessGroovyc(Collection<String> outputs, boolean hasStubExcludes) {
    myOutputs = outputs;
    myHasStubExcludes = hasStubExcludes;
    mySharedPool = SystemProperties.getBooleanProperty("groovyc.in.process.shared.pool", true);
  }

  @Override
  public GroovycContinuation runGroovyc(Collection<String> compilationClassPath,
                                        boolean forStubs,
                                        CompileContext context,
                                        File tempFile,
                                        GroovycOutputParser parser, String byteCodeTargetLevel) throws Exception {
    boolean jointPossible = forStubs && !myHasStubExcludes;
    LinkedBlockingQueue<Object> mailbox = jointPossible && SystemProperties.getBooleanProperty("groovyc.joint.compilation", true)
                                          ? new LinkedBlockingQueue<>() : null;

    final JointCompilationClassLoader loader = createCompilationClassLoader(compilationClassPath);
    if (loader == null) {
      parser.addCompilerMessage(parser.reportNoGroovy(null));
      return null;
    }

    final ExecutorService executorService = mySharedPool ? SharedThreadPool.getInstance() : ourExecutor;
    final Future<Void> future = executorService.submit(() -> {
      try {
        runGroovycInThisProcess(loader, forStubs, context, tempFile, parser, byteCodeTargetLevel, mailbox, mySharedPool);
      }
      finally {
        if (mailbox != null) {
          mailbox.offer(GROOVYC_FINISHED);
        }
      }
      return null;
    });
    if (mailbox == null) {
      future.get();
      return null;
    }

    return waitForStubGeneration(future, mailbox, parser, loader);
  }

  @Nullable
  private static GroovycContinuation waitForStubGeneration(Future<Void> future,
                                                           LinkedBlockingQueue<?> mailbox,
                                                           GroovycOutputParser parser,
                                                           JointCompilationClassLoader loader) throws InterruptedException {
    while (true) {
      Object msg = mailbox.poll(1, TimeUnit.MINUTES);
      if (GROOVYC_FINISHED.equals(msg)) {
        return null;
      }
      else if (msg instanceof Queue) {
        // a signal that stubs are generated, so we can continue to other builders
        // and use the passed queue to notify the suspended thread to continue compiling groovy

        //noinspection unchecked
        Queue<String> toGroovyc = (Queue<String>)msg;
        loader.resetCache();
        return createContinuation(future, toGroovyc, parser);
      }
      else if (msg != null) {
        throw new AssertionError("Unknown message: " + msg);
      }
    }
  }

  @NotNull
  private static GroovycContinuation createContinuation(Future<Void> future,
                                                        @NotNull Queue<String> mailbox,
                                                        GroovycOutputParser parser) {
    return new GroovycContinuation() {
      @NotNull
      @Override
      public GroovycOutputParser continueCompilation() throws Exception {
        parser.onContinuation();
        mailbox.offer(GroovyRtConstants.JAVAC_COMPLETED);
        future.get();
        return parser;
      }

      @Override
      public void buildAborted() {
        mailbox.offer(GroovyRtConstants.BUILD_ABORTED);
      }
    };
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void runGroovycInThisProcess(ClassLoader loader,
                                              boolean forStubs,
                                              CompileContext context,
                                              File tempFile,
                                              final GroovycOutputParser parser,
                                              @Nullable String byteCodeTargetLevel,
                                              @Nullable Queue<? super Object> mailbox,
                                              boolean sharedPool) throws IOException {
    PrintStream oldOut = sharedPool ? null : System.out;
    PrintStream oldErr = sharedPool? null : System.err;
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();

    PrintStream out = createStream(parser, ProcessOutputTypes.STDOUT, oldOut);
    PrintStream err = createStream(parser, ProcessOutputTypes.STDERR, oldErr);
    if (!sharedPool) {
      System.setOut(out);
      System.setErr(err);
    }
    Thread.currentThread().setContextClassLoader(loader);
    try {
      Class<?> runnerClass = loader.loadClass("org.jetbrains.groovy.compiler.rt.GroovycRunner");
      Method intMain = runnerClass.getDeclaredMethod("intMain2",
                                                     boolean.class, boolean.class, boolean.class,
                                                     String.class, String.class, String.class,
                                                     Queue.class, PrintStream.class, PrintStream.class);
      JpsGroovySettings groovySettings = JpsGroovycRunner.getGroovyCompilerSettings(context);
      Integer exitCode = (Integer)intMain.invoke(null,
                                                 groovySettings.invokeDynamic, false, forStubs,
                                                 tempFile.getPath(), groovySettings.configScript, byteCodeTargetLevel,
                                                 mailbox, out, err);
      parser.notifyFinished(exitCode);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      out.flush();
      err.flush();

      if (!sharedPool) {
        System.setOut(oldOut);
        System.setErr(oldErr);
      }
      Thread.currentThread().setContextClassLoader(oldLoader);
    }
  }

  @Nullable
  private JointCompilationClassLoader createCompilationClassLoader(Collection<String> compilationClassPath) throws Exception {
    ClassLoader parent = obtainParentLoader(compilationClassPath);

    ClassLoader groovyClassLoader;
    try {
      ClassLoader auxiliary = parent != null ? parent : buildCompilationClassLoader(compilationClassPath, null).get();
      Class<?> gcl = auxiliary.loadClass("groovy.lang.GroovyClassLoader");
      groovyClassLoader = (ClassLoader)gcl.getConstructor(ClassLoader.class)
        .newInstance(parent != null ? parent : ClassLoaderUtil.getPlatformLoaderParentIfOnJdk9());
    }
    catch (ClassNotFoundException e) {
      return null;
    }

    return new JointCompilationClassLoader(buildCompilationClassLoader(compilationClassPath, groovyClassLoader));
  }

  private UrlClassLoader.Builder buildCompilationClassLoader(Collection<String> compilationClassPath, ClassLoader parent)
    throws MalformedURLException {
    return UrlClassLoader.build().
      urls(toUrls(compilationClassPath)).parent(parent).allowLock().
      useCache(ourLoaderCachePool, new UrlClassLoader.CachingCondition() {
        @Override
        public boolean shouldCacheData(@NotNull URL url) {
          try {
            String file = FileUtil.toCanonicalPath(new File(url.toURI()).getPath());
            for (String output : myOutputs) {
              if (FileUtil.startsWith(output, file)) {
                return false;
              }
            }
            return true;
          }
          catch (URISyntaxException e) {
            LOG.info(e);
            return false;
          }
        }
      });
  }

  static String evaluatePathToGroovyAllForParentClassloader(Collection<String> compilationClassPath) {
    if (!SystemInfoRt.IS_AT_LEAST_JAVA9 && !"true".equals(System.getProperty("groovyc.reuse.compiler.classes", "true"))) {
      return null;
    }

    List<String> groovyJars = ContainerUtil.findAll(compilationClassPath, s -> {
      String fileName = PathUtilRt.getFileName(s);
      return GROOVY_ALL_JAR_PATTERN.matcher(fileName).matches() || GROOVY_JAR_PATTERN.matcher(fileName).matches();
    });
    ContainerUtil.retainAll(groovyJars, s -> {
      String fileName = PathUtilRt.getFileName(s);
      return !GROOVY_ECLIPSE_BATCH_PATTERN.matcher(fileName).matches() && !GROOVY_JPS_PLUGIN_JARS_PATTERN.matcher(fileName).matches();
    });

    LOG.debug("Groovy jars: " + groovyJars);

    if (groovyJars.size() != 1 || !GROOVY_ALL_JAR_PATTERN.matcher(PathUtilRt.getFileName(groovyJars.get(0))).matches()) {
      // avoid complications caused by caching classes from several groovy versions in classpath
      return null;
    }

    return groovyJars.get(0);
  }

  @Nullable
  private static ClassLoader obtainParentLoader(Collection<String> compilationClassPath) throws MalformedURLException {
    String groovyAll = evaluatePathToGroovyAllForParentClassloader(compilationClassPath);
    if (groovyAll == null) return null;

    Pair<String, ClassLoader> pair = SoftReference.dereference(ourParentLoaderCache);
    if (pair != null && pair.first.equals(groovyAll)) {
      return pair.second;
    }

    final ClassDependencyLoader checkWellFormed = new ClassDependencyLoader() {
      @Override
      protected void loadClassDependencies(Class aClass) throws ClassNotFoundException {
        if (!isCompilerCoreClass(aClass.getName()) || !(aClass.getClassLoader() instanceof UrlClassLoader)) {
          super.loadClassDependencies(aClass);
        }
      }

      private boolean isCompilerCoreClass(String name) {
        if (name.startsWith("groovyjarjar")) {
          return true;
        }
        if (name.startsWith("org.codehaus.groovy.")) {
          String tail = name.substring("org.codehaus.groovy.".length());
          if (tail.startsWith("ast") ||
              tail.startsWith("classgen") ||
              tail.startsWith("tools.javac") ||
              tail.startsWith("antlr") ||
              tail.startsWith("vmplugin") ||
              tail.startsWith("reflection") ||
              tail.startsWith("control")) {
            return true;
          }
          if (tail.startsWith("runtime") && name.contains("GroovyMethods")) {
            return true;
          }
        }
        return false;
      }
    };
    UrlClassLoader.Builder builder = UrlClassLoader.build();
    builder.urls(toUrls(ContainerUtil.concat(GroovyBuilder.getGroovyRtRoots(), Collections.singletonList(groovyAll))));
    builder.allowLock();
    builder.useCache(ourLoaderCachePool, new UrlClassLoader.CachingCondition() {
      @Override
      public boolean shouldCacheData(
        @NotNull URL url) {
        return true;
      }
    });
    ClassLoaderUtil.addPlatformLoaderParentIfOnJdk9(builder);
    UrlClassLoader groovyAllLoader = builder.get();

    ClassLoader wrapper = new URLClassLoader(new URL[0], groovyAllLoader) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("groovy.grape.")) {
          // grape depends on Ivy which is not included in this class loader
          throw new ClassNotFoundException(name);
        }
        try {
          return checkWellFormed.loadDependencies(super.loadClass(name, resolve));
        }
        catch (NoClassDefFoundError e) {
          // We might attempt to load some class in groovy-all.jar that depends on a class from another library
          // (e.g. GroovyTestCase extends TestCase).
          // We don't want groovyc's resolve to stop at this point.
          // Let's try in the child class loader which contains full compilation class with all libraries, including groovy-all.
          // For this to happen we should throw ClassNotFoundException
          throw new ClassNotFoundException(name, e);
        }
      }
    };

    ourParentLoaderCache = new SoftReference<>(Pair.create(groovyAll, wrapper));
    return wrapper;
  }


  @NotNull
  private static List<URL> toUrls(Collection<String> paths) throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    for (String s : paths) {
      urls.add(new File(s).toURI().toURL());
    }
    return urls;
  }

  @NotNull
  private static PrintStream createStream(@NotNull GroovycOutputParser parser,
                                          @NotNull Key<?> type,
                                          @Nullable("null means not overridden") PrintStream overridden) throws IOException {
    final Thread thread = Thread.currentThread();
    OutputStream out = new OutputStream() {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      boolean hasLineSeparator = false;

      @Override
      public void write(int b) throws IOException {
        if (overridden != null && Thread.currentThread() != thread) {
          overridden.write(b);
          return;
        }

        if (hasLineSeparator && !isLineSeparator(b)) {
          flush();
        }
        else {
          hasLineSeparator |= isLineSeparator(b);
        }
        line.write(b);
      }

      private boolean isLineSeparator(int b) {
        return b == '\n' || b == '\r';
      }

      @Override
      public void flush() throws IOException {
        if (overridden != null && Thread.currentThread() != thread) {
          overridden.flush();
          return;
        }

        if (line.size() > 0) {
          parser.notifyTextAvailable(StringUtil.convertLineSeparators(line.toString("UTF-8")), type);
          line = new ByteArrayOutputStream();
          hasLineSeparator = false;
        }
      }
    };
    return new PrintStream(out, false, "UTF-8");
  }
}
