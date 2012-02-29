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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.testFramework.LightPlatformLangTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;

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

  public void testBadLinksAreIgnored() throws Exception {
    if (!SystemInfo.areSymLinksSupported) {
      System.out.println("Test not passed");
      return;
    }

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
    if (!VirtualDirectoryImpl.ALT_SYMLINK_HANDLING) {
      // todo[r.sh] deal with cross-circular links
      assertEquals(0, circularLink1VFile.getChildren()[0].getChildren().length);
      assertEquals(0, circularLink2VFile.getChildren()[0].getChildren().length);
    }
  }

  public void testTargetIsWritable() throws Exception {
    if (!SystemInfo.areSymLinksSupported) return;

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
    if (!SystemInfo.areSymLinksSupported) return;

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
    if (!SystemInfo.areSymLinksSupported) return;

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
    if (!SystemInfo.areSymLinksSupported) return;

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

  // todo[r.sh] use NIO2 API after migration to JDK 7
  private static File createTempLink(final String target, final String link) throws InterruptedException, ExecutionException {
    final boolean isAbsolute = SystemInfo.isUnix && StringUtil.startsWithChar(link, '/') ||
                               SystemInfo.isWindows && link.matches("^[c-zC-Z]:.*$");
    final File linkFile = isAbsolute ? new File(link) : new File(FileUtil.getTempDirectory(), link);
    assertTrue(link, !linkFile.exists() || linkFile.delete());
    final File parentDir = linkFile.getParentFile();
    assertTrue("link=" + link + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));

    final GeneralCommandLine commandLine;
    if (SystemInfo.isWindows) {
      commandLine = new File(target).isDirectory()
                    ? new GeneralCommandLine("cmd", "/C", "mklink", "/D", linkFile.getAbsolutePath(), target)
                    : new GeneralCommandLine("cmd", "/C", "mklink", linkFile.getAbsolutePath(), target);
    }
    else {
      commandLine = new GeneralCommandLine("ln", "-s", target, linkFile.getAbsolutePath());
    }
    final int res = commandLine.createProcess().waitFor();
    assertEquals(commandLine.getCommandLineString(), 0, res);

    final File targetFile = new File(target);
    assertEquals("target=" + target + ", link=" + linkFile, targetFile.exists(), linkFile.exists());
    return linkFile;
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
