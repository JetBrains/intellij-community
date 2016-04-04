/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class VfsUtilTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void testFixIdeaUrl() {
    assertEquals("file:/C:/Temp/README.txt", VfsUtilCore.fixIDEAUrl("file://C:/Temp/README.txt"));
    assertEquals("file:/C:/Temp/README.txt", VfsUtilCore.fixIDEAUrl("file:///C:/Temp/README.txt"));
    assertEquals("file:/tmp/foo.bar", VfsUtilCore.fixIDEAUrl("file:///tmp/foo.bar"));
  }

  @Test
  public void testFindFileByUrl() throws Exception {
    File file1 = new File(PathManagerEx.getTestDataPath());
    file1 = new File(file1, "vfs");
    file1 = new File(file1, "findFileByUrl");
    VirtualFile file0 = VfsUtil.findFileByURL(file1.toURI().toURL());
    assertNotNull(file0);
    assertTrue(file0.isDirectory());
    List<VirtualFile> list = VfsUtil.getChildren(file0, file -> !file.getName().endsWith(".new"));
    assertEquals(2, list.size());     // "CVS" dir ignored

    File file2 = new File(file1, "test.zip");
    URL url2 = file2.toURI().toURL();

    url2 = new URL("jar", "", url2.toExternalForm() + "!/");
    file0 = VfsUtil.findFileByURL(url2);
    assertNotNull(file0);
    assertTrue(file0.isDirectory());

    url2 = new URL(url2, "com/intellij/installer");
    file0 = VfsUtil.findFileByURL(url2);
    assertNotNull(file0);
    assertTrue(file0.isDirectory());

    File file3 = new File(file1, "1.txt");
    file0 = VfsUtil.findFileByURL(file3.toURI().toURL());
    String content = VfsUtilCore.loadText(file0);
    assertNotNull(file0);
    assertFalse(file0.isDirectory());
    assertEquals("test text", content);
  }

  @Test
  public void testFindRelativeFile() throws Exception {
    File ioTestDataDir = new File(PathManagerEx.getTestDataPath());
    VirtualFile testDataDir = LocalFileSystem.getInstance().findFileByIoFile(ioTestDataDir);
    assertNotNull(testDataDir);
    assertEquals(testDataDir, VfsUtilCore.findRelativeFile(VfsUtilCore.convertFromUrl(ioTestDataDir.toURI().toURL()), null));
    assertEquals(testDataDir, VfsUtilCore.findRelativeFile(ioTestDataDir.getAbsolutePath(), null));

    File ioVfsDir = new File(ioTestDataDir, "vfs");
    VirtualFile vfsDir = LocalFileSystem.getInstance().findFileByIoFile(ioVfsDir);
    assertNotNull(vfsDir);
    assertEquals(vfsDir, VfsUtilCore.findRelativeFile(ioVfsDir.getAbsolutePath(), null));
    assertEquals(vfsDir, VfsUtilCore.findRelativeFile("vfs", testDataDir));
  }

  @Test
  public void testRelativePath() throws Exception {
    File root = new File(PathManagerEx.getTestDataPath());
    File testRoot = new File(new File(root, "vfs"), "relativePath");
    VirtualFile vTestRoot = LocalFileSystem.getInstance().findFileByIoFile(testRoot);
    assertNotNull(vTestRoot);
    assertTrue(vTestRoot.isDirectory());

    File subDir = new File(testRoot, "subDir");
    VirtualFile vSubDir = LocalFileSystem.getInstance().findFileByIoFile(subDir);
    assertNotNull(vSubDir);

    File subSubDir = new File(subDir, "subSubDir");
    VirtualFile vSubSubDir = LocalFileSystem.getInstance().findFileByIoFile(subSubDir);
    assertNotNull(vSubSubDir);

    assertEquals("subDir", VfsUtilCore.getRelativePath(vSubDir, vTestRoot, '/'));
    assertEquals("subDir/subSubDir", VfsUtilCore.getRelativePath(vSubSubDir, vTestRoot, '/'));
    assertEquals("", VfsUtilCore.getRelativePath(vTestRoot, vTestRoot, '/'));
  }

  @Test
  public void testFindChildWithTrailingSpace() throws IOException {
    File tempDir = myTempDir.newFolder();
    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(vDir);
    assertTrue(vDir.isDirectory());

    VirtualFile child = vDir.findChild(" ");
    assertNull(child);

    UsefulTestCase.assertEmpty(vDir.getChildren());
  }

  @Test
  public void testDirAttributeRefreshes() throws IOException {
    File tempDir = myTempDir.newFolder();
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

  @Test
  public void testPresentableUrlSurvivesDeletion() throws IOException {
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(myTempDir.newFile("file.txt"));
    assertNotNull(file);
    String url = file.getPresentableUrl();
    assertNotNull(url);
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws IOException {
        file.delete(this);
      }
    }.execute();
    assertEquals(url, file.getPresentableUrl());
  }

  @Test
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

  @Test
  public void testIsAncestor() {
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir"));
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir/file.txt"));
    assertTrue(VfsUtilCore.isEqualOrAncestor("file:///my/dir/", "file:///my/dir/file.txt"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir2", "file:///my/dir/file.txt"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir", "file:///my/dir2"));
    assertFalse(VfsUtilCore.isEqualOrAncestor("file:///my/dir/", "file:///my/dir2"));
  }

  @Test
  public void testFindRootWithDenormalizedPath() throws IOException {
    File tempJar = IoTestUtil.createTestJar(myTempDir.newFile("test.jar"));
    VirtualFile jar = LocalFileSystem.getInstance().findFileByIoFile(tempJar);
    assertNotNull(jar);

    JarFileSystem fs = JarFileSystem.getInstance();
    NewVirtualFile root1 = ManagingFS.getInstance().findRoot(jar.getPath() + "!/", fs);
    NewVirtualFile root2 = ManagingFS.getInstance().findRoot(jar.getParent().getPath() + "//" + jar.getName() + "!/", fs);
    assertNotNull(root1);
    assertSame(root1, root2);
  }

  @Test
  public void testNotCanonicallyNamedChild() throws IOException {
    File tempDir = myTempDir.newFolder();
    assertTrue(new File(tempDir, "libFiles").createNewFile());
    assertTrue(new File(tempDir, "CssInvalidElement").createNewFile());
    assertTrue(new File(tempDir, "extFiles").createNewFile());

    VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
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

  @Test public void testRenameDuringFullRefresh() throws IOException { doRenameAndRefreshTest(true); }
  @Test public void testRenameDuringPartialRefresh() throws IOException { doRenameAndRefreshTest(false); }

  private void doRenameAndRefreshTest(boolean full) throws IOException {
    assertFalse(ApplicationManager.getApplication().isDispatchThread());

    File tempDir = myTempDir.newFolder();
    assertTrue(new File(tempDir, "child").createNewFile());

    VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(tempDir);
    assertNotNull(parent);
    if (full) {
      assertEquals(1, parent.getChildren().length);
    }
    VirtualFile child = parent.findChild("child");
    assertNotNull(child);

    List<VirtualFile> files = Collections.singletonList(parent);

    Semaphore semaphore = new Semaphore();
    for (int i = 0; i < 1000; i++) {
      semaphore.down();
      VfsUtil.markDirty(true, false, parent);
      LocalFileSystem.getInstance().refreshFiles(files, true, true, semaphore::up);

      assertTrue(child.isValid());
      String newName = "name" + i;
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          child.rename(this, newName);
        }
      }.execute();
      assertTrue(child.isValid());

      TimeoutUtil.sleep(1);  // needed to prevent frequent event detector from triggering
    }

    assertTrue(semaphore.waitFor(60000));
  }
}