/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.lang;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import javax.crypto.KeyAgreement;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.junit.Assert.*;

/**
 * @author Dmitry Avdeev
 */
public class UrlClassLoaderTest {
  @Test
  public void testBootstrapResources() {
    String name = "com/sun/xml/internal/messaging/saaj/soap/LocalStrings.properties";
    assertNotNull(UrlClassLoaderTest.class.getClassLoader().getResourceAsStream(name));
    assertNull(UrlClassLoader.build().get().getResourceAsStream(name));
    assertNotNull(UrlClassLoader.build().allowBootstrapResources().get().getResourceAsStream(name));
  }

  @Test
  public void testNonCanonicalPaths() throws IOException {
    File root = FileUtil.createTempDirectory("testNonCanonicalPaths", "");
    File subDir = createTestDir(root, "dir");
    createTestFile(subDir, "a.txt");

    URL url = root.toURI().toURL();
    UrlClassLoader customCl = UrlClassLoader.build().urls(url).get();
    try (URLClassLoader standardCl = new URLClassLoader(new URL[]{url})) {
      String relativePathToFile = "dir/a.txt";
      assertNotNull(customCl.getResourceAsStream(relativePathToFile));
      assertNotNull(standardCl.findResource(relativePathToFile));

      String nonCanonicalPathToFile = "dir/a.txt/../a.txt";
      assertNotNull(customCl.getResourceAsStream(nonCanonicalPathToFile));
      assertNotNull(standardCl.findResource(nonCanonicalPathToFile));

      String absolutePathToFile = "/dir/a.txt";
      assertNotNull(customCl.getResourceAsStream(absolutePathToFile)); // non-standard CL behavior
      assertNull(standardCl.findResource(absolutePathToFile));

      String absoluteNonCanonicalPathToFile = "/dir/a.txt/../a.txt";
      assertNotNull(customCl.getResourceAsStream(absoluteNonCanonicalPathToFile));  // non-standard CL behavior
      assertNull(standardCl.findResource(absoluteNonCanonicalPathToFile));
    }
  }

