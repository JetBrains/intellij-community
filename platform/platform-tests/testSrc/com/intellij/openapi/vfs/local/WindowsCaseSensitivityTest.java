// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WindowsCaseSensitivityTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @BeforeClass
  public static void setUp() {
    assumeWindows();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());
    assumeTrue("'fsutil.exe' not found in %Path%", PathEnvironmentVariableUtil.findInPath("fsutil.exe") != null);
    assumeTrue("'wsl.exe' not found in %Path% (needed for 'setCaseSensitiveInfo')", PathEnvironmentVariableUtil.findInPath("wsl.exe") != null);
  }

  @Test
  public void testCaseSensitiveDirectoryUnderWindowsMustBeDetected() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertFalse(vDir.isCaseSensitive());
    IoTestUtil.setCaseSensitivity(dir, true);
    VirtualFile readme = createChildData(vDir, "readme.txt");
    assertTrue(readme.isCaseSensitive());
  }

  @Test
  public void testFSUtilWorks_tempTest() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertFalse(vDir.isCaseSensitive());
    IoTestUtil.setCaseSensitivity(dir, true);
    VirtualFile readme = createChildData(vDir, "readme.txt");
    VirtualFile README = createChildData(vDir, "README.TXT");
    assertNotEquals(((VirtualFileSystemEntry)readme).getId(), ((VirtualFileSystemEntry)README).getId());
    assertTrue(readme.isCaseSensitive());
    assertTrue(README.isCaseSensitive());
  }

  private static VirtualFile createChildData(VirtualFile dir, String name) throws IOException {
    return WriteAction.computeAndWait(() -> dir.createChildData(null, name));
  }

  @Test
  public void testChangeCaseSensitivityMidWayMustNotLeadToCollisions() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertFalse(vDir.isCaseSensitive());
    VirtualFile readme = createChildData(vDir, "readme.txt");
    IoTestUtil.setCaseSensitivity(dir, true);
    vDir.refresh(false, true);
    VirtualFile README = createChildData(vDir, "README.TXT");  // must not fail with "already exists" exception
    assertTrue(vDir.isCaseSensitive());
    assertNotEquals(readme, README);
  }

  @Test
  public void testChangeCSAndCreateNewFileMustLeadToImmediateCSFlagUpdate() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualDirectoryImpl vDir = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertEquals(FileAttributes.CaseSensitivity.UNKNOWN, vDir.getChildrenCaseSensitivity());
    VirtualFile readme = createChildData(vDir, "readme.txt");
    IoTestUtil.setCaseSensitivity(dir, true);
    VirtualFile README = createChildData(vDir, "README.TXT");
    assertTrue(vDir.isCaseSensitive());
    assertNotEquals(readme, README);
  }

  @Test
  public void vfsEventMustBeFiredOnCaseSensitivityChange() throws IOException {
    String childName = "0";
    File ioFile = myTempDir.newFile("xxx/" + childName);
    assertTrue(ioFile.exists());
    VirtualDirectoryImpl dir = (VirtualDirectoryImpl)LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile.getParentFile());
    Semaphore eventFound = new Semaphore(1);
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable()).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener(){
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        VFileEvent changeEvent = ContainerUtil.find(events, event -> event instanceof VFilePropertyChangeEvent
                 && ((VFilePropertyChangeEvent)event).getPropertyName() == VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY
                 && dir.equals(event.getFile())
                 && ((VFilePropertyChangeEvent)event).getNewValue() == FileAttributes.CaseSensitivity.SENSITIVE);
        if (changeEvent != null) {
          eventFound.up();
        }
      }
    });
    IoTestUtil.setCaseSensitivity(ioFile.getParentFile(), true);
    assertNotNull(dir.findChild(childName));
    assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, dir.getChildrenCaseSensitivity());
    UIUtil.pump();
    eventFound.waitFor();
  }
}
