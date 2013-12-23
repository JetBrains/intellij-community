/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.vcs.DirectoryData;
import com.intellij.util.Processor;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VfsUtilTest extends PlatformLangTestCase {
  @Override
  protected void runBareRunnable(Runnable runnable) throws Throwable {
    runnable.run();
  }

  @Override
  protected void setUp() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          VfsUtilTest.super.setUp();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          VfsUtilTest.super.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public void testFindFileByUrl() throws Exception {

    File file1 = new File(PathManagerEx.getTestDataPath());
    file1 = new File(file1, "vfs");
    file1 = new File(file1, "findFileByUrl");
    VirtualFile file0 = VfsUtil.findFileByURL(file1.toURI().toURL());
    assertNotNull(file0);
    assertTrue(file0.isDirectory());
    final VirtualFile[] children = file0.getChildren();
    List<VirtualFile> list = new ArrayList<VirtualFile>();
    final VirtualFileFilter fileFilter = new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        return !file.getName().endsWith(".new");
      }
    };
    for (VirtualFile child : children) {
      if (fileFilter.accept(child)) {
        list.add(child);
      }
    }
    assertEquals(2, list.size());     // "CVS" dir ignored

    File file2 = new File(file1, "test.zip");
    URL url2 = file2.toURI().toURL();
    url2 = new URL("jar", "", url2.toExternalForm() + "!/");
    url2 = new URL(url2, "com/intellij/installer");
    url2 = new URL(url2.toExternalForm());
    file0 = VfsUtil.findFileByURL(url2);
    assertNotNull(file0);
    assertTrue(file0.isDirectory());

    File file3 = new File(file1, "1.txt");
    file0 = VfsUtil.findFileByURL(file3.toURI().toURL());
    String content = VfsUtilCore.loadText(file0);
    assertNotNull(file0);
    assertFalse(file0.isDirectory());
    assertEquals(content, "test text");
  }

  public void testRelativePath() throws Exception {
    final File root = new File(PathManagerEx.getTestDataPath());
    final File testRoot = new File(new File(root, "vfs"), "relativePath");
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(testRoot);
    assertNotNull(vTestRoot);
    assertTrue(vTestRoot.isDirectory());

    final File subDir = new File(testRoot, "subDir");
    final VirtualFile vSubDir = LocalFileSystem.getInstance().findFileByIoFile(subDir);
    assertNotNull(vSubDir);

    final File subSubDir = new File(subDir, "subSubDir");
    final VirtualFile vSubSubDir = LocalFileSystem.getInstance().findFileByIoFile(subSubDir);
    assertNotNull(vSubSubDir);

    assertEquals("subDir", VfsUtilCore.getRelativePath(vSubDir, vTestRoot, '/'));
    assertEquals("subDir/subSubDir", VfsUtilCore.getRelativePath(vSubSubDir, vTestRoot, '/'));
    assertEquals("", VfsUtilCore.getRelativePath(vTestRoot, vTestRoot, '/'));
  }

  public void testVisitRecursively() throws Exception {
    final DirectoryData data = new DirectoryData(myProject.getBaseDir());
    try {
      data.clear();
      data.create();

      final File subDir = new File(data.getBase().getPath(), "DL0N1");
      final VirtualFile vSubDir = LocalFileSystem.getInstance().findFileByIoFile(subDir);
      assertNotNull(vSubDir);

      VfsUtilCore.visitChildrenRecursively(data.getBase(), new VirtualFileVisitor() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          assertTrue(!VfsUtilCore.isAncestor(vSubDir, file, true));
          return !vSubDir.equals(file);
        }
      });
    }
    finally {
      data.clear();
    }
  }

  public void testAsyncRefresh() throws Throwable {
    final Throwable[] ex = {null};
    boolean success = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(
      Arrays.asList(new Object[8]), ProgressManager.getInstance().getProgressIndicator(), true, new Processor<Object>() {
      @Override
      public boolean process(Object o) {
        try {
          doAsyncRefreshTest();
        }
        catch (Throwable t) {
          ex[0] = t;
        }
        return true;
      }
    });
    if (ex[0] != null) throw ex[0];
    if (!success) System.out.println("!success");
  }

  private void doAsyncRefreshTest() throws Exception {
    final int N = 1000;
    final byte[] data = "xxx".getBytes();

    File temp = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        result.setResult(res);
      }
    }.execute().getResultObject();
    LocalFileSystem fs = LocalFileSystem.getInstance();
    VirtualFile vTemp = fs.findFileByIoFile(temp);
    assertNotNull(vTemp);
    VirtualFile[] children = new VirtualFile[N];

    long[] timestamp = new long[N];
    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      FileUtil.writeToFile(file, data);
      VirtualFile child = fs.refreshAndFindFileByIoFile(file);
      assertNotNull(child);
      children[i] = child;
      timestamp[i] = file.lastModified();
    }

    vTemp.refresh(false, true);

    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      assertEquals(timestamp[i], file.lastModified());
      VirtualFile child = fs.findFileByIoFile(file);
      assertNotNull(child);
      IoTestUtil.assertTimestampsEqual(timestamp[i], child.getTimeStamp());
    }

    for (int i = 0; i < N; i++) {
      File file = new File(temp, i + ".txt");
      FileUtil.writeToFile(file, "xxx".getBytes());
      assertTrue(file.setLastModified(timestamp[i] - 2000));
      long modified = file.lastModified();
      assertTrue("File:" + file.getPath() + "; time:" + modified, timestamp[i] != modified);
      timestamp[i] = modified;
      IoTestUtil.assertTimestampsNotEqual(children[i].getTimeStamp(), modified);
    }

    final CountDownLatch latch = new CountDownLatch(N);
    for (final VirtualFile child : children) {
      child.refresh(true, true, new Runnable() {
        @Override
        public void run() {
          latch.countDown();
        }
      });
    }
    while (true) {
      latch.await(100, TimeUnit.MILLISECONDS);
      UIUtil.pump();
      if (latch.getCount() == 0) break;
    }

    for (int i = 0; i < N; i++) {
      VirtualFile child = children[i];
      IoTestUtil.assertTimestampsEqual(timestamp[i], child.getTimeStamp());
    }
  }

  public void testFindChildWithTrailingSpace() throws IOException {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        result.setResult(res);
      }
    }.execute().getResultObject();
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    VirtualFile child = vDir.findChild(" ");
    assertNull(child);

    UsefulTestCase.assertEmpty(vDir.getChildren());
  }

  public void testDirAttributeRefreshes() throws IOException {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        result.setResult(res);
      }
    }.execute().getResultObject();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    File file = FileUtil.createTempFile(tempDir, "xxx", "yyy", true);
    assertNotNull(file);
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    assertFalse(vFile.isDirectory());

    boolean deleted = file.delete();
    assertTrue(deleted);
    boolean created = file.mkdir();
    assertTrue(created);
    assertTrue(file.exists());

    VirtualFile vFile2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile2);
    assertTrue(vFile2.isDirectory());
  }

  public void testPresentableUrlSurvivesDeletion() throws IOException {
    final VirtualFile file = createTempFile("txt", null, "content", Charset.defaultCharset());
    String url = file.getPresentableUrl();
    assertNotNull(url);
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        file.delete(this);
      }
    }.execute();
    assertEquals(url, file.getPresentableUrl());
  }

  public void testToUri() {
    if (!SystemInfo.isWindows) {
      assertEquals("file:///asd", VfsUtil.toUri(new File("/asd")).toASCIIString());
      assertEquals("file:///asd%20/sd", VfsUtil.toUri(new File("/asd /sd")).toASCIIString());
    }

    URI uri = VfsUtil.toUri("file:///asd");
    assertNotNull(uri);
    assertEquals("file", uri.getScheme());
    assertEquals("/asd", uri.getPath());

    uri = VfsUtil.toUri("file:///asd/ ads/ad#test");
    assertNotNull(uri);
    assertEquals("file", uri.getScheme());
    assertEquals("/asd/ ads/ad", uri.getPath());
    assertEquals("test", uri.getFragment());

    uri = VfsUtil.toUri("file:///asd/ ads/ad#");
    assertNotNull(uri);
    assertEquals("file:///asd/%20ads/ad#", uri.toString());

    uri = VfsUtil.toUri("mailto:someone@example.com");
    assertNotNull(uri);
    assertEquals("someone@example.com", uri.getSchemeSpecificPart());

    if (SystemInfo.isWindows) {
      uri = VfsUtil.toUri("file://C:/p");
      assertNotNull(uri);
      assertEquals("file", uri.getScheme());
      assertEquals("/C:/p", uri.getPath());
    }

    uri = VfsUtil.toUri("file:///Users/S pace");
    assertNotNull(uri);
    assertEquals("file", uri.getScheme());
    assertEquals("/Users/S pace", uri.getPath());
    assertEquals("/Users/S%20pace", uri.getRawPath());
    assertEquals("file:///Users/S%20pace", uri.toString());

    uri = VfsUtil.toUri("http://developer.android.com/guide/developing/tools/avd.html");
    assertNotNull(uri);
    assertEquals("http", uri.getScheme());
    assertEquals("/guide/developing/tools/avd.html", uri.getRawPath());
    assertEquals("http://developer.android.com/guide/developing/tools/avd.html", uri.toString());

    uri = VfsUtil.toUri("http://developer.android.com/guide/developing/tools/avd.html?f=23r2ewd");
    assertNotNull(uri);
    assertEquals("http", uri.getScheme());
    assertEquals("/guide/developing/tools/avd.html", uri.getRawPath());
    assertEquals("http://developer.android.com/guide/developing/tools/avd.html?f=23r2ewd", uri.toString());
    assertEquals("f=23r2ewd", uri.getQuery());
  }

  public void testIsAncestor() {
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir"));
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir/file.txt"));
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir/", "file:///my/dir/file.txt"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir2", "file:///my/dir/file.txt"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir2"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir/", "file:///my/dir2"));
  }

  public void testFindChildByNamePerformance() throws IOException {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        result.setResult(res);
      }
    }.execute().getResultObject();
    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        for (int i=0; i<10000; i++) {
          final String name = i + ".txt";
          vDir.createChildData(vDir, name);
        }
      }
    }.execute();
    final VirtualFile theChild = vDir.findChild("5111.txt");
    System.out.println("Start searching...");
    PlatformTestUtil.startPerformanceTest("find child is slow", 450, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        for (int i = 0; i < 1000000; i++) {
          VirtualFile child = vDir.findChild("5111.txt");
          assertSame(theChild, child);
        }
      }
    }).assertTiming();

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        for (VirtualFile file : vDir.getChildren()) {
          file.delete(this);
        }
      }
    }.execute().throwException();

  }

  public void testFindRootWithDenormalizedPath() {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        new File(res, "x.jar").createNewFile();
        result.setResult(res);
      }
    }.execute().getResultObject();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    VirtualFile jar = vDir.findChild("x.jar");
    assertNotNull(jar);

    NewVirtualFile root1 = ManagingFS.getInstance().findRoot(jar.getPath()+"!/", JarFileSystem.getInstance());
    NewVirtualFile root2 = ManagingFS.getInstance().findRoot(jar.getParent().getPath() + "//"+ jar.getName()+"!/", JarFileSystem.getInstance());
    assertNotNull(root1);
    assertSame(root1, root2);
  }

  public void testFindRootPerformance() {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        new File(res, "x.jar").createNewFile();
        result.setResult(res);
      }
    }.execute().getResultObject();
    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    final VirtualFile jar = vDir.findChild("x.jar");
    assertNotNull(jar);

    final NewVirtualFile root = ManagingFS.getInstance().findRoot(jar.getPath()+"!/", JarFileSystem.getInstance());
    PlatformTestUtil.startPerformanceTest("find root is slow", 500, new ThrowableRunnable() {
      @Override
      public void run() throws Throwable {
        final String path = jar.getPath() + "!/";
        final JarFileSystem fileSystem = JarFileSystem.getInstance();
        JobLauncher.getInstance().invokeConcurrentlyUnderProgress(Collections.nCopies(500, null), null, false, new Processor<Object>() {
          @Override
          public boolean process(Object o) {
            for (int i = 0; i < 1000; i++) {
              NewVirtualFile rootJar = ManagingFS.getInstance().findRoot(path, fileSystem);
              assertNotNull(rootJar);
              assertSame(root, rootJar);
            }
            return true;
          }
        });
      }
    }).assertTiming();
  }

  public void testNotCanonicallyNamedChild() {
    File tempDir = new WriteAction<File>() {
      @Override
      protected void run(Result<File> result) throws Throwable {
        File res = createTempDirectory();
        new File(res, "libFiles").createNewFile();
        new File(res, "CssInvalidElement").createNewFile();
        new File(res, "extFiles").createNewFile();
        result.setResult(res);
      }
    }.execute().getResultObject();
    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    assertNotNull(vDir.findChild("libFiles"));
    assertNotNull(vDir.findChild("CssInvalidElement"));

    assertNotNull(vDir.findChild("extFiles"));
    assertNotNull(vDir.findChild("/extFiles/"));
    assertNotNull(vDir.findChild("extFiles/"));
    assertNotNull(vDir.findChild("/extFiles"));
    assertNotNull(vDir.findChild("//extFiles"));
    assertNotNull(vDir.findChild("extFiles///"));

    assertNull(vDir.findChild("/xxx/extFiles/"));
    assertNull(vDir.findChild("xxx/extFiles/"));
    assertNull(vDir.findChild("/xxx/extFiles"));
    assertNull(vDir.findChild("xxx/extFiles"));
    assertNull(vDir.findChild("xxx//extFiles"));
  }
}
