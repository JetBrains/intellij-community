/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class SymLinkHandlingTest extends LightPlatformTestCase {
  private LocalFileSystem myFileSystem;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public SymLinkHandlingTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

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
    if (!SystemInfo.areSymLinksSupported) return;

    final File missingFile = new File(FileUtil.getTempDirectory(), "missing_file");
    assertTrue(missingFile.getAbsolutePath(), !missingFile.exists() || missingFile.delete());
    final File missingLinkFile = createTempLink(missingFile.getAbsolutePath(), "missing_link");
    final VirtualFile missingLinkVFile = refreshAndFind(missingLinkFile);
    assertNull(missingLinkVFile);

    final File selfLinkFile = createTempLink("self_link", "self_link");
    final VirtualFile selfLinkVFile = refreshAndFind(selfLinkFile);
    assertNull(selfLinkVFile);
  }

  public void testTargetIsWriteable() throws Exception {
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

  // todo[r.sh] use NIO2 API after migration to JDK 7
  private static File createTempLink(final String target, final String link) throws IOException, InterruptedException {
    final File linkFile = new File(FileUtil.getTempDirectory(), link);
    assertTrue(link, !linkFile.exists() || linkFile.delete());
    final File parentDir = linkFile.getParentFile();
    assertTrue("link=" + link + ", parent=" + parentDir, parentDir != null && (parentDir.isDirectory() || parentDir.mkdirs()));

    final ProcessBuilder builder;
    if (SystemInfo.isWindows) {
      builder = new File(target).isDirectory()
                ? new ProcessBuilder("cmd", "/C", "mklink", "/D", linkFile.getAbsolutePath(), target)
                : new ProcessBuilder("cmd", "/C", "mklink", linkFile.getAbsolutePath(), target);
    }
    else {
      builder = new ProcessBuilder("ln", "-s", target, linkFile.getAbsolutePath());
    }
    final Process process = builder.start();
    final int res = process.waitFor();
    assertTrue(builder.command() + ": " + res, res == 0);
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
