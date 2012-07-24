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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformLangTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.io.IoTestUtil.createTempLink;

public class SymlinkHandlingTest extends LightPlatformLangTestCase {
  private LocalFileSystem myFileSystem;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFileSystem = LocalFileSystem.getInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    myFileSystem = null;
    super.tearDown();
  }

  @Override
  protected void runTest() throws Throwable {
    if (SystemInfo.areSymLinksSupported) {
      super.runTest();
    }
    else {
      System.err.println("Skipped: " + getName());
    }
  }

  public void testBadLinksAreIgnored() throws Exception {
    final File missingFile = new File(FileUtil.getTempDirectory(), "missing_file");
    assertTrue(missingFile.getAbsolutePath(), !missingFile.exists() || missingFile.delete());
    final File missingLinkFile = createTempLink(missingFile.getAbsolutePath(), "missing_link");
    final VirtualFile missingLinkVFile = refreshAndFind(missingLinkFile);
    assertNull(missingLinkVFile);

    final File selfLinkFile = createTempLink("self_link", "self_link");
    final VirtualFile selfLinkVFile = refreshAndFind(selfLinkFile);
    assertNull(selfLinkVFile);

    final File pointLinkFile = createTempLink(".", "point_link");
    final VirtualFile pointLinkVFile = refreshAndFind(pointLinkFile);
    assertNotNull(pointLinkVFile);
    assertEquals(0, pointLinkVFile.getChildren().length);

    final File circularDir1 = FileUtil.createTempDirectory("dir1.", null);
    final File circularDir2 = FileUtil.createTempDirectory("dir2.", null);
    final File circularLink1 = createTempLink(circularDir2.getAbsolutePath(), circularDir1 + File.separator + "link");
    final File circularLink2 = createTempLink(circularDir1.getAbsolutePath(), circularDir2 + File.separator + "link");
    final VirtualFile circularLink1VFile = refreshAndFind(circularLink1);
    final VirtualFile circularLink2VFile = refreshAndFind(circularLink2);
    assertNotNull(circularLink1VFile);
    assertNotNull(circularLink2VFile);
    assertEquals(1, circularLink1VFile.getChildren().length);
    assertEquals(1, circularLink2VFile.getChildren().length);
    assertEquals(0, circularLink1VFile.getChildren()[0].getChildren().length);
    assertEquals(0, circularLink2VFile.getChildren()[0].getChildren().length);
  }

  public void testTargetIsWritable() throws Exception {
    final File targetFile = FileUtil.createTempFile("target", "");
    final File linkFile = createTempLink(targetFile.getAbsolutePath(), "link");
    final VirtualFile linkVFile = refreshAndFind(linkFile);
    assertTrue("link=" + linkFile + ", vLink=" + linkVFile, linkVFile != null && !linkVFile.isDirectory() && linkVFile.isSymLink());

    assertTrue(targetFile.getAbsolutePath(), targetFile.setWritable(true, false) && targetFile.canWrite());
    refresh();
    assertTrue(linkVFile.getPath(), linkVFile.isWritable());
    assertTrue(targetFile.getAbsolutePath(), targetFile.setWritable(false, false) && !targetFile.canWrite());
    refresh();
    assertFalse(linkVFile.getPath(), linkVFile.isWritable());

    final File targetDir = FileUtil.createTempDirectory("targetDir", "");
    final File linkDir = createTempLink(targetDir.getAbsolutePath(), "linkDir");
    final VirtualFile linkVDir = refreshAndFind(linkDir);
    assertTrue("link=" + linkDir + ", vLink=" + linkVDir, linkVDir != null && linkVDir.isDirectory() && linkVDir.isSymLink());

    if (!SystemInfo.isWindows) {
      assertTrue(targetDir.getAbsolutePath(), targetDir.setWritable(true, false) && targetDir.canWrite());
      refresh();
      assertTrue(linkVDir.getPath(), linkVDir.isWritable());
      assertTrue(targetDir.getAbsolutePath(), targetDir.setWritable(false, false) && !targetDir.canWrite());
      refresh();
      assertFalse(linkVDir.getPath(), linkVDir.isWritable());
    }
    else {
      assertEquals(linkVDir.getPath(), targetDir.canWrite(), linkVDir.isWritable());
    }
  }

  public void testLinkDeleteIsSafe() throws Exception {
    final File targetFile = FileUtil.createTempFile("target", "");
    final File linkFile = createTempLink(targetFile.getAbsolutePath(), "link");
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

    final File targetDir = FileUtil.createTempDirectory("targetDir", "");
    final File childFile = new File(targetDir, "child.txt");
    assertTrue(childFile.getAbsolutePath(), childFile.exists() || childFile.createNewFile());
    final File linkDir = createTempLink(targetDir.getAbsolutePath(), "linkDir");
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
    final File targetFile = FileUtil.createTempFile("target", "");
    final File targetDir = FileUtil.createTempDirectory("targetDir", "");

    // file link
    File link = createTempLink(targetFile.getAbsolutePath(), "link");
    VirtualFile vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               vFile1 != null && !vFile1.isDirectory() && vFile1.isSymLink());

    // file link => dir
    assertTrue(link.getAbsolutePath(), link.delete() && link.mkdir() && link.isDirectory());
    VirtualFile vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile2,
               !vFile1.isValid() && vFile2 != null && vFile2.isDirectory() && !vFile2.isSymLink());

    // dir => dir link
    assertTrue(link.getAbsolutePath(), link.delete());
    link = createTempLink(targetDir.getAbsolutePath(), "link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && vFile1.isDirectory() && vFile1.isSymLink());

    // dir link => file
    assertTrue(link.getAbsolutePath(), link.delete() && link.createNewFile() && link.isFile());
    vFile2 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile1.isValid() && vFile2 != null && !vFile2.isDirectory() && !vFile2.isSymLink());

    // file => file link
    assertTrue(link.getAbsolutePath(), link.delete());
    link = createTempLink(targetFile.getAbsolutePath(), "link");
    vFile1 = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vFile1,
               !vFile2.isValid() && vFile1 != null && !vFile1.isDirectory() && vFile1.isSymLink());
  }

  public void testLinkSwitch() throws Exception {
    final File targetDir1 = FileUtil.createTempDirectory("targetDir1", "");
    final File targetDir2 = FileUtil.createTempDirectory("targetDir2", "");
    assertTrue(new File(targetDir1, "child1.txt").createNewFile());
    assertTrue(new File(targetDir2, "child11.txt").createNewFile());
    assertTrue(new File(targetDir2, "child12.txt").createNewFile());

    final File link = createTempLink(targetDir1.getAbsolutePath(), "link");
    VirtualFile vLink = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink,
               vLink != null && vLink.isDirectory() && vLink.isSymLink());
    assertEquals(1, vLink.getChildren().length);

    assertTrue(link.toString(), link.delete());
    createTempLink(targetDir2.getAbsolutePath(), link.getName());

    vLink = refreshAndFind(link);
    assertTrue("link=" + link + ", vLink=" + vLink,
               vLink != null && vLink.isDirectory() && vLink.isSymLink());
    assertEquals(2, vLink.getChildren().length);
  }

  public void testContentSynchronization() throws Exception {
    final File file = FileUtil.createTempFile("file.", ".txt");
    final VirtualFile vFile = refreshAndFind(file);
    assertNotNull(file.getPath(), vFile);
    assertTrue(file.getPath(), vFile.isValid());

    final File link1 = createTempLink(file.getPath(), "link1-" + file.getName());
    final File link2 = createTempLink(file.getPath(), "link2-" + link1.getName());
    final VirtualFile vLink = refreshAndFind(link2);
    assertNotNull(link2.getPath(), vLink);
    assertTrue(link2.getPath(), vLink.isValid());

    String fileContent = VfsUtilCore.loadText(vFile);
    assertEquals("", fileContent);
    String linkContent = VfsUtilCore.loadText(vLink);
    assertEquals("", linkContent);

    fileContent = "new content";
    vFile.setBinaryContent(fileContent.getBytes());
    assertEquals(fileContent.length(), vLink.getLength());
    assertEquals(fileContent.length(), vFile.getLength());
    linkContent = VfsUtilCore.loadText(vLink);
    assertEquals(fileContent, linkContent);

    linkContent = "newer content";
    vLink.setBinaryContent(linkContent.getBytes());
    assertEquals(linkContent.length(), vLink.getLength());
    assertEquals(linkContent.length(), vFile.getLength());
    fileContent = VfsUtilCore.loadText(vFile);
    assertEquals(linkContent, fileContent);
  }

  public void testFindByLinkParentPath() throws Exception {
    final File topDir = FileUtil.createTempDirectory("topDir.", null);
    final File subDir1 = FileUtil.createTempDirectory(topDir, "subDir1.", null);
    final File link = createTempLink(subDir1.getAbsolutePath(), "link");
    final VirtualFile vLink = refreshAndFind(link);
    assertNotNull(link.getPath(), vLink);

    final File subDir2 = FileUtil.createTempDirectory(topDir, "subDir2.", null);
    final File subChild = FileUtil.createTempFile(subDir2, "subChild.", ".txt", true);
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
    return myFileSystem.findFileByPath(ioFile.getAbsolutePath());
  }

  private void refresh() {
    final String tempPath = FileUtil.getTempDirectory();
    final VirtualFile tempDir = myFileSystem.findFileByPath(tempPath);
    assertNotNull(tempPath, tempDir);
    tempDir.refresh(false, true);
  }
}
