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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author peter
 */
class InProcessGroovyc {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.InProcessGroovyc");
  private static final Pattern GROOVY_ALL_JAR_PATTERN = Pattern.compile("groovy-all(-(.*))?\\.jar");
  private static SoftReference<Pair<String, ClassLoader>> ourParentLoaderCache;
  private static final UrlClassLoader.CachePool ourLoaderCachePool = UrlClassLoader.createCachePool();

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static void runGroovycInThisProcess(Collection<String> compilationClassPath,
                                      final Collection<String> outputs,
                                      List<String> programParams,
                                      final GroovycOutputParser parser)
    throws MalformedURLException {

    ClassLoader parent = obtainParentLoader(compilationClassPath);

    UrlClassLoader loader = UrlClassLoader.build().
      urls(toUrls(compilationClassPath)).parent(parent).
      useCache(ourLoaderCachePool, new UrlClassLoader.CachingCondition() {
        @Override
        public boolean shouldCacheData(@NotNull URL url) {
          try {
            String file = FileUtil.toCanonicalPath(new File(url.toURI()).getPath());
            for (String output : outputs) {
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
      }).get();

    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;
    ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();

    System.setOut(createStream(parser, ProcessOutputTypes.STDOUT, oldOut));
    System.setErr(createStream(parser, ProcessOutputTypes.STDERR, oldErr));
    Thread.currentThread().setContextClassLoader(loader);
    try {
      Class<?> runnerClass = loader.loadClass("org.jetbrains.groovy.compiler.rt.GroovycRunner");
      Method intMain = runnerClass.getDeclaredMethod("intMain", String[].class);
      Integer exitCode = (Integer)intMain.invoke(null, new Object[]{ArrayUtil.toStringArray(programParams)});
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
  private static ClassLoader obtainParentLoader(Collection<String> compilationClassPath) throws MalformedURLException {
    if (!"true".equals(System.getProperty("groovyc.reuse.compiler.classes", "true"))) {
      return null;
    }

    String groovyAll = ContainerUtil.find(compilationClassPath, new Condition<String>() {
      @Override
      public boolean value(String s) {
        return GROOVY_ALL_JAR_PATTERN.matcher(StringUtil.getShortName(s, '/')).matches();
      }
    });
    if (groovyAll == null) {
      return null;
    }

    Pair<String, ClassLoader> pair = SoftReference.dereference(ourParentLoaderCache);
    if (pair != null && pair.first.equals(groovyAll)) {
      return pair.second;
    }

    UrlClassLoader groovyAllLoader = 
      UrlClassLoader.build().urls(toUrls(Arrays.asList(GroovyBuilder.getGroovyRtRoot().getPath(), groovyAll))).useCache().get();
    ClassLoader wrapper = new URLClassLoader(new URL[0], groovyAllLoader) {
      @Override
      protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
          return super.loadClass(name, resolve);
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
      ;
    ourParentLoaderCache = new SoftReference<Pair<String, ClassLoader>>(Pair.create(groovyAll, wrapper));
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
        if (line.size() > 0) {
          parser.notifyTextAvailable(StringUtil.convertLineSeparators(line.toString()), type);
          line = new ByteArrayOutputStream();
          hasLineSeparator = false;
        }
      }

    });
  }
}
