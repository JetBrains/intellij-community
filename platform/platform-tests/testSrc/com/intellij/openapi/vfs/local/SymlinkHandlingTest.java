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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class SymlinkHandlingTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Before
  public void setUp() {
    assumeTrue(SystemInfo.areSymLinksSupported);
  }

  @Test
  public void testMissingLink() {
    File missingFile = new File(myTempDir.getRoot(), "missing_file");
    assertTrue(missingFile.getPath(), !missingFile.exists() || missingFile.delete());
    File missingLinkFile = createSymLink(missingFile.getPath(), myTempDir.getRoot() + "/missing_link", false);
    VirtualFile missingLinkVFile = refreshAndFind(missingLinkFile);
    assertNotNull(missingLinkVFile);
    assertBrokenLink(missingLinkVFile);
    assertVisitedPaths(missingLinkVFile.getPath());
  }

  @Test
  public void testSelfLink() {
    String target = new File(myTempDir.getRoot(), "self_link").getPath();
    File selfLinkFile = createSymLink(target, target, false);
    VirtualFile selfLinkVFile = refreshAndFind(selfLinkFile);
    assertNotNull(selfLinkVFile);
    assertBrokenLink(selfLinkVFile);
    assertVisitedPaths(selfLinkVFile.getPath());
  }

  @Test
  public void testDotLink() {
    File dotLinkFile = createSymLink(".", myTempDir.getRoot() + "/dot_link");
    VirtualFile dotLinkVFile = refreshAndFind(dotLinkFile);
    assertNotNull(dotLinkVFile);
    assertTrue(dotLinkVFile.is(VFileProperty.SYMLINK));
    assertTrue(dotLinkVFile.isDirectory());
    assertPathsEqual(myTempDir.getRoot().getPath(), dotLinkVFile.getCanonicalPath());
    assertVisitedPaths(dotLinkVFile.getPath());
  }

  @Test
  public void testCircularLink() throws Exception {
    File upDir = myTempDir.newFolder("sub");
    File upLinkFile = createSymLink(upDir.getPath(), upDir.getPath() + "/up_link");
    VirtualFile upLinkVFile = refreshAndFind(upLinkFile);
    assertNotNull(upLinkVFile);
    assertTrue(upLinkVFile.is(VFileProperty.SYMLINK));
    assertTrue(upLinkVFile.isDirectory());
    assertPathsEqual(upDir.getPath(), upLinkVFile.getCanonicalPath());
    assertVisitedPaths(upDir.getPath(), upLinkVFile.getPath());

    File repeatedLinksFile = new File(upDir.getPath() + StringUtil.repeat(File.separator + upLinkFile.getName(), 4));
    assertTrue(repeatedLinksFile.getPath(), repeatedLinksFile.isDirectory());
    VirtualFile repeatedLinksVFile = refreshAndFind(repeatedLinksFile);
    assertNotNull(repeatedLinksFile.getPath(), repeatedLinksVFile);
    assertTrue(repeatedLinksVFile.is(VFileProperty.SYMLINK));
    assertTrue(repeatedLinksVFile.isDirectory());
    assertPathsEqual(upDir.getPath(), repeatedLinksVFile.getCanonicalPath());
    assertEquals(upLinkVFile.getCanonicalFile(), repeatedLinksVFile.getCanonicalFile());
  }

  @Test
  public void testMutualRecursiveLinks() throws Exception {
    File circularDir1 = myTempDir.newFolder("dir1");
    File circularDir2 = myTempDir.newFolder("dir2");
    File circularLink1 = createSymLink(circularDir2.getPath(), circularDir1 + "/link1");
    File circularLink2 = createSymLink(circularDir1.getPath(), circularDir2 + "/link2");
    VirtualFile circularLink1VFile = refreshAndFind(circularLink1);
    VirtualFile circularLink2VFile = refreshAndFind(circularLink2);
    assertNotNull(circularLink1VFile);
    assertNotNull(circularLink2VFile);
    assertVisitedPaths(circularDir1.getPath(), circularLink1.getPath(), circularLink1.getPath() + "/" + circularLink2.getName(),
                       circularDir2.getPath(), circularLink2.getPath(), circularLink2.getPath() + "/" + circularLink1.getName());
  }

  @Test
  public void testDuplicateLinks() throws Exception {
    File targetDir = myTempDir.newFolder("target");
    File link1 = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/link1");
    File link2 = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/link2");
    assertVisitedPaths(targetDir.getPath(), link1.getPath(), link2.getPath());
  }

  @Test
  public void testSidewaysRecursiveLink() throws Exception {
    File target = myTempDir.newFolder("dir_a");
    File link1Home = createTestDir(target, "dir_b");
    File link1 = createSymLink(SystemInfo.isWindows ? target.getPath() : "../../" + target.getName(), link1Home.getPath() + "/link1");
    File mainDir = myTempDir.newFolder("project");
    File subDir = createTestDir(mainDir, "dir_c");
    File link2Home = createTestDir(subDir, "dir_d");
    File link2 = createSymLink(SystemInfo.isWindows ? target.getPath() : "../../../" + target.getName(), link2Home.getPath() + "/link2");
    assertVisitedPaths(mainDir,
                       subDir.getPath(), link2Home.getPath(), link2.getPath(), link2.getPath() + "/" + link1Home.getName(),
                       link2.getPath() + "/" + link1Home.getName() + "/" + link1.getName());
  }

  @Test
  public void testVisitAllNonRecursiveLinks() throws Exception {
    File target = myTempDir.newFolder("target");
    File child = createTestDir(target, "child");
    File link1 = createSymLink(target.getPath(), myTempDir.getRoot() + "/link1");
    File link2 = createSymLink(target.getPath(), myTempDir.getRoot() + "/link2");
    assertVisitedPaths(target.getPath(), child.getPath(),
                       link1.getPath(), link1.getPath() + "/child",
                       link2.getPath(), link2.getPath() + "/child");
  }

  @Test
  public void testTargetIsWritable() throws Exception {
    File targetFile = myTempDir.newFile("target.txt");
    File linkFile = createSymLink(targetFile.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() &&
                                                            linkVFile.is(VFileProperty.SYMLINK));

    setWritableAndCheck(targetFile, true);
    refresh();
    assertTrue(linkVFile.getPath(), linkVFile.isWritable());
    setWritableAndCheck(targetFile, false);
    refresh();
    assertFalse(linkVFile.getPath(), linkVFile.isWritable());

    File targetDir = myTempDir.newFolder("target");
    File linkDir = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/linkDir");
    VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir, linkVDir != null && linkVDir.isDirectory() && linkVDir.is(VFileProperty.SYMLINK));

    if (!SystemInfo.isWindows) {
      setWritableAndCheck(targetDir, true);
      refresh();
      assertTrue(linkVDir.getPath(), linkVDir.isWritable());
      setWritableAndCheck(targetDir, false);
      refresh();
      assertFalse(linkVDir.getPath(), linkVDir.isWritable());
    }
    else {
      assertEquals(linkVDir.getPath(), targetDir.canWrite(), linkVDir.isWritable());
    }
  }

  private static void setWritableAndCheck(File file, boolean writable) {
    assertTrue(file.getPath(), file.setWritable(writable, false));
    assertEquals(file.getPath(), writable, file.canWrite());
  }

  @Test
  public void testLinkDeleteIsSafe() throws Exception {
    File targetFile = myTempDir.newFile("target");
    File linkFile = createSymLink(targetFile.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile,
               linkVFile != null && !linkVFile.isDirectory() && linkVFile.is(VFileProperty.SYMLINK));

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        linkVFile.delete(SymlinkHandlingTest.this);
      }
    }.execute();
    assertFalse(linkVFile.toString(), linkVFile.isValid());
    assertFalse(linkFile.exists());
    assertTrue(targetFile.exists());

    File targetDir = myTempDir.newFolder("targetDir");
    File childFile = new File(targetDir, "child.txt");
    assertTrue(childFile.getPath(), childFile.exists() || childFile.createNewFile());
    File linkDir = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/linkDir");
    VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir,
               linkVDir != null && linkVDir.isDirectory() && linkVDir.is(VFileProperty.SYMLINK) && linkVDir.getChildren().length == 1);

    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        linkVDir.delete(SymlinkHandlingTest.this);
      }
    }.execute();
    assertFalse(linkVDir.toString(), linkVDir.isValid());
    assertFalse(linkDir.exists());
    assertTrue(targetDir.exists());
    assertTrue(childFile.exists());
  }

  @Test
  public void testTransGenderRefresh() throws Exception {
    File targetFile = myTempDir.newFile("target");
    File targetDir = myTempDir.newFolder("targetDir");

    // file link
    File link = createSymLink(targetFile.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               vFile1 != null && !vFile1.isDirectory() && vFile1.is(VFileProperty.SYMLINK));

    // file link => dir
    assertTrue(link.getPath(), link.delete() && link.mkdir() && link.isDirectory());
    VirtualFile vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile2,
               !vFile1.isValid() && vFile2 != null && vFile2.isDirectory() && !vFile2.is(VFileProperty.SYMLINK));

    // dir => dir link
    assertTrue(link.getPath(), link.delete());
    link = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && vFile1.isDirectory() && vFile1.is(VFileProperty.SYMLINK));

    // dir link => file
    assertTrue(link.getPath(), link.delete() && link.createNewFile() && link.isFile());
    vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile1.isValid() && vFile2 != null && !vFile2.isDirectory() && !vFile2.is(VFileProperty.SYMLINK));

    // file => file link
    assertTrue(link.getPath(), link.delete());
    link = createSymLink(targetFile.getPath(), myTempDir.getRoot() + "/link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && !vFile1.isDirectory() && vFile1.is(VFileProperty.SYMLINK));
  }

  @Test
  public void testDirLinkSwitchWithDifferentlenghtContent() throws Exception {
    doTestDirLinkSwitch("text", "longer text");
  }

  @Test
  public void testDirLinkSwitchWithSameLengthContent() throws Exception {
    doTestDirLinkSwitch("text 1", "text 2");
  }

  private void doTestDirLinkSwitch(String text1, String text2) throws Exception {
    File targetDir1 = myTempDir.newFolder("target1");
    File targetDir2 = myTempDir.newFolder("target2");
    
    File target1Child = new File(targetDir1, "child1.txt");
    assertTrue(target1Child.createNewFile());
    File target2Child = new File(targetDir2, "child1.txt");
    assertTrue(target2Child.createNewFile());
    assertTrue(new File(targetDir2, "child2.txt").createNewFile());
    FileUtil.writeToFile(target1Child, text1);
    FileUtil.writeToFile(target2Child, text2);

    File link = createSymLink(targetDir1.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile vLink1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink1,
               vLink1 != null && vLink1.isDirectory() && vLink1.is(VFileProperty.SYMLINK));
    assertEquals(1, vLink1.getChildren().length);
    assertPathsEqual(targetDir1.getPath(), vLink1.getCanonicalPath());
    assertEquals(FileUtil.loadFile(target1Child), VfsUtilCore.loadText(vLink1.findChild("child1.txt")));

    assertTrue(link.toString(), link.delete());
    createSymLink(targetDir2.getPath(), myTempDir.getRoot() + "/" + link.getName());

    refresh();
    assertTrue(vLink1.isValid());
    VirtualFile vLink2 = LocalFileSystem.getInstance().findFileByIoFile(link);
    assertEquals(vLink1, vLink2);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               vLink2 != null && vLink2.isDirectory() && vLink2.is(VFileProperty.SYMLINK));
    assertEquals(2, vLink2.getChildren().length);
    assertPathsEqual(targetDir2.getPath(), vLink1.getCanonicalPath());
    assertEquals(FileUtil.loadFile(target2Child), VfsUtilCore.loadText(vLink1.findChild("child1.txt")));
    assertEquals(FileUtil.loadFile(target2Child), VfsUtilCore.loadText(vLink2.findChild("child1.txt")));
  }

  @Test
  public void testFileLinkSwitchWithDifferentlenghtContent() throws Exception {
    doTestLinkSwitch("text", "longer text");
  }

  @Test
  public void testFileLinkSwitchWithSameLengthContent() throws Exception {
    doTestLinkSwitch("text 1", "text 2");
  }

  private void doTestLinkSwitch(String text1, String text2) throws IOException {
    File target1 = myTempDir.newFile("target1.txt");
    FileUtil.writeToFile(target1, text1);
    File target2 = myTempDir.newFile("target2.txt");
    FileUtil.writeToFile(target2, text2);

    File link = createSymLink(target1.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile vLink1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink1,
               vLink1 != null && !vLink1.isDirectory() && vLink1.is(VFileProperty.SYMLINK));
    assertEquals(FileUtil.loadFile(target1), VfsUtilCore.loadText(vLink1));
    assertPathsEqual(target1.getPath(), vLink1.getCanonicalPath());

    assertTrue(link.toString(), link.delete());
    createSymLink(target2.getPath(), myTempDir.getRoot() + "/" + link.getName());

    refresh();
    assertTrue(vLink1.isValid());
    VirtualFile vLink2 = LocalFileSystem.getInstance().findFileByIoFile(link);
    VfsUtilCore.loadText(vLink2);
    assertEquals(vLink1, vLink2);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               vLink2 != null && !vLink2.isDirectory() && vLink2.is(VFileProperty.SYMLINK));
    assertEquals(FileUtil.loadFile(target2), VfsUtilCore.loadText(vLink1));
    assertEquals(FileUtil.loadFile(target2), VfsUtilCore.loadText(vLink2));
    assertPathsEqual(target2.getPath(), vLink1.getCanonicalPath());
  }

  @Test
  public void testTraversePathBehindLink() throws Exception {
    File topDir = myTempDir.newFolder("top");
    File subDir1 = createTestDir(topDir, "sub1");
    File link = createSymLink(subDir1.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile vLink = refreshAndFind(link);
    assertNotNull(link.getPath(), vLink);

    File subDir2 = createTestDir(topDir, "sub2");
    File subChild = createTestFile(subDir2, "subChild.txt");
    VirtualFile vSubChild = refreshAndFind(subChild);
    assertNotNull(subChild.getPath(), vSubChild);

    String relPath = "../" + subDir2.getName() + "/" + subChild.getName();
    VirtualFile vSubChildRel;
    vSubChildRel = vLink.findFileByRelativePath(relPath);
    assertEquals(vSubChild, vSubChildRel);
    vSubChildRel = LocalFileSystem.getInstance().findFileByPath(vLink.getPath() + "/" + relPath);
    assertEquals(vSubChild, vSubChildRel);
  }


  @Nullable
  private VirtualFile refreshAndFind(File ioFile) {
    refresh();
    return LocalFileSystem.getInstance().findFileByPath(ioFile.getPath());
  }

  protected void refresh() {
    VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myTempDir.getRoot());
    assertNotNull(myTempDir.getRoot().getPath(), tempDir);

    tempDir.getChildren();
    tempDir.refresh(false, true);
    VfsUtilCore.visitChildrenRecursively(tempDir, new VirtualFileVisitor() { });
  }

  private static void assertBrokenLink(@NotNull VirtualFile link) {
    assertTrue(link.is(VFileProperty.SYMLINK));
    assertEquals(0, link.getLength());
    assertNull(link.getCanonicalPath(), link.getCanonicalPath());
  }

  private void assertVisitedPaths(String... expected) {
    assertVisitedPaths(myTempDir.getRoot(), expected);
  }

  private void assertVisitedPaths(File from, String... expected) {
    VirtualFile vDir = refreshAndFind(from);
    assertNotNull(vDir);

    Set<String> expectedSet =
      Stream.concat(Stream.of(expected).map(FileUtil::toSystemIndependentName), Stream.of(vDir.getPath())).collect(Collectors.toSet());

    Set<String> actualSet = new java.util.HashSet<>();
    VfsUtilCore.visitChildrenRecursively(vDir, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        actualSet.add(file.getPath());
        return true;
      }
    });

    assertEquals(expectedSet, actualSet);
  }
}