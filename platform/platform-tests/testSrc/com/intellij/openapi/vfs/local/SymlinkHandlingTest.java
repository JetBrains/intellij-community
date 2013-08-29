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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;

public class SymlinkHandlingTest extends SymlinkTestCase {
  public void testMissingLink() throws Exception {
    File missingFile = new File(myTempDir, "missing_file");
    assertTrue(missingFile.getPath(), !missingFile.exists() || missingFile.delete());
    File missingLinkFile = createSymLink(missingFile.getPath(), myTempDir.getPath() + "/missing_link", false);
    VirtualFile missingLinkVFile = refreshAndFind(missingLinkFile);
    assertNotNull(missingLinkVFile);
    assertBrokenLink(missingLinkVFile);
    assertVisitedPaths(missingLinkVFile.getPath());
  }

  public void testSelfLink() throws Exception {
    String target = new File(myTempDir.getPath(), "self_link").getPath();
    File selfLinkFile = createSymLink(target, target, false);
    VirtualFile selfLinkVFile = refreshAndFind(selfLinkFile);
    assertNotNull(selfLinkVFile);
    assertBrokenLink(selfLinkVFile);
    assertVisitedPaths(selfLinkVFile.getPath());
  }

  public void testDotLink() throws Exception {
    File dotLinkFile = createSymLink(".", myTempDir + "/dot_link");
    VirtualFile dotLinkVFile = refreshAndFind(dotLinkFile);
    assertNotNull(dotLinkVFile);
    assertTrue(dotLinkVFile.is(VFileProperty.SYMLINK));
    assertTrue(dotLinkVFile.isDirectory());
    assertPathsEqual(myTempDir.getPath(), dotLinkVFile.getCanonicalPath());
    assertVisitedPaths(dotLinkVFile.getPath());
  }

  public void testCircularLink() throws Exception {
    File upDir = createTestDir(myTempDir, "sub");
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

  public void testMutualRecursiveLinks() throws Exception {
    File circularDir1 = createTestDir(myTempDir, "dir1");
    File circularDir2 = createTestDir(myTempDir, "dir2");
    File circularLink1 = createSymLink(circularDir2.getPath(), circularDir1 + "/link1");
    File circularLink2 = createSymLink(circularDir1.getPath(), circularDir2 + "/link2");
    VirtualFile circularLink1VFile = refreshAndFind(circularLink1);
    VirtualFile circularLink2VFile = refreshAndFind(circularLink2);
    assertNotNull(circularLink1VFile);
    assertNotNull(circularLink2VFile);
    assertVisitedPaths(circularDir1.getPath(), circularLink1.getPath(), circularLink1.getPath() + "/" + circularLink2.getName(),
                       circularDir2.getPath(), circularLink2.getPath(), circularLink2.getPath() + "/" + circularLink1.getName());
  }

  public void testDuplicateLinks() throws Exception {
    File targetDir = createTestDir(myTempDir, "target");
    File link1 = createSymLink(targetDir.getPath(), myTempDir + "/link1");
    File link2 = createSymLink(targetDir.getPath(), myTempDir + "/link2");
    assertVisitedPaths(targetDir.getPath(), link1.getPath(), link2.getPath());
  }

  public void testTargetIsWritable() throws Exception {
    File targetFile = createTestFile(myTempDir, "target.txt");
    File linkFile = createSymLink(targetFile.getPath(), myTempDir + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() &&
                                                            linkVFile.is(VFileProperty.SYMLINK));

    setWritableAndCheck(targetFile, true);
    refresh();
    assertTrue(linkVFile.getPath(), linkVFile.isWritable());
    setWritableAndCheck(targetFile, false);
    refresh();
    assertFalse(linkVFile.getPath(), linkVFile.isWritable());

    File targetDir = createTestDir(myTempDir, "target");
    File linkDir = createSymLink(targetDir.getPath(), myTempDir + "/linkDir");
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

  public void testLinkDeleteIsSafe() throws Exception {
    File targetFile = createTestFile(myTempDir, "target");
    File linkFile = createSymLink(targetFile.getPath(), myTempDir + "/link");
    VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() &&
                                                            linkVFile.is(VFileProperty.SYMLINK));

    AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
    try {
      linkVFile.delete(this);
    }
    finally {
      token.finish();
    }
    assertFalse(linkVFile.toString(), linkVFile.isValid());
    assertFalse(linkFile.exists());
    assertTrue(targetFile.exists());

    File targetDir = createTestDir(myTempDir, "targetDir");
    File childFile = new File(targetDir, "child.txt");
    assertTrue(childFile.getPath(), childFile.exists() || childFile.createNewFile());
    File linkDir = createSymLink(targetDir.getPath(), myTempDir + "/linkDir");
    VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir,
               linkVDir != null && linkVDir.isDirectory() && linkVDir.is(VFileProperty.SYMLINK) && linkVDir.getChildren().length == 1);

    token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
    try {
      linkVDir.delete(this);
    }
    finally {
      token.finish();
    }
    assertFalse(linkVDir.toString(), linkVDir.isValid());
    assertFalse(linkDir.exists());
    assertTrue(targetDir.exists());
    assertTrue(childFile.exists());
  }

