// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.ExceptionUtil;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

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
    UrlClassLoader customCl = builder().files(List.of(path)).get();
    try (URLClassLoader standardCl = new URLClassLoader(new URL[]{path.toUri().toURL()})) {
      String relativePathToFile = "dir/a.txt";
      assertNotNull(customCl.findResource(relativePathToFile));
      assertNotNull(standardCl.findResource(relativePathToFile));

      String nonCanonicalPathToFile = "dir/a.txt/../a.txt";
      assertNotNull(customCl.findResource(nonCanonicalPathToFile));
      assertNotNull(standardCl.findResource(nonCanonicalPathToFile));

      String absolutePathToFile = "/dir/a.txt";
      assertNull(customCl.findResource(absolutePathToFile));  // there's no logger on the classpath, `UrlClassLoader#logError` prints to stderr
      assertNull(standardCl.findResource(absolutePathToFile));
    }
  }

  @Test
  public void testConcurrentResourceLoading() throws Exception {
    List<String> resourceNames = new ArrayList<>();
    List<Path> files = new ArrayList<>();

    for (File file : Objects.requireNonNull(new File(PlatformTestUtil.getCommunityPath(), "lib").listFiles())) {
      if (file.getName().endsWith(".jar")) {
        files.add(file.toPath());

        try (ZipFile zipFile = new ZipFile(file)) {
          Enumeration<? extends ZipEntry> entries = zipFile.entries();
          while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
              resourceNames.add(entry.getName());
            }
          }
        }
      }
    }

    assertThat(files).isNotEmpty();
    assertThat(resourceNames).isNotEmpty();

    int attemptCount = 1000;
    int threadCount = 3;
    int resourceCount = 20;

    ExecutorService executor = Executors.newWorkStealingPool(threadCount);
    try {
      Random random = new Random();
      for (int attempt = 0; attempt < attemptCount; attempt++) {
        PathClassLoader loader = new PathClassLoader(builder().files(files).parent(null));
        List<String> namesToLoad = new ArrayList<>();
        for (int j = 0; j < resourceCount; j++) {
          namesToLoad.add(resourceNames.get(random.nextInt(resourceNames.size())));
        }

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
          futures.add(executor.submit(() -> {
            for (String name : namesToLoad) {
              try {
                assertThat(findResource(loader, name, random.nextBoolean())).describedAs("cannot find " + name).isNotNull();
              }
              catch (IOException e) {
                ExceptionUtil.rethrow(e);
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

    UrlClassLoader flat = builder().files(List.of(theBad, theGood)).get();
    assertThat(findResource(flat, entryName, false)).isNotNull();

    String content = Attributes.Name.MANIFEST_VERSION + ": 1.0\n" +
                     Attributes.Name.CLASS_PATH + ": " + theBad.toUri().toURL() + " " + theGood.toUri().toURL() + "\n\n";
    Path theUgly = createTestJar(tempDir.newFile(ClassPath.CLASSPATH_JAR_FILE_NAME_PREFIX + "_3.jar"), JarFile.MANIFEST_NAME, content).toPath();

    UrlClassLoader recursive = builder().files(List.of(theUgly)).get();
    assertThat(recursive.findResource(entryName)).isNotNull();
  }

  @Test
  public void testDirEntry() throws IOException {
    String resourceDirName = "test_res_dir";
    String resourceDirName2 = "test_res_dir2";
    File theGood = createTestJar(tempDir.newFile("1_normal.jar"), resourceDirName + "/test_res.txt", "-", resourceDirName2 + "/", null);
    UrlClassLoader flat = builder().files(List.of(theGood.toPath())).get();

    String resourceDirNameWithSlash = resourceDirName + "/";
    String resourceDirNameWithSlash_ = "/" + resourceDirNameWithSlash;
    String resourceDirNameWithSlash2 = resourceDirName2 + "/";
    String resourceDirNameWithSlash2_ = "/" + resourceDirNameWithSlash2;

    assertThat(findResource(flat, resourceDirNameWithSlash, false)).isNull();
    assertThat(findResource(flat, resourceDirNameWithSlash2, false)).isNotNull();

    try (URLClassLoader recursive2 = new URLClassLoader(new URL[]{theGood.toURI().toURL()})) {
      assertNotNull(recursive2.findResource(resourceDirNameWithSlash2));
      assertNull(recursive2.findResource(resourceDirNameWithSlash2_));
      assertNull(recursive2.findResource(resourceDirNameWithSlash));
      assertNull(recursive2.findResource(resourceDirNameWithSlash_));
    }
  }

  private static URL findResource(UrlClassLoader loader, String name, boolean findAll) throws IOException {
    if (findAll) {
      Enumeration<URL> resources = loader.getResources(name);
      assertThat(resources.hasMoreElements()).describedAs("No resources for " + name).isTrue();
      return resources.nextElement();
    }
    else {
      return loader.findResource(name);
    }
  }

  @Test
  public void testFindDirWhenUsingCache() throws IOException {
    int counter = 1;
    for (String dirName : new String[]{"dir", "dir.class", "dir.class"}) {
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
    testAction.consume(builder().useCache().files(List.of(url)).get());
  }

  @Test
  public void testUncInClasspath() {
    assumeWindows();
    Path uncRootPath = Paths.get(toLocalUncPath(tempDir.getRoot().getPath()));
    assumeTrue("Cannot access " + uncRootPath, Files.isDirectory(uncRootPath));

    String entryName = "test_res_dir/test_res.txt";
    File jar = createTestJar(tempDir.newFile("test.jar"), entryName, "-");
    UrlClassLoader cl = builder().files(List.of(uncRootPath.resolve(jar.getName()))).get();
    assertNotNull(cl.findResource(entryName));
  }

  private static UrlClassLoader.Builder builder() {
    return UrlClassLoader.build().allowLock(false);
  }
}
