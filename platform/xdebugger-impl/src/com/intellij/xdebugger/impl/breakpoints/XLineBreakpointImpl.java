// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.editor.RangeMarker;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.xdebugger.impl.breakpoints.XBreakpointProxyKt.asProxy;
import static com.intellij.xdebugger.impl.frame.XDebugSessionProxy.useFeLineBreakpointProxy;

@ApiStatus.Internal
public final class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, LineBreakpointState>
  implements XLineBreakpoint<P> {

  // TODO IJPL-185322 move to some external manager
  private final XBreakpointVisualRepresentation myVisualRepresentation;

  private final XLineBreakpointType<P> myType;
  private volatile XSourcePosition mySourcePosition;

  public XLineBreakpointImpl(final XLineBreakpointType<P> type,
                             XBreakpointManagerImpl breakpointManager,
                             final @Nullable P properties, LineBreakpointState state) {
    super(type, breakpointManager, properties, state);
    myType = type;
    myVisualRepresentation = new XBreakpointVisualRepresentation(getCoroutineScope(), asProxy(this), !useFeLineBreakpointProxy(),
                                                                 new XBreakpointManagerProxy.Monolith(breakpointManager));
  }

  // TODO IJPL-185322 migrate to backend -> frontend rpc flow notification
  public void updateUI() {
    myVisualRepresentation.updateUI();
  }

  public @Nullable VirtualFile getFile() {
    return VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
  }

  @Override
  public @NotNull XLineBreakpointType<P> getType() {
    return myType;
  }

  @Override
  public int getLine() {
    return myState.getLine();
  }

  @Override
  public String getFileUrl() {
    return myState.getFileUrl();
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

  public @Nullable RangeHighlighter getHighlighter() {
    return myVisualRepresentation.getHighlighter();
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

  @Override
  protected void doDispose() {
    myVisualRepresentation.removeHighlighter();
    myVisualRepresentation.redrawInlineInlays(getFile(), getLine());
  }

  public void updatePosition() {
    RangeMarker highlighter = myVisualRepresentation.getRangeMarker();
    if (highlighter != null && highlighter.isValid()) {
      resetSourcePosition(); // reset the source position even if the line number has not changed, as the offset may be cached inside
      setLine(-1, highlighter.getDocument().getLineNumber(highlighter.getStartOffset()), false);
    }
  }


  void resetSourcePosition() {
    resetSourcePosition(-1);
  }

  public void resetSourcePosition(long requestId) {
    mySourcePosition = null;
    if (getBreakpointManager().getRequestCounter().setRequestCompleted(requestId)) {
      fireBreakpointChanged();
    }
  }

  public void setFileUrl(final String newUrl) {
    setFileUrl(-1, newUrl);
  }

  public void setFileUrl(long requestId, String newUrl) {
    updateStateIfNeededAndNotify(requestId, newUrl, this::getFileUrl, (url) -> {
      var oldFile = getFile();
      myState.setFileUrl(url);
      resetSourcePosition();
      myVisualRepresentation.removeHighlighter();
      myVisualRepresentation.redrawInlineInlays(oldFile, getLine());
      myVisualRepresentation.redrawInlineInlays(getFile(), getLine());
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
    if (getLine() != line && visualLineMightBeChanged && !myType.lineShouldBeChanged(this, line, getProject())) {
      return;
    }

    updateStateIfNeededAndNotify(requestId, line, this::getLine, (l) -> {
      var oldLine = getLine();
      myState.setLine(line);
      resetSourcePosition();

      if (visualLineMightBeChanged) {
        myVisualRepresentation.removeHighlighter();
      }

      // We try to redraw inlays every time,
      // due to lack of synchronization between inlay redrawing and breakpoint changes.
      myVisualRepresentation.redrawInlineInlays(getFile(), oldLine);
      myVisualRepresentation.redrawInlineInlays(getFile(), line);
    });
  }

  public void doUpdateUI(Runnable callOnUpdate) {
    myVisualRepresentation.doUpdateUI(callOnUpdate);
  }

  public @NotNull GutterDraggableObject createBreakpointDraggableObject() {
    return myVisualRepresentation.createBreakpointDraggableObject();
  }

  @Override
  public boolean isTemporary() {
    return myState.isTemporary();
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
