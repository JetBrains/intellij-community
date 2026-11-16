// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ui.EDT;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.xdebugger.XBreakpointsFixture.MY_LINE_BREAKPOINT_TYPE;
import static com.intellij.xdebugger.XBreakpointsFixtureKt.xBreakpointsFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
public class UpdateBreakpointsAfterRenameTest {
  private final TestFixture<XBreakpointsFixture> breakpointsFixture = xBreakpointsFixture();

  @Test
  public void testRenameFile() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    final VirtualFile file = fixture.createFile("file.txt");
    XLineBreakpoint<?> b = putBreakpoint(file);
    fixture.rename(file, "file2.txt");
    assertTrue(b.getFileUrl().endsWith("file2.txt"));
    assertSame(b, getBreakpointManager().findBreakpointAtLine(MY_LINE_BREAKPOINT_TYPE, file, 0));
  }

  @Test
  public void testMoveFile() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    final VirtualFile file = fixture.createFile("dir/a.txt");
    final VirtualFile targetDir = fixture.createFile("dir2/b.txt").getParent();
    final XLineBreakpoint<?> b = putBreakpoint(file);
    fixture.move(file, targetDir);
    assertTrue(b.getFileUrl().endsWith("dir2/a.txt"));
  }

  @Test
  public void testRenameParentDir() {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    final VirtualFile file = fixture.createFile("dir/x.txt");
    final XLineBreakpoint<?> b = putBreakpoint(file);
    fixture.rename(file.getParent(), "dir2");
    assertTrue(b.getFileUrl().endsWith("dir2/x.txt"));
  }

  @Test
  public void testDeleteFile(@TestDisposable Disposable disposable) throws IOException {
    XBreakpointsFixture fixture = breakpointsFixture.get();
    final VirtualFile file = fixture.createFile("file.txt");
    final XLineBreakpoint<?> breakpoint = putBreakpoint(file);
    AtomicBoolean breakpointRemoved = new AtomicBoolean();
    AtomicBoolean breakpointRemovedOnEdt = new AtomicBoolean();
    fixture.getProject().getMessageBus().connect(disposable).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<?> removedBreakpoint) {
        if (removedBreakpoint == breakpoint) {
          breakpointRemoved.set(true);
          breakpointRemovedOnEdt.set(EDT.isCurrentThreadEdt());
        }
      }
    });

    WriteAction.runAndWait(() -> file.delete(this));

    assertTrue(breakpointRemoved.get());
    assertFalse(breakpointRemovedOnEdt.get());
    assertFalse(fixture.getAllBreakpoints().contains(breakpoint));
  }

  private XLineBreakpoint<?> putBreakpoint(final VirtualFile file) {
    return getBreakpointManager().addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, file.getUrl(), 0, null, false);
  }

  private XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(breakpointsFixture.get().getProject()).getBreakpointManager();
  }
}
