/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ClassLoaderUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.ClassDependencyLoader;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author peter
 */
class InProcessGroovyc implements GroovycFlavor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.InProcessGroovyc");
  private static final Pattern GROOVY_ALL_JAR_PATTERN = Pattern.compile("groovy-all(-(.*))?\\.jar");
  private static final Pattern GROOVY_JAR_PATTERN = Pattern.compile("groovy(-(.*))?\\.jar");
  private static final Pattern GROOVY_ECLIPSE_BATCH_PATTERN = Pattern.compile("groovy-eclipse-batch-(.*)\\.jar");
  private static final ThreadPoolExecutor ourExecutor = ConcurrencyUtil.newSingleThreadExecutor("Groovyc");
  private static SoftReference<Pair<String, ClassLoader>> ourParentLoaderCache;
  private static final UrlClassLoader.CachePool ourLoaderCachePool = UrlClassLoader.createCachePool();
  private final Collection<String> myOutputs;
  private final boolean myHasStubExcludes;

  InProcessGroovyc(Collection<String> outputs, boolean hasStubExcludes) {
    myOutputs = outputs;
    myHasStubExcludes = hasStubExcludes;
  }

  @Override
  public GroovycContinuation runGroovyc(final Collection<String> compilationClassPath,
                                        final boolean forStubs,
                                        final JpsGroovySettings settings,
                                        final File tempFile,
                                        final GroovycOutputParser parser) throws Exception {
    boolean jointPossible = forStubs && !myHasStubExcludes;
    final LinkedBlockingQueue<String> mailbox = jointPossible && SystemProperties.getBooleanProperty("groovyc.joint.compilation", true)
                                                ? new LinkedBlockingQueue<>() : null;

    final JointCompilationClassLoader loader = createCompilationClassLoader(compilationClassPath);
    if (loader == null) {
      parser.addCompilerMessage(parser.reportNoGroovy());
      return null;
    }

    final Future<Void> future = ourExecutor.submit(() -> {
      runGroovycInThisProcess(loader, forStubs, settings, tempFile, parser, mailbox);
      return null;
    });
    if (mailbox == null) {
      future.get();
      return null;
    }

    return waitForStubGeneration(future, mailbox, parser, loader);
  }

  @Nullable
  private static GroovycContinuation waitForStubGeneration(final Future<Void> future,
                                                           final LinkedBlockingQueue<String> mailbox,
                                                           final GroovycOutputParser parser,
                                                           JointCompilationClassLoader loader) throws InterruptedException {
    while (true) {
      if (future.isDone()) {
        return null;
      }

      Object msg = mailbox.poll(10, TimeUnit.MILLISECONDS);
      if (GroovyRtConstants.STUBS_GENERATED.equals(msg)) {
        loader.resetCache();
        return createContinuation(future, mailbox, parser);
      }
      if (msg != null) {
        throw new AssertionError("Unknown message: " + msg);
      }
    }
  }

  @NotNull
  private static GroovycContinuation createContinuation(final Future<Void> future,
                                                        final LinkedBlockingQueue<String> mailbox,
                                                        final GroovycOutputParser parser) {
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
                                              JpsGroovySettings settings,
                                              File tempFile,
                                              final GroovycOutputParser parser,
                                              @Nullable Queue mailbox) throws Exception {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();

    System.setOut(createStream(parser, ProcessOutputTypes.STDOUT, oldOut));
    System.setErr(createStream(parser, ProcessOutputTypes.STDERR, oldErr));
    Thread.currentThread().setContextClassLoader(loader);
    try {
      Class<?> runnerClass = loader.loadClass("org.jetbrains.groovy.compiler.rt.GroovycRunner");
      Method intMain = runnerClass.getDeclaredMethod("intMain2", boolean.class, boolean.class, boolean.class, String.class, String.class, Queue.class);
      Integer exitCode = (Integer)intMain.invoke(null, settings.invokeDynamic, false, forStubs, tempFile.getPath(), settings.configScript, mailbox);
      parser.notifyFinished(exitCode);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    finally {
      System.out.flush();
      System.err.flush();

      System.setOut(oldOut);
      System.setErr(oldErr);
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
      groovyClassLoader = (ClassLoader)gcl.getConstructor(ClassLoader.class).newInstance(parent != null ? parent : ClassLoaderUtil.getPlatformLoaderParentIfOnJdk9());
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

  @Nullable
  private static ClassLoader obtainParentLoader(Collection<String> compilationClassPath) throws MalformedURLException {
    if (!SystemInfo.IS_AT_LEAST_JAVA9 && !"true".equals(System.getProperty("groovyc.reuse.compiler.classes", "true"))) {
      return null;
    }

    List<String> groovyJars = ContainerUtil.findAll(compilationClassPath, s -> {
      String fileName = StringUtil.getShortName(s, '/');
      return GROOVY_ALL_JAR_PATTERN.matcher(fileName).matches() || GROOVY_JAR_PATTERN.matcher(fileName).matches();
    });
    ContainerUtil.retainAll(groovyJars, s -> !GROOVY_ECLIPSE_BATCH_PATTERN.matcher(StringUtil.getShortName(s, '/')).matches());

    LOG.debug("Groovy jars: " + groovyJars);

    if (groovyJars.size() != 1 || !GROOVY_ALL_JAR_PATTERN.matcher(StringUtil.getShortName(groovyJars.get(0), '/')).matches()) {
      // avoid complications caused by caching classes from several groovy versions in classpath
      return null;
    }

    String groovyAll = groovyJars.get(0);
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
    List<URL> urls = ContainerUtil.newArrayList();
    for (String s : paths) {
      urls.add(new File(s).toURI().toURL());
    }
    return urls;
  }

  @NotNull
  private static PrintStream createStream(final GroovycOutputParser parser, final Key type, final PrintStream overridden) {
    final Thread thread = Thread.currentThread();
    return new PrintStream(new OutputStream() {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      boolean hasLineSeparator = false;

      @Override
      public void write(int b) throws IOException {
        if (Thread.currentThread() != thread) {
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
        if (Thread.currentThread() != thread) {
          overridden.flush();
          return;
        }

        if (line.size() > 0) {
          parser.notifyTextAvailable(StringUtil.convertLineSeparators(line.toString()), type);
          line = new ByteArrayOutputStream();
          hasLineSeparator = false;
        }
      }

    });
  }
}
