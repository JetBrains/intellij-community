// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;

import java.io.File;

/**
 * @author nik
 */
public class UpdateBreakpointsAfterRenameTest extends XBreakpointsTestCase {
  public void testRenameFile() {
    final VirtualFile file = createFile("file.txt");
    XLineBreakpoint<?> b = putBreakpoint(file);
    rename(file, "file2.txt");
    assertTrue(b.getFileUrl().endsWith("file2.txt"));
    assertSame(b, getBreakpointManager().findBreakpointAtLine(XDebuggerTestCase.MY_LINE_BREAKPOINT_TYPE, file, 0));
  }

  public void testMoveFile() {
    final VirtualFile file = createFile("dir/a.txt");
    final VirtualFile targetDir = createFile("dir2/b.txt").getParent();
    final XLineBreakpoint<?> b = putBreakpoint(file);
    move(file, targetDir);
    assertTrue(b.getFileUrl().endsWith("dir2/a.txt"));
  }

  public void testRenameParentDir() {
    final VirtualFile file = createFile("dir/x.txt");
    final XLineBreakpoint<?> b = putBreakpoint(file);
    rename(file.getParent(), "dir2");
    assertTrue(b.getFileUrl().endsWith("dir2/x.txt"));
  }

  private XLineBreakpoint<?> putBreakpoint(final VirtualFile file) {
    return WriteAction.compute(() -> getBreakpointManager()
      .addLineBreakpoint(XDebuggerTestCase.MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, null, false));
  }

  private VirtualFile createFile(String path) {
    final File ioFile = new File(getTempDir().createTempDir(), FileUtil.toSystemDependentName(path));
    FileUtil.createIfDoesntExist(ioFile);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(virtualFile);
    return virtualFile;
  }

  protected XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }
}
