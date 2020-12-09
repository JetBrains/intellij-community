// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.junit.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;
import static org.junit.Assert.*;

public class SymlinkHandlingTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Before
  public void setUp() {
    assumeSymLinkCreationIsSupported();
  }

  @After
  public void tearDown() {
    VirtualFile root = refreshAndFind(tempDir.getRoot());
    // purge VFS to avoid persisting these specific file names through to the next launch
    VfsTestUtil.deleteFile(root);
  }

  @Test
  public void testMissingLink() {
    File missingFile = new File(tempDir.getRoot(), "missing_file");
    assertTrue(missingFile.getPath(), !missingFile.exists() || missingFile.delete());
    File missingLinkFile = createSymLink(missingFile.getPath(), tempDir.getRoot() + "/missing_link", false);
    VirtualFile missingLinkVFile = refreshAndFind(missingLinkFile);
    assertNotNull(missingLinkVFile);
    assertBrokenLink(missingLinkVFile);
    assertVisitedPaths(missingLinkVFile.getPath());
  }

  @Test
  public void testSelfLink() {
    String target = new File(tempDir.getRoot(), "self_link").getPath();
    File selfLinkFile = createSymLink(target, target, false);
    VirtualFile selfLinkVFile = refreshAndFind(selfLinkFile);
    assertNotNull(selfLinkVFile);
    assertBrokenLink(selfLinkVFile);
    assertVisitedPaths(selfLinkVFile.getPath());
  }

  @Test
  public void testDotLink() {
    File dotLinkFile = createSymLink(".", tempDir.getRoot() + "/dot_link");
    VirtualFile dotLinkVFile = refreshAndFind(dotLinkFile);
    assertNotNull(dotLinkVFile);
    assertTrue(dotLinkVFile.is(VFileProperty.SYMLINK));
    assertTrue(dotLinkVFile.isDirectory());
    assertPathsEqual(tempDir.getRoot().getPath(), dotLinkVFile.getCanonicalPath());
    assertVisitedPaths(dotLinkVFile.getPath());
  }

  @Test
  public void testCircularLink() {
    File upDir = tempDir.newDirectory("sub");
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
    File circularDir1 = tempDir.newDirectory("dir1");
    File circularDir2 = tempDir.newDirectory("dir2");
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
    File targetDir = tempDir.newDirectory("target");
    File link1 = createSymLink(targetDir.getPath(), tempDir.getRoot() + "/link1");
    File link2 = createSymLink(targetDir.getPath(), tempDir.getRoot() + "/link2");
    assertVisitedPaths(targetDir.getPath(), link1.getPath(), link2.getPath());
  }

  @Test
  public void testSidewaysRecursiveLink() {
    File a = tempDir.newDirectory("a");
    File b = createTestDir(a, "b");
    File link1 = createSymLink(SystemInfo.isWindows ? a.getPath() : "../../" + a.getName(), b.getPath() + "/link1");
    File project = tempDir.newDirectory("project");
    File c = createTestDir(project, "c");
    File d = createTestDir(c, "d");
    File link2 = createSymLink(SystemInfo.isWindows ? a.getPath() : "../../../" + a.getName(), d.getPath() + "/link2");
    assertVisitedPaths(project,
                       c.getPath(), d.getPath(), link2.getPath(), link2.getPath() + "/" + b.getName(),
                       link2.getPath() + "/" + b.getName() + "/" + link1.getName());
  }

  @Test
  public void testVisitAllNonRecursiveLinks() {
    File target = tempDir.newDirectory("target");
    File child = createTestDir(target, "child");
    File link1 = createSymLink(target.getPath(), tempDir.getRoot() + "/link1");
    File link2 = createSymLink(target.getPath(), tempDir.getRoot() + "/link2");
    assertVisitedPaths(target.getPath(), child.getPath(),
                       link1.getPath(), link1.getPath() + "/child",
                       link2.getPath(), link2.getPath() + "/child");
  }

  @Test
  public void testTargetIsWritable() {
    File targetFile = tempDir.newFile("target.txt");
    File linkFile = createSymLink(targetFile.getPath(), tempDir.getRoot() + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() &&
                                                            linkVFile.is(VFileProperty.SYMLINK));

    setWritableAndCheck(targetFile, true);
    refresh(tempDir.getRoot());
    assertTrue(linkVFile.getPath(), linkVFile.isWritable());
    setWritableAndCheck(targetFile, false);
    refresh(tempDir.getRoot());
    assertFalse(linkVFile.getPath(), linkVFile.isWritable());

    File targetDir = tempDir.newDirectory("target");
    File linkDir = createSymLink(targetDir.getPath(), tempDir.getRoot() + "/linkDir");
    VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir, linkVDir != null && linkVDir.isDirectory() && linkVDir.is(VFileProperty.SYMLINK));

    if (!SystemInfo.isWindows) {
      setWritableAndCheck(targetDir, true);
      refresh(tempDir.getRoot());
      assertTrue(linkVDir.getPath(), linkVDir.isWritable());
      setWritableAndCheck(targetDir, false);
      refresh(tempDir.getRoot());
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
    File targetFile = tempDir.newFile("target");
    File linkFile = createSymLink(targetFile.getPath(), tempDir.getRoot() + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile,
               linkVFile != null && !linkVFile.isDirectory() && linkVFile.is(VFileProperty.SYMLINK));

    WriteAction.runAndWait(() -> linkVFile.delete(this));
    assertFalse(linkVFile.toString(), linkVFile.isValid());
    assertFalse(linkFile.exists());
    assertTrue(targetFile.exists());

    File targetDir = tempDir.newDirectory("targetDir");
    File childFile = new File(targetDir, "child.txt");
    assertTrue(childFile.getPath(), childFile.exists() || childFile.createNewFile());
    File linkDir = createSymLink(targetDir.getPath(), tempDir.getRoot() + "/linkDir");
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
    File targetFile = tempDir.newFile("target");
    File targetDir = tempDir.newDirectory("targetDir");

    // file link
    File link = createSymLink(targetFile.getPath(), tempDir.getRoot() + "/link");
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
    link = createSymLink(targetDir.getPath(), tempDir.getRoot() + "/link");
    refresh(tempDir.getRoot());
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && vFile1.isDirectory() && vFile1.is(VFileProperty.SYMLINK));

    // dir link => file
    assertTrue(link.getPath(), link.delete() && link.createNewFile() && link.isFile());
    refresh(tempDir.getRoot());
    vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile1.isValid() && vFile2 != null && !vFile2.isDirectory() && !vFile2.is(VFileProperty.SYMLINK));

    // file => file link
    assertTrue(link.getPath(), link.delete());
    link = createSymLink(targetFile.getPath(), tempDir.getRoot() + "/link");
    refresh(tempDir.getRoot());
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && !vFile1.isDirectory() && vFile1.is(VFileProperty.SYMLINK));
  }

  @Test
  public void testDirLinkSwitch() throws Exception {
    Path target1Child = tempDir.newFile("target1/child1.txt", "text".getBytes(StandardCharsets.UTF_8)).toPath();
    Path target2Child = tempDir.newFile("target2/child1.txt", "longer text".getBytes(StandardCharsets.UTF_8)).toPath();
    tempDir.newFile("target2/child2.txt");
    Path target1 = target1Child.getParent(), target2 = target2Child.getParent();
    Path link = tempDir.getRoot().toPath().resolve("link");
    createSymbolicLink(link, target1);
    VirtualFile vLink = refreshAndFind(link.toFile());
    assertTrue("link=" + link + ", vLink=" + vLink, vLink != null && vLink.isDirectory() && vLink.is(VFileProperty.SYMLINK));
    vLink.setCharset(StandardCharsets.UTF_8);
    assertEquals(1, vLink.getChildren().length);
    assertPathsEqual(target1.toString(), vLink.getCanonicalPath());
    assertEquals(Files.readString(target1Child), VfsUtilCore.loadText(vLink.findChild("child1.txt")));

    Files.delete(link);
    createSymbolicLink(link, target2);
    refresh(tempDir.getRoot());
    assertTrue("vLink=" + vLink, vLink.isValid());
    assertEquals(2, vLink.getChildren().length);
    assertEquals(Files.readString(target2Child), VfsUtilCore.loadText(vLink.findChild("child1.txt")));
    assertPathsEqual(target2.toString(), vLink.getCanonicalPath());
  }

  @Test
  public void testFileLinkSwitch() throws Exception {
    Path target1 = tempDir.newFile("target1.txt", "text".getBytes(StandardCharsets.UTF_8)).toPath();
    Path target2 = tempDir.newFile("target2.txt", "longer text".getBytes(StandardCharsets.UTF_8)).toPath();
    Path link = tempDir.getRoot().toPath().resolve("link");
    createSymbolicLink(link, target1);
    VirtualFile vLink = refreshAndFind(link.toFile());
    assertTrue("link=" + link + ", vLink=" + vLink, vLink != null && !vLink.isDirectory() && vLink.is(VFileProperty.SYMLINK));
    vLink.setCharset(StandardCharsets.UTF_8);
    assertEquals(Files.readString(target1), VfsUtilCore.loadText(vLink));
    assertPathsEqual(target1.toString(), vLink.getCanonicalPath());

    Files.delete(link);
    createSymbolicLink(link, target2);
    refresh(tempDir.getRoot());
    assertTrue("vLink=" + vLink, vLink.isValid());
    assertEquals(Files.readString(target2), VfsUtilCore.loadText(vLink));
    assertPathsEqual(target2.toString(), vLink.getCanonicalPath());
  }

  @Test
  public void testTraversePathBehindLink() {
    File topDir = tempDir.newDirectory("top");
    File subDir1 = createTestDir(topDir, "sub1");
    File link = createSymLink(subDir1.getPath(), tempDir.getRoot() + "/link");
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

  @Test
  public void testCircularSymlinksMustBeDetected() {
    File top = tempDir.newDirectory("top");
    File sub1 = createTestDir(top, "sub1");
    File link = createSymLink(top.getPath(), sub1.getPath() + "/link");
    VirtualFile vLink = refreshAndFind(link);
    assertNotNull(link.getPath(), vLink);

    String path = sub1.getPath() + StringUtil.repeat("/" + link.getName() + "/" + sub1.getName(), 10);
    VirtualFile f = LocalFileSystem.getInstance().findFileByPath(path);
    assertNotNull(f);
    while (!VfsUtilCore.pathEqualsTo(f, sub1.getPath())) {
      if (f.getName().equals(link.getName())) {
        assertTrue(f.getPath(),f.is(VFileProperty.SYMLINK));
        assertTrue(f.getPath(), f.isRecursiveOrCircularSymlink());
      }
      else {
        assertEquals(f.getPath(), sub1.getName(), f.getName());
        assertFalse(f.getPath(),f.is(VFileProperty.SYMLINK));
        assertFalse(f.isRecursiveOrCircularSymlink());
      }
      f = f.getParent();
    }
  }

  @Test
  public void testCircularSymlinksMustBeDetectedEvenForAsideLinks() {
    File top = tempDir.newDirectory("top");
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
    assertTrue(f.getPath(), f.isRecursiveOrCircularSymlink());
    f = f.getParent();
    assertEquals(ss1.getName(), f.getName());
    assertFalse(f.getPath(), f.is(VFileProperty.SYMLINK));
    assertFalse(f.getPath(), f.isRecursiveOrCircularSymlink());
    f = f.getParent();
    assertEquals(link1.getName(), f.getName());
    assertTrue(f.getPath(), f.is(VFileProperty.SYMLINK));
    assertTrue(f.getPath(), f.isRecursiveOrCircularSymlink());
  }

  //<editor-fold desc="Helpers.">
  private @Nullable static VirtualFile refreshAndFind(File ioFile) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
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
    assertVisitedPaths(tempDir.getRoot(), expected);
  }

  private static void assertVisitedPaths(File from, String... expected) {
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
  //</editor-fold>
}
