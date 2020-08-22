// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ThrowableConsumer;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
import static org.junit.Assume.assumeTrue;

/**
 * @author Dmitry Avdeev
 */
public class UrlClassLoaderTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  @ReviseWhenPortedToJDK("9")
  public void testBootstrapResources() {
    String name = JavaVersion.current().feature > 8 ? "META-INF/services/java.nio.file.spi.FileSystemProvider"
                                                    : "com/sun/xml/internal/messaging/saaj/soap/LocalStrings.properties";
    assertNotNull(ClassLoader.getSystemResourceAsStream(name));
    assertNull(UrlClassLoader.build().get().getResourceAsStream(name));
    assertNotNull(UrlClassLoader.build().allowBootstrapResources().get().getResourceAsStream(name));
  }

  @Test
  public void testNonCanonicalPaths() throws IOException {
    tempDir.newFile("dir/a.txt");

    URL url = tempDir.getRoot().toURI().toURL();
    UrlClassLoader customCl = UrlClassLoader.build().urls(url).get();
    try (URLClassLoader standardCl = new URLClassLoader(new URL[]{url})) {
      String relativePathToFile = "dir/a.txt";
      assertNotNull(customCl.findResource(relativePathToFile));
      assertNotNull(standardCl.findResource(relativePathToFile));

      String nonCanonicalPathToFile = "dir/a.txt/../a.txt";
      assertNotNull(customCl.findResource(nonCanonicalPathToFile));
      assertNotNull(standardCl.findResource(nonCanonicalPathToFile));

      String absolutePathToFile = "/dir/a.txt";
      assertNotNull(customCl.findResource(absolutePathToFile));  // non-standard CL behavior
      assertNull(standardCl.findResource(absolutePathToFile));

      String absoluteNonCanonicalPathToFile = "/dir/a.txt/../a.txt";
      assertNotNull(customCl.findResource(absoluteNonCanonicalPathToFile));  // non-standard CL behavior
      assertNull(standardCl.findResource(absoluteNonCanonicalPathToFile));
    }
  }

  @Test
  public void testConcurrentResourceLoading() throws Exception {
    List<String> resourceNames = new ArrayList<>();
    List<URL> urls = new ArrayList<>();

    File[] libs = Objects.requireNonNull(new File(PathManager.getHomePathFor(UrlClassLoader.class) + "/lib").listFiles());
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
        List<String> namesToLoad = new ArrayList<>();
        for (int j = 0; j < resourceCount; j++) {
          namesToLoad.add(resourceNames.get(random.nextInt(resourceNames.size())));
        }

        List<Future<?>> futures = new ArrayList<>();
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

        for (Future<?> future : futures) {
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
    String entryName = "test_res_dir/test_res.txt";
    File theGood = createTestJar(tempDir.newFile("1_normal.jar"), entryName, "-");
    File theBad = tempDir.newFile("2_broken.jar", new byte[1024]);

    UrlClassLoader flat = UrlClassLoader.build().urls(theBad.toURI().toURL(), theGood.toURI().toURL()).get();
    assertNotNull(findResource(flat, entryName, false));

    String content = Attributes.Name.MANIFEST_VERSION + ": 1.0\n" +
                     Attributes.Name.CLASS_PATH + ": " + theBad.toURI().toURL() + " " + theGood.toURI().toURL() + "\n\n";
    File theUgly = createTestJar(tempDir.newFile(ClassPath.CLASSPATH_JAR_FILE_NAME_PREFIX + "_3.jar"), JarFile.MANIFEST_NAME, content);

    UrlClassLoader recursive = UrlClassLoader.build().urls(theUgly.toURI().toURL()).get();
    assertNotNull(findResource(recursive, entryName, false));
  }

  @Test
  public void testDirEntry() throws IOException {
    String resourceDirName = "test_res_dir";
    String resourceDirName2 = "test_res_dir2";
    File theGood = createTestJar(tempDir.newFile("1_normal.jar"), resourceDirName + "/test_res.txt", "-", resourceDirName2 + "/", null);
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
    for (String dirName : new String[]{"dir", "dir/", "dir.class", "dir.class/"}) {
      for (String resourceName : new String[]{"a.class", "a.txt"}) {
        File root = tempDir.newDirectory("testFindDirWhenUsingCache" + (counter++));
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

  @Test
  public void testUncInClasspath() throws IOException {
    assumeWindows();
    Path uncRootPath = Paths.get(toLocalUncPath(tempDir.getRoot().getPath()));
    assumeTrue("Cannot access " + uncRootPath, Files.isDirectory(uncRootPath));

    String entryName = "test_res_dir/test_res.txt";
    File jar = createTestJar(tempDir.newFile("test.jar"), entryName, "-");
    UrlClassLoader cl = UrlClassLoader.build().urls(uncRootPath.resolve(jar.getName()).toUri().toURL()).get();
    assertNotNull(cl.findResource(entryName));
  }
}