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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.jar.JarFile;

public class PersistentFsTest extends PlatformTestCase {
  private PersistentFS myFs;
  private LocalFileSystem myLocalFs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFs = PersistentFS.getInstance();
    myLocalFs = LocalFileSystem.getInstance();
  }

  @Override
  protected void tearDown() throws Exception {
    myLocalFs = null;
    myFs = null;
    super.tearDown();
  }

  public void testAccessingFileByID() throws Exception {
    File dir = createTempDirectory();
    File file = new File(dir, "test.txt");
    assertTrue(file.createNewFile());

    VirtualFile vFile = myLocalFs.refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);

    int id = ((VirtualFileWithId)vFile).getId();
    assertEquals(vFile, myFs.findFileById(id));

    delete(vFile);
    assertNull(myFs.findFileById(id));
  }

  public void testFindRootShouldNotBeFooledByRelativePath() throws Exception {
    File tmp = createTempDirectory();
    File x = new File(tmp, "x.jar");
    assertTrue(x.createNewFile());

    VirtualFile vx = myLocalFs.refreshAndFindFileByIoFile(x);
    assertNotNull(vx);

    JarFileSystem jfs = JarFileSystem.getInstance();
    VirtualFile root = jfs.getJarRootForLocalFile(vx);
    String path = vx.getPath() + "/../" + vx.getName() + JarFileSystem.JAR_SEPARATOR;
    assertSame(myFs.findRoot(path, jfs), root);
  }

  public void testFindRootMustCreateFileWithCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x.jar");
  }

  private void checkMustCreateRootWithCanonicalPath(String jarName) throws IOException {
    File tmp = createTempDirectory();
    File x = new File(tmp, jarName);
    assertTrue(x.createNewFile());

    JarFileSystem jfs = JarFileSystem.getInstance();
    String path = x.getPath() + "/../" + x.getName() + JarFileSystem.JAR_SEPARATOR;
    NewVirtualFile root = myFs.findRoot(path, jfs);
    assertFalse(root.getPath(), root.getPath().contains("../"));
    assertFalse(root.getPath(), root.getPath().contains("/.."));
  }

  public void testFindRootMustCreateFileWithStillCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x..jar");
  }

  public void testFindRootMustCreateFileWithYetAnotherCanonicalPath() throws Exception {
    checkMustCreateRootWithCanonicalPath("x...jar");
  }

  public void testDeleteSubstRoots() throws Exception {
    if (!SystemInfo.isWindows) return;

    File tempDirectory = FileUtil.createTempDirectory(getTestName(false), null);
    File substRoot = IoTestUtil.createSubst(tempDirectory.getPath());
    VirtualFile subst = myLocalFs.refreshAndFindFileByIoFile(substRoot);
    assertNotNull(subst);

    try {
      final File[] children = substRoot.listFiles();
      assertNotNull(children);
    }
    finally {
      IoTestUtil.deleteSubst(substRoot.getPath());
    }
    subst.refresh(false, true);

    VirtualFile[] roots = myFs.getRoots(myLocalFs);
    for (VirtualFile root : roots) {
      String rootPath = root.getPath();
      String prefix = StringUtil.commonPrefix(rootPath, substRoot.getPath());
      assertEmpty(prefix);
    }
  }

  public void testLocalRoots() {
    VirtualFile tempRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assertNotNull(tempRoot);

    VirtualFile[] roots = myFs.getLocalRoots();
    for (VirtualFile root : roots) {
      assertTrue("root=" + root, root.isInLocalFileSystem());
      VirtualFileSystem fs = root.getFileSystem();
      assertTrue("fs=" + fs, fs instanceof LocalFileSystem);
      assertFalse("fs=" + fs, fs instanceof TempFileSystem);
    }
  }

  public void testInvalidJarRootsIgnored() throws Exception {
    File file = IoTestUtil.createTestFile("file.txt");
    String url = "jar://" + FileUtil.toSystemIndependentName(file.getPath()) + "!/";
    assertNull(VirtualFileManager.getInstance().findFileByUrl(url));
  }

  public void testBrokenJarRoots() throws Exception {
    final File jarFile = IoTestUtil.createTestFile("empty.jar");

    final int[] logCount = {0};
    LoggedErrorProcessor.setNewInstance(new LoggedErrorProcessor() {
      @Override
      public void processWarn(String message, Throwable t, @NotNull Logger logger) {
        super.processWarn(message, t, logger);
        if (message.contains(jarFile.getName())) logCount[0]++;
      }
    });

    try {
      String rootUrl = "jar://" + FileUtil.toSystemIndependentName(jarFile.getPath()) + "!/";
      String entryUrl = rootUrl + JarFile.MANIFEST_NAME;
      assertNotNull(getVirtualFile(jarFile));
      VirtualFile jarRoot = VirtualFileManager.getInstance().findFileByUrl(rootUrl);
      assertNotNull(jarRoot);
      assertTrue(jarRoot.isValid());
      assertEquals(0, jarRoot.getChildren().length);
      assertNull(VirtualFileManager.getInstance().findFileByUrl(entryUrl));

      VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(jarRoot);
      assertNotNull(local);
      IoTestUtil.createTestJar(jarFile);
      local.refresh(false, false);
      assertTrue(jarRoot.isValid());
      assertEquals(1, jarRoot.getChildren().length);
      assertNotNull(VirtualFileManager.getInstance().findFileByUrl(entryUrl));
    }
    finally {
      LoggedErrorProcessor.restoreDefaultProcessor();
    }

    assertEquals(1, logCount[0]);
  }

  public void testModCountIncreases() throws IOException {
    VirtualFile vFile = setupFile();
    ManagingFS managingFS = ManagingFS.getInstance();
    int inSessionModCount = managingFS.getModificationCount();
    int globalModCount = managingFS.getFilesystemModificationCount();
    final int parentModCount = managingFS.getModificationCount(vFile.getParent());

    WriteAction.run(() -> vFile.setWritable(false));

    assertEquals(globalModCount + 1, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount + 1, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());

    FSRecords.force();
    assertFalse(FSRecords.isDirty());
    ++globalModCount;

    int finalGlobalModCount = globalModCount;

    try (AccessToken ignore = HeavyProcessLatch.INSTANCE.processStarted("This test wants no indices flush")) {
      WriteAction.run(() -> {
        final long timestamp = vFile.getTimeStamp();
        int finalInSessionModCount = managingFS.getModificationCount();
        vFile.setWritable(true);  // 1 change
        vFile.setBinaryContent("foo".getBytes(Charset.defaultCharset())); // content change + length change + maybe timestamp change

        // we check in write action to avoid observing background thread to index stuff
        final int changesCount = timestamp == vFile.getTimeStamp() ? 3 : 4;
        assertEquals(finalGlobalModCount + changesCount, managingFS.getModificationCount(vFile));
        assertEquals(finalGlobalModCount + changesCount, managingFS.getFilesystemModificationCount());
        assertEquals(finalInSessionModCount + changesCount, managingFS.getModificationCount());
        assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
      });
    }
  }

  @NotNull
  private static VirtualFile setupFile() throws IOException {
    File file = IoTestUtil.createTestFile("file.txt");
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vFile);
    return vFile;
  }

  public void testModCountNotIncreases() throws IOException {
    VirtualFile vFile = setupFile();
    ManagingFS managingFS = ManagingFS.getInstance();
    final int globalModCount = managingFS.getFilesystemModificationCount();
    final int parentModCount = managingFS.getModificationCount(vFile.getParent());
    int inSessionModCount = managingFS.getModificationCount();

    FSRecords.force();
    assertFalse(FSRecords.isDirty());

    FileAttribute attribute = new FileAttribute("test.attribute", 1, true);
    WriteAction.run(() -> {
      try(DataOutputStream output = attribute.writeAttribute(vFile)) {
        DataInputOutputUtil.writeINT(output, 1);
      }
    });

    assertEquals(globalModCount, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());

    assertTrue(FSRecords.isDirty());
    FSRecords.force();
    assertFalse(FSRecords.isDirty());

    //
    int fileId = ((VirtualFileWithId)vFile).getId();
    FSRecords.setTimestamp(fileId, FSRecords.getTimestamp(fileId));
    FSRecords.setLength(fileId, FSRecords.getLength(fileId));

    assertEquals(globalModCount, managingFS.getModificationCount(vFile));
    assertEquals(globalModCount, managingFS.getFilesystemModificationCount());
    assertEquals(parentModCount, managingFS.getModificationCount(vFile.getParent()));
    assertEquals(inSessionModCount + 1, managingFS.getModificationCount());
    assertFalse(FSRecords.isDirty());
  }
}
