/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static com.intellij.openapi.util.io.FileUtil.createTempFile;
import static com.intellij.openapi.util.io.IoTestUtil.createSymLink;
import static com.intellij.testFramework.PlatformTestUtil.assertPathsEqual;

public class SymlinkHandlingTest extends SymlinkTestCase {
  public void testMissingLink() throws Exception {
    final File missingFile = new File(myTempDir, "missing_file");
    assertTrue(missingFile.getPath(), !missingFile.exists() || missingFile.delete());
    final File missingLinkFile = createSymLink(missingFile.getPath(), myTempDir.getPath() + "/missing_link", false);
    final VirtualFile missingLinkVFile = refreshAndFind(missingLinkFile);
    assertNotNull(missingLinkVFile);
    assertBrokenLink(missingLinkVFile);
    assertVisitedPaths(missingLinkVFile.getPath());
  }

  public void testSelfLink() throws Exception {
    String target = new File(myTempDir.getPath(), "self_link").getPath();
    final File selfLinkFile = createSymLink(target, target, false);
    final VirtualFile selfLinkVFile = refreshAndFind(selfLinkFile);
    assertNotNull(selfLinkVFile);
    assertBrokenLink(selfLinkVFile);
    assertVisitedPaths(selfLinkVFile.getPath());
  }

  public void testDotLink() throws Exception {
    final File dotLinkFile = createSymLink(".", myTempDir + "/dot_link");
    final VirtualFile dotLinkVFile = refreshAndFind(dotLinkFile);
    assertNotNull(dotLinkVFile);
    assertTrue(dotLinkVFile.isSymLink());
    assertTrue(dotLinkVFile.isDirectory());
    assertPathsEqual(myTempDir.getPath(), dotLinkVFile.getCanonicalPath());
    assertVisitedPaths(dotLinkVFile.getPath());
  }

  public void testCircularLink() throws Exception {
    final File upDir = createTempDirectory(myTempDir, "sub.", ".dir");
    final File upLinkFile = createSymLink(upDir.getPath(), upDir.getPath() + "/up_link");
    final VirtualFile upLinkVFile = refreshAndFind(upLinkFile);
    assertNotNull(upLinkVFile);
    assertTrue(upLinkVFile.isSymLink());
    assertTrue(upLinkVFile.isDirectory());
    assertPathsEqual(upDir.getPath(), upLinkVFile.getCanonicalPath());
    assertVisitedPaths(upDir.getPath(), upLinkVFile.getPath());

    final File repeatedLinksFile = new File(upDir.getPath() + StringUtil.repeat(File.separator + upLinkFile.getName(), 4));
    assertTrue(repeatedLinksFile.getPath(), repeatedLinksFile.isDirectory());
    final VirtualFile repeatedLinksVFile = refreshAndFind(repeatedLinksFile);
    assertNotNull(repeatedLinksFile.getPath(), repeatedLinksVFile);
    assertTrue(repeatedLinksVFile.isSymLink());
    assertTrue(repeatedLinksVFile.isDirectory());
    assertPathsEqual(upDir.getPath(), repeatedLinksVFile.getCanonicalPath());
    assertEquals(upLinkVFile.getCanonicalFile(), repeatedLinksVFile.getCanonicalFile());
  }

  public void testMutualRecursiveLinks() throws Exception {
    final File circularDir1 = createTempDirectory(myTempDir, "dir1.", ".tmp");
    final File circularDir2 = createTempDirectory(myTempDir, "dir2.", ".tmp");
    final File circularLink1 = createSymLink(circularDir2.getPath(), circularDir1 + "/link1");
    final File circularLink2 = createSymLink(circularDir1.getPath(), circularDir2 + "/link2");
    final VirtualFile circularLink1VFile = refreshAndFind(circularLink1);
    final VirtualFile circularLink2VFile = refreshAndFind(circularLink2);
    assertNotNull(circularLink1VFile);
    assertNotNull(circularLink2VFile);
    assertVisitedPaths(circularDir1.getPath(), circularLink1.getPath(), circularLink1.getPath() + "/" + circularLink2.getName(),
                       circularDir2.getPath(), circularLink2.getPath(), circularLink2.getPath() + "/" + circularLink1.getName());
  }

  public void testDuplicateLinks() throws Exception {
    final File targetDir = createTempDirectory(myTempDir, "target.", ".dir");
    final File link1 = createSymLink(targetDir.getPath(), myTempDir + "/link1");
    final File link2 = createSymLink(targetDir.getPath(), myTempDir + "/link2");
    assertVisitedPaths(targetDir.getPath(), link1.getPath(), link2.getPath());
  }

  public void testTargetIsWritable() throws Exception {
    final File targetFile = createTempFile(myTempDir, "target.", ".txt");
    final File linkFile = createSymLink(targetFile.getPath(), myTempDir + "/link");
    final VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() && linkVFile.isSymLink());

    setWritableAndCheck(targetFile, true);
    refresh();
    assertTrue(linkVFile.getPath(), linkVFile.isWritable());
    setWritableAndCheck(targetFile, false);
    refresh();
    assertFalse(linkVFile.getPath(), linkVFile.isWritable());