  @Test
  public void testConcurrentResourceLoading() throws Exception {
    List<String> resourceNames = ContainerUtil.newArrayList();
    List<URL> urls = ContainerUtil.newArrayList();

    File[] libs = ObjectUtils.assertNotNull(new File(PathManager.getHomePathFor(UrlClassLoader.class) + "/lib").listFiles());
    for (File file : libs) {
      if (file.getName().endsWith(".jar")) {
        urls.add(file.toURI().toURL());

        try (ZipFile zipFile = new ZipFile(file)) {
          Enumeration<? extends ZipEntry> entries = zipFile.entries();
          while (entries.hasMoreElements()) {
            resourceNames.add(entries.nextElement().getName());
          }
        }
      }
    }

    int attemptCount = 1000;
    int threadCount = 3;
    int resourceCount = 20;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount, ConcurrencyUtil.newNamedThreadFactory("concurrent loading"));
    try {
      Random random = new Random();
      UrlClassLoader.CachePool pool = UrlClassLoader.createCachePool();
      for (int attempt = 0; attempt < attemptCount; attempt++) {
        // fails also without cache pool (but with cache enabled), but takes much longer
        UrlClassLoader loader = UrlClassLoader.build().urls(urls).parent(null).allowLock(true).useCache(pool, (url) -> true).get();
        List<String> namesToLoad = ContainerUtil.newArrayList();
        for (int j = 0; j < resourceCount; j++) {
          namesToLoad.add(resourceNames.get(random.nextInt(resourceNames.size())));
        }

        List<Future> futures = ContainerUtil.newArrayList();
        for (int i = 0; i < threadCount; i++) {
          futures.add(executor.submit(() -> {
            for (String name : namesToLoad) {
              try {
                assertNotNull(findResource(loader, name, random.nextBoolean()));
              }
              catch (Throwable e) {
                System.err.println("Failed loading " + name);
                throw new RuntimeException(e);
              }
            }
          }));
        }

        for (Future future : futures) {
          future.get();
        }
      }
    }
    finally {
      executor.shutdownNow();
      executor.awaitTermination(1000, TimeUnit.SECONDS);
    }
  }

  @Test
  public void testInvalidJarsInClassPath() throws IOException {
    File sadHill = createTestDir("testInvalidJarsInClassPath");
    try {
      String entryName = "test_res_dir/test_res.txt";
      File theGood = createTestJar(createTestFile(sadHill, "1_normal.jar"), entryName, "-");
      File theBad = createTestFile(sadHill, "2_broken.jar", new String(new char[1024]));

      UrlClassLoader flat = UrlClassLoader.build().urls(theBad.toURI().toURL(), theGood.toURI().toURL()).useLazyClassloadingCaches(false).get();
      assertNotNull(findResource(flat, entryName, false));

      String content = Attributes.Name.MANIFEST_VERSION + ": 1.0\n" +
                       Attributes.Name.CLASS_PATH + ": " + theBad.toURI().toURL() + " " + theGood.toURI().toURL() + "\n\n";
      File theUgly = createTestJar(createTestFile(sadHill, ClassPath.CLASSPATH_JAR_FILE_NAME_PREFIX + "_3.jar"), JarFile.MANIFEST_NAME, content);

      UrlClassLoader recursive = UrlClassLoader.build().urls(theUgly.toURI().toURL()).useLazyClassloadingCaches(false).get();
      assertNotNull(findResource(recursive, entryName, false));
    }
    finally {
      FileUtil.delete(sadHill);
    }
  }

  @Test
  public void testDirEntry() throws IOException {
    File sadHill = createTestDir("testDirEntry");
    try {
      String resourceDirName = "test_res_dir";
      String resourceDirName2 = "test_res_dir2";
      File theGood = createTestJar(createTestFile(sadHill, "1_normal.jar"), resourceDirName + "/test_res.txt", "-", resourceDirName2 + "/", null);
      UrlClassLoader flat = UrlClassLoader.build().urls(theGood.toURI().toURL()).get();

      String resourceDirNameWithSlash = resourceDirName + "/";
      String resourceDirNameWithSlash_ = "/" + resourceDirNameWithSlash;
      String resourceDirNameWithSlash2 = resourceDirName2 + "/";
      String resourceDirNameWithSlash2_ = "/" + resourceDirNameWithSlash2;

      assertNull(findResource(flat, resourceDirNameWithSlash, false));
      assertNull(findResource(flat, resourceDirNameWithSlash_, false));
      assertNotNull(findResource(flat, resourceDirNameWithSlash2, false));
      assertNotNull(findResource(flat, resourceDirNameWithSlash2_, false)); // non-standard CL behavior

      try (URLClassLoader recursive2 = new URLClassLoader(new URL[]{theGood.toURI().toURL()})) {
        assertNotNull(recursive2.findResource(resourceDirNameWithSlash2));
        assertNull(recursive2.findResource(resourceDirNameWithSlash2_));
        assertNull(recursive2.findResource(resourceDirNameWithSlash));
        assertNull(recursive2.findResource(resourceDirNameWithSlash_));
      }
    }
    finally {
      FileUtil.delete(sadHill);
    }
  }

  private static URL findResource(UrlClassLoader loader, String name, boolean findAll) {
    if (findAll) {
      try {
        Enumeration<URL> resources = loader.getResources(name);
        assertTrue(resources.hasMoreElements());
        return resources.nextElement();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return loader.findResource(name);
    }
  }

  @Test
  public void testFindDirWhenUsingCache() throws IOException {
    int counter = 1;
    for (String dirName : new String[]{ "dir", "dir/", "dir.class", "dir.class/"}) {
      for(String resourceName: new String[] {"a.class", "a.txt"} ) {
        File root = FileUtil.createTempDirectory("testFindDirWhenUsingCache", String.valueOf(counter++));
        File subDir = createTestDir(root, dirName);
        createTestFile(subDir, resourceName);

        URL url = root.toURI().toURL();

        try (URLClassLoader standardCl = new URLClassLoader(new URL[]{url})) {
          Enumeration<URL> resources = standardCl.findResources(dirName);
          assertTrue(resources.hasMoreElements());
          URL expectedResourceUrl = resources.nextElement();

          withCustomCachedClassloader(url, (customCl) -> {
            assertNull(customCl.findResource("SomeNonExistentResource.resource"));
            checkResourceUrlIsTheSame(customCl, dirName, expectedResourceUrl);
          });
          withCustomCachedClassloader(url, (customCl) -> checkResourceUrlIsTheSame(customCl, dirName, expectedResourceUrl));
        }
      }
    }
  }

  private static void checkResourceUrlIsTheSame(UrlClassLoader customCl, String resourceName, URL expectedResourceUrl) throws IOException {
    Enumeration<URL> customClResources = customCl.findResources(resourceName);
    assertTrue(customClResources.hasMoreElements());
    assertEquals(expectedResourceUrl, customClResources.nextElement());
  }

  private static void withCustomCachedClassloader(URL url, ThrowableConsumer<UrlClassLoader, IOException> testAction) throws IOException {
    testAction.consume(UrlClassLoader.build().useCache().urls(url).get());
  }

  /**
   * IDEA's class loader {@link UrlClassLoader} is optimized for speed but does not works correctly in a few cases.
   * For example it does not assign {@link java.security.CodeSource} to loaded classes. In such cases should be used
   * default class loader.
   *
   * @see IDEA-181010
   */
  @Test
  public void testFallbackForBlacklistedPackages() throws Exception {
    String className = "org.bouncycastle.jce.provider.BouncyCastleProvider";
    URL classUrl = UrlClassLoaderTest.class.getClassLoader().getResource(className.replace('.', '/') + ".class");
    assertEquals("jar", classUrl.getProtocol());
    classUrl = new URL(classUrl.toExternalForm().split("[!]", 2)[0].substring("jar:".length()));

    ClassLoader classLoader;
    Exception error;

    classLoader = UrlClassLoader.build()
      .urls(classUrl)
      .get();
    error = codeThatRegistersSecurityProvider(classLoader, className);
    assertEquals(SecurityException.class, error == null ? null : error.getClass());

    classLoader = UrlClassLoader.build()
      .urls(classUrl)
      .useDefaultClassLoaderForPrefixes("org.bouncycastle.")
      .get();
    error = codeThatRegistersSecurityProvider(classLoader, className);
    assertEquals(null, error == null ? null : error.getClass());
  }

  @Nullable
  private Exception codeThatRegistersSecurityProvider(ClassLoader classLoader, String className) {
    Class<?> providerClass;
    Provider provider;
    try {
      providerClass = classLoader.loadClass(className);
      provider = (Provider)providerClass.newInstance();
    }
    catch (Exception error) {
      throw new IllegalStateException(error);
    }
    Security.addProvider(provider);

    try {
      KeyAgreement.getInstance("DH", provider);
      return null;
    }
    catch (Exception error) {
      return error;
    }
  }
}