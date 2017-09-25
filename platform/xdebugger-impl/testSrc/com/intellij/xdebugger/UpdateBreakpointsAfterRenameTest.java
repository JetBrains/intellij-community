/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
    final File ioFile = new File(myTempFiles.createTempDir(), FileUtil.toSystemDependentName(path));
    FileUtil.createIfDoesntExist(ioFile);
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(virtualFile);
    return virtualFile;
  }

  protected XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }
}
