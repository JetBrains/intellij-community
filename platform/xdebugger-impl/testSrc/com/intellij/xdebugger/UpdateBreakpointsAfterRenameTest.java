// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class UpdateBreakpointsAfterRenameTest extends XBreakpointsTestCase {
  @Rule
  public TestRule timeout = new DisableOnDebug(Timeout.seconds(30));

  @Test
  public void testRenameFile() throws IOException {
    final VirtualFile file = createFile("file.txt");
    XLineBreakpoint<?> b = putBreakpoint(file);
    rename(file, "file2.txt");
    assertTrue(b.getFileUrl().endsWith("file2.txt"));
    assertSame(b, getBreakpointManager().findBreakpointAtLine(XDebuggerTestCase.MY_LINE_BREAKPOINT_TYPE, file, 0));
  }

  @Test
  public void testMoveFile() throws IOException {
    final VirtualFile file = createFile("dir/a.txt");
    final VirtualFile targetDir = createFile("dir2/b.txt").getParent();
    final XLineBreakpoint<?> b = putBreakpoint(file);
    move(file, targetDir);
    assertTrue(b.getFileUrl().endsWith("dir2/a.txt"));
  }

  @Test
  public void testRenameParentDir() throws IOException {
    final VirtualFile file = createFile("dir/x.txt");
    final XLineBreakpoint<?> b = putBreakpoint(file);
    rename(file.getParent(), "dir2");
    assertTrue(b.getFileUrl().endsWith("dir2/x.txt"));
  }

  private XLineBreakpoint<?> putBreakpoint(final VirtualFile file) {
    return getBreakpointManager().addLineBreakpoint(XDebuggerTestCase.MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, null, false);
  }

  private VirtualFile createFile(@NotNull String path) throws IOException {
    Path ioFile = getTempDir().newPath().resolve(path);
    Files.createDirectories(ioFile.getParent());
    Files.createFile(ioFile);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(ioFile);
    assertNotNull(virtualFile);
    return virtualFile;
  }

  protected XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }
}
