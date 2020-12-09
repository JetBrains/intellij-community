// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ThrowableConsumer;
import org.junit.Assert;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

/**
 * @author Dmitry Avdeev
 */
public class UrlClassLoaderTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void testBootstrapResources() {
    String name = "META-INF/services/java.nio.file.spi.FileSystemProvider";
    assertNotNull(ClassLoader.getSystemResourceAsStream(name));
    assertNull(UrlClassLoader.build().get().getResourceAsStream(name));
    assertNotNull(UrlClassLoader.build().allowBootstrapResources().get().getResourceAsStream(name));
  }

  @Test
  public void testNonCanonicalPaths() throws IOException {
    tempDir.newFile("dir/a.txt");

    Path path = tempDir.getRoot().toPath();
    UrlClassLoader customCl = UrlClassLoader.build().files(List.of(path)).get();
    try (URLClassLoader standardCl = new URLClassLoader(new URL[]{path.toUri().toURL()})) {
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
    List<Path> files = new ArrayList<>();

    File[] libs = Objects.requireNonNull(new File(PathManager.getHomePathFor(UrlClassLoader.class) + "/lib").listFiles());
    for (File file : libs) {
      if (file.getName().endsWith(".jar")) {
        files.add(file.toPath());

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
        UrlClassLoader loader = UrlClassLoader.build().files(files).parent(null).allowLock(true).useCache(pool, (url) -> true).get();
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
    Path theGood = createTestJar(tempDir.newFile("1_normal.jar"), entryName, "-").toPath();
    Path theBad = tempDir.newFile("2_broken.jar", new byte[1024]).toPath();

    UrlClassLoader flat = UrlClassLoader.build().files(List.of(theBad, theGood)).get();
    assertThat(findResource(flat, entryName, false)).isNotNull();

    String content = Attributes.Name.MANIFEST_VERSION + ": 1.0\n" +
                     Attributes.Name.CLASS_PATH + ": " + theBad.toUri().toURL() + " " + theGood.toUri().toURL() + "\n\n";
    Path theUgly = createTestJar(tempDir.newFile(ClassPath.CLASSPATH_JAR_FILE_NAME_PREFIX + "_3.jar"), JarFile.MANIFEST_NAME, content).toPath();

    UrlClassLoader recursive = UrlClassLoader.build().files(List.of(theUgly)).get();
    assertThat(recursive.findResource(entryName)).isNotNull();
  }

  @Test
  public void testDirEntry() throws IOException {
    String resourceDirName = "test_res_dir";
    String resourceDirName2 = "test_res_dir2";
    File theGood = createTestJar(tempDir.newFile("1_normal.jar"), resourceDirName + "/test_res.txt", "-", resourceDirName2 + "/", null);
    UrlClassLoader flat = UrlClassLoader.build().files(Collections.singletonList(theGood.toPath())).get();

    String resourceDirNameWithSlash = resourceDirName + "/";
    String resourceDirNameWithSlash_ = "/" + resourceDirNameWithSlash;
    String resourceDirNameWithSlash2 = resourceDirName2 + "/";
    String resourceDirNameWithSlash2_ = "/" + resourceDirNameWithSlash2;

    assertThat(findResource(flat, resourceDirNameWithSlash, false)).isNull();
    assertThat(findResource(flat, resourceDirNameWithSlash_, false)).isNull();
    assertThat(findResource(flat, resourceDirNameWithSlash2, false)).isNotNull();
    // non-standard CL behavior
    assertThat(findResource(flat, resourceDirNameWithSlash2_, false)).isNotNull();

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
        Assert.assertTrue(resources.hasMoreElements());
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
        Path root = tempDir.newDirectory("testFindDirWhenUsingCache" + (counter++)).toPath();
        Path subDir = createTestDir(root.toFile(), dirName).toPath();
        createTestFile(subDir.toFile(), resourceName);

        try (URLClassLoader standardCl = new URLClassLoader(new URL[]{root.toUri().toURL()})) {
          Enumeration<URL> resources = standardCl.findResources(dirName);
          assertThat(resources.hasMoreElements()).isTrue();
          URL expectedResourceUrl = resources.nextElement();

          withCustomCachedClassloader(root, (customCl) -> {
            assertThat(customCl.findResource("SomeNonExistentResource.resource")).isNull();
            checkResourceUrlIsTheSame(customCl, dirName, expectedResourceUrl);
          });
          withCustomCachedClassloader(root, (customCl) -> checkResourceUrlIsTheSame(customCl, dirName, expectedResourceUrl));
        }
      }
    }
  }

  private static void checkResourceUrlIsTheSame(UrlClassLoader customCl, String resourceName, URL expectedResourceUrl) throws IOException {
    Enumeration<URL> customClResources = customCl.findResources(resourceName);
    assertThat(customClResources.hasMoreElements()).isTrue();
    assertThat(StringUtil.trimEnd(customClResources.nextElement().toExternalForm(), "/")).isEqualTo(StringUtil.trimEnd(expectedResourceUrl.toExternalForm(), "/"));
  }

  private static void withCustomCachedClassloader(Path url, ThrowableConsumer<UrlClassLoader, IOException> testAction) throws IOException {
    testAction.consume(UrlClassLoader.build().useCache().files(List.of(url)).get());
  }

  @Test
  public void testUncInClasspath() {
    assumeWindows();
    Path uncRootPath = Paths.get(toLocalUncPath(tempDir.getRoot().getPath()));
    assumeTrue("Cannot access " + uncRootPath, Files.isDirectory(uncRootPath));

    String entryName = "test_res_dir/test_res.txt";
    File jar = createTestJar(tempDir.newFile("test.jar"), entryName, "-");
    UrlClassLoader cl = UrlClassLoader.build().files(Collections.singletonList(uncRootPath.resolve(jar.getName()))).get();
    assertNotNull(cl.findResource(entryName));
  }
}