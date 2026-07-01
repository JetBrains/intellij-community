// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement;
import com.intellij.xdebugger.impl.proxy.MonolithBreakpointManagerKt;
import com.intellij.xdebugger.impl.proxy.MonolithBreakpointProxyKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public final class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, LineBreakpointState>
  implements XLineBreakpoint<P> {

  private final BreakpointDraggableObjectFactory myBreakpointDraggableObjectFactory;

  private final XLineBreakpointType<P> myType;
  private volatile XSourcePosition mySourcePosition;

  public XLineBreakpointImpl(final XLineBreakpointType<P> type,
                             XBreakpointManagerImpl breakpointManager,
                             final @Nullable P properties, LineBreakpointState state) {
    super(type, breakpointManager, properties, state);
    myType = type;
    myBreakpointDraggableObjectFactory = new BreakpointDraggableObjectFactory(
      MonolithBreakpointManagerKt.asProxy(breakpointManager),
      MonolithBreakpointProxyKt.asProxy(this)
    );
  }

  /**
   * @deprecated The platform handles Breakpoint UI update on the frontend
   */
  @Deprecated
  public void updateUI() {
  }

  public @Nullable VirtualFile getFile() {
    return VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
  }

  @Override
  public @NotNull XLineBreakpointType<P> getType() {
    return myType;
  }

  @Override
  public @NotNull XLineBreakpointVerticalPlacement getPlacement() {
    return withStateLock(() -> myState.getPlacement());
  }

  @Override
  public int getLine() {
    return withStateLock(() -> myState.getLine());
  }

  @Override
  public String getFileUrl() {
    return withStateLock(() -> myState.getFileUrl());
  }

  @Override
  public String getPresentableFilePath() {
    String url = getFileUrl();
    if (LocalFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(url))) {
      String path = VfsUtilCore.urlToPath(url);

      // Try to get the path relative to the project directory to make the result easier to read.
      VirtualFile project = ProjectUtil.guessProjectDir(getProject());
      String relativePath = project != null
                            ? FileUtil.getRelativePath(project.getPath(), path, '/')
                            : null;

      String presentablePath = relativePath != null ? relativePath : path;
      return FileUtil.toSystemDependentName(presentablePath);
    }
    return url;
  }

  @Override
  public String getShortFilePath() {
    return new File(VfsUtilCore.urlToPath(getFileUrl())).getName();
  }

  /**
   * @deprecated This method always returns null, since the backend's breakpoint doesn't have a highlighter.
   */
  @Deprecated
  public @Nullable RangeHighlighter getHighlighter() {
    return null;
  }

  @Override
  public XSourcePosition getSourcePosition() {
    if (mySourcePosition != null) {
      return mySourcePosition;
    }
    mySourcePosition = super.getSourcePosition();
    if (mySourcePosition == null) {
      mySourcePosition = XDebuggerUtil.getInstance().createPosition(getFile(), getLine());
    }
    return mySourcePosition;
  }

  @SuppressWarnings("deprecation") // for API compatibility
  @Deprecated
  @Override
  public boolean isValid() {
    return super.isValid();
  }

  /**
   * @deprecated This method does nothing, since the backend's breakpoint doesn't have a range marker used in the update position.
   */
  @Deprecated
  public void updatePosition() {
  }

  void resetSourcePosition() {
    resetSourcePosition(-1);
  }

  public void resetSourcePosition(long requestId) {
    mySourcePosition = null;
    if (getBreakpointManager().getRequestCounter().setRequestCompleted(getBreakpointId(), requestId)) {
      fireBreakpointChanged();
    }
  }

  public void setFileUrl(final String newUrl) {
    setFileUrl(-1, newUrl);
  }

  public void setFileUrl(long requestId, String newUrl) {
    updateStateIfNeededAndNotify(requestId, newUrl, this::getFileUrl, (url) -> {
      myState.setFileUrl(url);
      resetSourcePosition();
    });
  }

  @ApiStatus.Internal
  public void setPlacement(@NotNull XLineBreakpointVerticalPlacement placement) {
    setPlacement(-1, placement);
  }

  public void setPlacement(long requestId, @NotNull XLineBreakpointVerticalPlacement placement) {
    updateStateIfNeededAndNotify(requestId, placement, this::getPlacement, (newPlacement) -> {
      myState.setPlacement(newPlacement);
    });
  }

  @ApiStatus.Internal
  public void setLine(final int line) {
    setLine(-1, line, true);
  }

  public void setLine(long requestId, int line) {
    setLine(requestId, line, true);
  }

  private void setLine(long requestId, final int line, boolean visualLineMightBeChanged) {

    updateStateIfNeededAndNotify(requestId, line, this::getLine, (l) -> {
      myState.setLine(line);
      resetSourcePosition();
    });
  }

  /**
   * @deprecated This method does nothing, since the backend's breakpoint doesn't have a UI.
   */
  @Deprecated
  public void doUpdateUI(Runnable callOnUpdate) {
  }

  public @NotNull GutterDraggableObject createBreakpointDraggableObject() {
    return myBreakpointDraggableObjectFactory.create();
  }

  @Override
  public boolean isTemporary() {
    return withStateLock(() -> myState.isTemporary());
  }

  @Override
  public void setTemporary(boolean temporary) {
    setTemporary(-1, temporary);
  }

  public void setTemporary(long requestId, boolean temporary) {
    updateStateIfNeededAndNotify(requestId, temporary, this::isTemporary, myState::setTemporary);
  }

  @Override
  public String toString() {
    return "XLineBreakpointImpl(id = " + getBreakpointId() + ", " + myType.getId() + " at " + getShortFilePath() + ":" + getLine() + ")";
  }
}