    final File targetDir = createTempDirectory(myTempDir, "target.", ".dir");
    final File linkDir = createSymLink(targetDir.getPath(), myTempDir + "/linkDir");
    final VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir, linkVDir != null && linkVDir.isDirectory() && linkVDir.isSymLink());

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
    final File targetFile = createTempFile(myTempDir, "target", "");
    final File linkFile = createSymLink(targetFile.getPath(), myTempDir + "/link");
    final VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() && linkVFile.isSymLink());

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

    final File targetDir = createTempDirectory(myTempDir, "targetDir", "");
    final File childFile = new File(targetDir, "child.txt");
    assertTrue(childFile.getPath(), childFile.exists() || childFile.createNewFile());
    final File linkDir = createSymLink(targetDir.getPath(), myTempDir + "/linkDir");
    final VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir,
               linkVDir != null && linkVDir.isDirectory() && linkVDir.isSymLink() && linkVDir.getChildren().length == 1);

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
    final File targetFile = createTempFile(myTempDir, "target", "");
    final File targetDir = createTempDirectory(myTempDir, "targetDir", "");

    // file link
    File link = createSymLink(targetFile.getPath(), myTempDir + "/link");
    VirtualFile vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               vFile1 != null && !vFile1.isDirectory() && vFile1.isSymLink());

    // file link => dir
    assertTrue(link.getPath(), link.delete() && link.mkdir() && link.isDirectory());
    VirtualFile vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile2,
               !vFile1.isValid() && vFile2 != null && vFile2.isDirectory() && !vFile2.isSymLink());

    // dir => dir link
    assertTrue(link.getPath(), link.delete());
    link = createSymLink(targetDir.getPath(), myTempDir + "/link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && vFile1.isDirectory() && vFile1.isSymLink());

    // dir link => file
    assertTrue(link.getPath(), link.delete() && link.createNewFile() && link.isFile());
    vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile1.isValid() && vFile2 != null && !vFile2.isDirectory() && !vFile2.isSymLink());

    // file => file link
    assertTrue(link.getPath(), link.delete());
    link = createSymLink(targetFile.getPath(), myTempDir + "/link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && !vFile1.isDirectory() && vFile1.isSymLink());
  }

  public void testDirLinkSwitch() throws Exception {
    final File targetDir1 = createTempDirectory(myTempDir, "target1.", ".dir");
    final File targetDir2 = createTempDirectory(myTempDir, "target2.", ".dir");
    assertTrue(new File(targetDir1, "child1.txt").createNewFile());
    assertTrue(new File(targetDir2, "child11.txt").createNewFile());
    assertTrue(new File(targetDir2, "child12.txt").createNewFile());

    final File link = createSymLink(targetDir1.getPath(), myTempDir + "/link");
    final VirtualFile vLink1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink1,
               vLink1 != null && vLink1.isDirectory() && vLink1.isSymLink());
    assertEquals(1, vLink1.getChildren().length);

    assertTrue(link.toString(), link.delete());
    createSymLink(targetDir2.getPath(), myTempDir + "/" + link.getName());

    refresh();
    assertFalse(vLink1.isValid());
    final VirtualFile vLink2 = myFileSystem.findFileByIoFile(link);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               vLink2 != null && vLink2.isDirectory() && vLink2.isSymLink());
    assertEquals(2, vLink2.getChildren().length);
  }

  public void testFileLinkSwitch() throws Exception {
    final File target1 = createTempFile(myTempDir, "target1.", ".txt");
    FileUtil.writeToFile(target1, "some text");
    final File target2 = createTempFile(myTempDir, "target2.", ".txt");
    FileUtil.writeToFile(target2, "some quite another text");

    final File link = createSymLink(target1.getPath(), myTempDir + "/link");
    final VirtualFile vLink1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink1,
               vLink1 != null && !vLink1.isDirectory() && vLink1.isSymLink());
    assertEquals(FileUtil.loadFile(target1), VfsUtilCore.loadText(vLink1));

    assertTrue(link.toString(), link.delete());
    createSymLink(target2.getPath(), myTempDir + "/" + link.getName());

    refresh();
    assertFalse(vLink1.isValid());
    final VirtualFile vLink2 = myFileSystem.findFileByIoFile(link);
    assertTrue("link=" + link + ", vLink=" + vLink2,
               vLink2 != null && !vLink2.isDirectory() && vLink2.isSymLink());
    assertEquals(FileUtil.loadFile(target2), VfsUtilCore.loadText(vLink2));
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
    final File topDir = createTempDirectory(myTempDir, "top.", ".dir");
    final File subDir1 = createTempDirectory(topDir, "sub1.", ".dir");
    final File link = createSymLink(subDir1.getPath(), myTempDir + "/link");
    final VirtualFile vLink = refreshAndFind(link);
    assertNotNull(link.getPath(), vLink);

    final File subDir2 = createTempDirectory(topDir, "sub2.", ".dir");
    final File subChild = createTempFile(subDir2, "subChild.", ".txt");
    final VirtualFile vSubChild = refreshAndFind(subChild);
    assertNotNull(subChild.getPath(), vSubChild);

    final String relPath = "../" + subDir2.getName() + "/" + subChild.getName();
    VirtualFile vSubChildRel;
    vSubChildRel = vLink.findFileByRelativePath(relPath);
    assertEquals(vSubChild, vSubChildRel);
    vSubChildRel = LocalFileSystem.getInstance().findFileByPath(vLink.getPath() + "/" + relPath);
    assertEquals(vSubChild, vSubChildRel);
  }

  @Nullable
  private VirtualFile refreshAndFind(final File ioFile) {
    refresh();
    return myFileSystem.findFileByPath(ioFile.getPath());
  }

  private static void assertBrokenLink(@NotNull final VirtualFile link) {
    assertTrue(link.isSymLink());
    assertEquals(0, link.getLength());
    assertNull(link.getCanonicalPath(), link.getCanonicalPath());
  }

  private void assertVisitedPaths(String... expected) {
    final VirtualFile vDir = myFileSystem.findFileByIoFile(myTempDir);
    assertNotNull(vDir);

    final Set<String> expectedSet = new HashSet<String>(expected.length + 1, 1);
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
