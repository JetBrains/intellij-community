// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;
import static org.junit.Assert.*;

public class SymlinkHandlingTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Before
  public void setUp() {
    assumeSymLinkCreationIsSupported();
  }

  @After
  public void tearDown() {
    VirtualFile root = refreshAndFind(myTempDir.getRoot());
    // purge VFS to avoid persisting these specific file names through to the next launch
    VfsTestUtil.deleteFile(root);
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
  public void testCircularLink() {
    File upDir = myTempDir.newDirectory("sub");
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
  public void testMutualRecursiveLinks() {
    File circularDir1 = myTempDir.newDirectory("dir1");
    File circularDir2 = myTempDir.newDirectory("dir2");
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
  public void testDuplicateLinks() {
    File targetDir = myTempDir.newDirectory("target");
    File link1 = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/link1");
    File link2 = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/link2");
    assertVisitedPaths(targetDir.getPath(), link1.getPath(), link2.getPath());
  }

  @Test
  public void testSidewaysRecursiveLink() {
    File a = myTempDir.newDirectory("a");
    File b = createTestDir(a, "b");
    File link1 = createSymLink(SystemInfo.isWindows ? a.getPath() : "../../" + a.getName(), b.getPath() + "/link1");
    File project = myTempDir.newDirectory("project");
    File c = createTestDir(project, "c");
    File d = createTestDir(c, "d");
    File link2 = createSymLink(SystemInfo.isWindows ? a.getPath() : "../../../" + a.getName(), d.getPath() + "/link2");
    assertVisitedPaths(project,
                       c.getPath(), d.getPath(), link2.getPath(), link2.getPath() + "/" + b.getName(),
                       link2.getPath() + "/" + b.getName() + "/" + link1.getName());
  }

  @Test
  public void testVisitAllNonRecursiveLinks() {
    File target = myTempDir.newDirectory("target");
    File child = createTestDir(target, "child");
    File link1 = createSymLink(target.getPath(), myTempDir.getRoot() + "/link1");
    File link2 = createSymLink(target.getPath(), myTempDir.getRoot() + "/link2");
    assertVisitedPaths(target.getPath(), child.getPath(),
                       link1.getPath(), link1.getPath() + "/child",
                       link2.getPath(), link2.getPath() + "/child");
  }

  @Test
  public void testTargetIsWritable() {
    File targetFile = myTempDir.newFile("target.txt");
    File linkFile = createSymLink(targetFile.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() &&
                                                            linkVFile.is(VFileProperty.SYMLINK));

    setWritableAndCheck(targetFile, true);
    refresh(myTempDir.getRoot());
    assertTrue(linkVFile.getPath(), linkVFile.isWritable());
    setWritableAndCheck(targetFile, false);
    refresh(myTempDir.getRoot());
    assertFalse(linkVFile.getPath(), linkVFile.isWritable());

    File targetDir = myTempDir.newDirectory("target");
    File linkDir = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/linkDir");
    VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir, linkVDir != null && linkVDir.isDirectory() && linkVDir.is(VFileProperty.SYMLINK));

    if (!SystemInfo.isWindows) {
      setWritableAndCheck(targetDir, true);
      refresh(myTempDir.getRoot());
      assertTrue(linkVDir.getPath(), linkVDir.isWritable());
      setWritableAndCheck(targetDir, false);
      refresh(myTempDir.getRoot());
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

    WriteAction.runAndWait(() -> linkVFile.delete(this));
    assertFalse(linkVFile.toString(), linkVFile.isValid());
    assertFalse(linkFile.exists());
    assertTrue(targetFile.exists());

    File targetDir = myTempDir.newDirectory("targetDir");
    File childFile = new File(targetDir, "child.txt");
    assertTrue(childFile.getPath(), childFile.exists() || childFile.createNewFile());
    File linkDir = createSymLink(targetDir.getPath(), myTempDir.getRoot() + "/linkDir");
    VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir,
               linkVDir != null && linkVDir.isDirectory() && linkVDir.is(VFileProperty.SYMLINK) && linkVDir.getChildren().length == 1);

    WriteAction.runAndWait(() -> linkVDir.delete(this));
    assertFalse(linkVDir.toString(), linkVDir.isValid());
    assertFalse(linkDir.exists());
    assertTrue(targetDir.exists());
    assertTrue(childFile.exists());
  }

  @Test
  public void testTransGenderRefresh() throws Exception {
    File targetFile = myTempDir.newFile("target");
    File targetDir = myTempDir.newDirectory("targetDir");

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
  public void testDirLinkSwitchWithDifferentContentLength() throws Exception {
    doTestDirLinkSwitch("text", "longer text");
  }

  @Test
  public void testDirLinkSwitchWithSameContentLength() throws Exception {
    doTestDirLinkSwitch("text 1", "text 2");
  }

  private void doTestDirLinkSwitch(String text1, String text2) throws Exception {
    File targetDir1 = myTempDir.newDirectory("target1");
    File targetDir2 = myTempDir.newDirectory("target2");

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

    refresh(myTempDir.getRoot());
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
  public void testFileLinkSwitchWithDifferentContentLength() throws Exception {
    doTestLinkSwitch("text", "longer text");
  }

  @Test
  public void testFileLinkSwitchWithSameContentLength() throws Exception {
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

    refresh(myTempDir.getRoot());
    assertTrue(vLink1.isValid());
    VirtualFile vLink2 = LocalFileSystem.getInstance().findFileByIoFile(link);
    VfsUtilCore.loadText(vLink2);
    assertEquals(vLink1, vLink2);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               !vLink2.isDirectory() && vLink2.is(VFileProperty.SYMLINK));
    assertEquals(FileUtil.loadFile(target2), VfsUtilCore.loadText(vLink1));
    assertEquals(FileUtil.loadFile(target2), VfsUtilCore.loadText(vLink2));
    assertPathsEqual(target2.getPath(), vLink1.getCanonicalPath());
  }

  @Test
  public void testTraversePathBehindLink() {
    File topDir = myTempDir.newDirectory("top");
    File subDir1 = createTestDir(topDir, "sub1");
    File link = createSymLink(subDir1.getPath(), myTempDir.getRoot() + "/link");
    VirtualFile vLink = refreshAndFind(link);
    assertNotNull(link.getPath(), vLink);

    File subDir2 = createTestDir(topDir, "sub2");
    File subChild = createTestFile(subDir2, "subChild.txt");
    VirtualFile vSubChild = refreshAndFind(subChild);
    assertNotNull(subChild.getPath(), vSubChild);

    String relPath = "../" + subDir2.getName() + "/" + subChild.getName();
    VirtualFile vSubChildRel = vLink.findFileByRelativePath(relPath);
    assertEquals(vSubChild, vSubChildRel);
    vSubChildRel = LocalFileSystem.getInstance().findFileByPath(vLink.getPath() + "/" + relPath);
    assertEquals(vSubChild, vSubChildRel);
  }


  @Nullable
  private VirtualFile refreshAndFind(File ioFile) {
    return refreshAndFind(myTempDir.getRoot(), ioFile);
  }

  @Nullable
  static VirtualFile refreshAndFind(File root, File ioFile) {
    refresh(root);
    return LocalFileSystem.getInstance().findFileByPath(ioFile.getPath());
  }

  private static void refresh(File root) {
    VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    assertNotNull(root.getPath(), tempDir);

    tempDir.getChildren();
    tempDir.refresh(false, true);
    VfsUtilCore.visitChildrenRecursively(tempDir, new VirtualFileVisitor<Void>() { });
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

    Set<String> expectedSet = StreamEx.of(expected).map(FileUtil::toSystemIndependentName).append(vDir.getPath()).toSet();

    Set<String> actualSet = new HashSet<>();
    VfsUtilCore.visitChildrenRecursively(vDir, new VirtualFileVisitor<Void>() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (!actualSet.add(file.getPath())) {
          throw new AssertionError(file + " already visited");
        }
        return true;
      }
    });

    List<String> exp = new ArrayList<>(expectedSet);
    Collections.sort(exp);
    List<String> act = new ArrayList<>(actualSet);
    Collections.sort(act);

    assertEquals(StringUtil.join(exp, "\n"), StringUtil.join(act, "\n"));
  }

  @Test
  public void testCircularSymlinksMustBeDetected() {
    File top = myTempDir.newDirectory("top");
    File sub1 = createTestDir(top, "sub1");
    File link = createSymLink(top.getPath(), sub1.getPath() + "/link");
    VirtualFile vLink = refreshAndFind(link);
    assertNotNull(link.getPath(), vLink);

    String path = sub1.getPath() + StringUtil.repeat("/" + link.getName() + "/" + sub1.getName(), 10);
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path);
    assertNotNull(f);
    while (!FileUtil.pathsEqual(f.getPath(), sub1.getPath())) {
      if (f.getName().equals(link.getName())) {
        assertTrue(f.getPath(),f.is(VFileProperty.SYMLINK));
        assertTrue(f.getPath(), f.isRecursiveOrCircularSymLink());
      }
      else {
        assertEquals(f.getPath(), sub1.getName(), f.getName());
        assertFalse(f.getPath(),f.is(VFileProperty.SYMLINK));
        assertFalse(f.isRecursiveOrCircularSymLink());
      }
      f = f.getParent();
    }
  }

  @Test
  public void testCircularSymlinksMustBeDetectedEvenForAsideLinks() {
    File top = myTempDir.newDirectory("top");
    File sub1 = createTestDir(top, "s1");
    File ss1 = createTestDir(sub1, "ss1");
    File link1 = createSymLink(sub1.getPath(), ss1.getPath() + "/l1");
    File sub2 = createTestDir(top, "s2");
    File ss2 = createTestDir(sub2, "ss2");
    File link2 = createSymLink(sub1.getPath(), ss2.getPath() + "/l2");

    VirtualFile vl1 = refreshAndFind(link1);
    assertNotNull(link1.getPath(), vl1);
    VirtualFile vl2 = refreshAndFind(link2);
    assertNotNull(link2.getPath(), vl2);

    String path = link2.getPath() +"/"+ss1.getName()+"/"+link1.getName()+ "/" + ss1.getName()+"/"+link1.getName();
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path);
    assertNotNull(f);

    assertEquals(link1.getName(), f.getName());
    assertTrue(f.getPath(), f.is(VFileProperty.SYMLINK));
    assertTrue(f.getPath(), f.isRecursiveOrCircularSymLink());
    f = f.getParent();
    assertEquals(ss1.getName(), f.getName());
    assertFalse(f.getPath(), f.is(VFileProperty.SYMLINK));
    assertFalse(f.getPath(), f.isRecursiveOrCircularSymLink());
    f = f.getParent();
    assertEquals(link1.getName(), f.getName());
    assertTrue(f.getPath(), f.is(VFileProperty.SYMLINK));
    assertTrue(f.getPath(), f.isRecursiveOrCircularSymLink());
  }
}