  public void testTransGenderRefresh() throws Exception {
    File targetFile = createTestFile(myTempDir, "target");
    File targetDir = createTestDir(myTempDir, "targetDir");

    // file link
    File link = createSymLink(targetFile.getPath(), myTempDir + "/link");
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
    link = createSymLink(targetDir.getPath(), myTempDir + "/link");
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
    link = createSymLink(targetFile.getPath(), myTempDir + "/link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && !vFile1.isDirectory() && vFile1.is(VFileProperty.SYMLINK));
  }

  public void testDirLinkSwitch() throws Exception {
    File targetDir1 = createTestDir(myTempDir, "target1");
    File targetDir2 = createTestDir(myTempDir, "target2");
    assertTrue(new File(targetDir1, "child1.txt").createNewFile());
    assertTrue(new File(targetDir2, "child11.txt").createNewFile());
    assertTrue(new File(targetDir2, "child12.txt").createNewFile());

    File link = createSymLink(targetDir1.getPath(), myTempDir + "/link");
    VirtualFile vLink1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink1,
               vLink1 != null && vLink1.isDirectory() && vLink1.is(VFileProperty.SYMLINK));
    assertEquals(1, vLink1.getChildren().length);
    assertPathsEqual(targetDir1.getPath(), vLink1.getCanonicalPath());

    assertTrue(link.toString(), link.delete());
    createSymLink(targetDir2.getPath(), myTempDir + "/" + link.getName());

    refresh();
    assertTrue(vLink1.isValid());
    VirtualFile vLink2 = myFileSystem.findFileByIoFile(link);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               vLink2 == vLink1 && vLink2.isDirectory() && vLink2.is(VFileProperty.SYMLINK));
    assertEquals(2, vLink2.getChildren().length);
    assertPathsEqual(targetDir2.getPath(), vLink1.getCanonicalPath());
  }

  public void testFileLinkSwitch() throws Exception {
    File target1 = createTestFile(myTempDir, "target1.txt");
    FileUtil.writeToFile(target1, "some text");
    File target2 = createTestFile(myTempDir, "target2.txt");
    FileUtil.writeToFile(target2, "some quite another text");

    File link = createSymLink(target1.getPath(), myTempDir + "/link");
    VirtualFile vLink1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink1,
               vLink1 != null && !vLink1.isDirectory() && vLink1.is(VFileProperty.SYMLINK));
    assertEquals(FileUtil.loadFile(target1), VfsUtilCore.loadText(vLink1));
    assertPathsEqual(target1.getPath(), vLink1.getCanonicalPath());

    assertTrue(link.toString(), link.delete());
    createSymLink(target2.getPath(), myTempDir + "/" + link.getName());

    refresh();
    assertTrue(vLink1.isValid());
    VirtualFile vLink2 = myFileSystem.findFileByIoFile(link);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               vLink2 == vLink1 && !vLink2.isDirectory() && vLink2.is(VFileProperty.SYMLINK));
    assertEquals(FileUtil.loadFile(target2), VfsUtilCore.loadText(vLink2));
    assertPathsEqual(target2.getPath(), vLink1.getCanonicalPath());
  }

/*
  public void testContentSynchronization() throws Exception {
    final File file = createTempFile(myTempDir, "file.", ".txt");
    final VirtualFile vFile = refreshAndFind(file);
    assertNotNull(file.getPath(), vFile);
    assertTrue(file.getPath(), vFile.isValid());

    final File link1 = createTempLink(file.getPath(), myTempDir + "/link1-" + file.getName());
    final File link2 = createTempLink(file.getPath(), myTempDir + "/link2-" + link1.getName());
    final VirtualFile vLink = refreshAndFind(link2);
    assertNotNull(link2.getPath(), vLink);
    assertTrue(link2.getPath(), vLink.isValid());

    String fileContent = VfsUtilCore.loadText(vFile);
    assertEquals("", fileContent);
    String linkContent = VfsUtilCore.loadText(vLink);
    assertEquals("", linkContent);

    fileContent = "new content";
    vFile.setBinaryContent(fileContent.getBytes());
    assertEquals(fileContent.length(), vFile.getLength());
    assertEquals(fileContent.length(), vLink.getLength());
    linkContent = VfsUtilCore.loadText(vLink);
    assertEquals(fileContent, linkContent);

    linkContent = "newer content";
    vLink.setBinaryContent(linkContent.getBytes());
    assertEquals(linkContent.length(), vLink.getLength());
    assertEquals(linkContent.length(), vFile.getLength());
    fileContent = VfsUtilCore.loadText(vFile);
    assertEquals(linkContent, fileContent);

    final String extContent = "changed externally";
    FileUtil.writeToFile(file, extContent.getBytes());
    refresh();
    assertEquals(extContent.length(), vFile.getLength());
    assertEquals(extContent.length(), vLink.getLength());
    fileContent = VfsUtilCore.loadText(vFile);
    linkContent = VfsUtilCore.loadText(vLink);
    assertEquals(extContent, fileContent);
    assertEquals(extContent, linkContent);
  }
*/

  public void testTraversePathBehindLink() throws Exception {
    File topDir = createTestDir(myTempDir, "top");
    File subDir1 = createTestDir(topDir, "sub1");
    File link = createSymLink(subDir1.getPath(), myTempDir + "/link");
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
    return myFileSystem.findFileByPath(ioFile.getPath());
  }

  private static void assertBrokenLink(@NotNull VirtualFile link) {
    assertTrue(link.is(VFileProperty.SYMLINK));
    assertEquals(0, link.getLength());
    assertNull(link.getCanonicalPath(), link.getCanonicalPath());
  }

  private void assertVisitedPaths(String... expected) {
    VirtualFile vDir = refreshAndFind(myTempDir);
    assertNotNull(vDir);

    Set<String> expectedSet = new HashSet<String>(expected.length + 1, 1);
    ContainerUtil.addAll(expectedSet, FileUtil.toSystemIndependentName(myTempDir.getPath()));
    ContainerUtil.addAll(expectedSet, ContainerUtil.map(expected, new Function<String, String>() {
      @Override
      public String fun(String path) {
        return FileUtil.toSystemIndependentName(path);
      }
    }));

    final Set<String> actualSet = new HashSet<String>();
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
