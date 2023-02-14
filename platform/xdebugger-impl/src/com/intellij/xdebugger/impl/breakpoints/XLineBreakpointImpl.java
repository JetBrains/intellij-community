// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.DocumentUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.io.File;
import java.util.Objects;

public final class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, LineBreakpointState<P>>
  implements XLineBreakpoint<P> {
  private static final Logger LOG = Logger.getInstance(XLineBreakpointImpl.class);

  @Nullable private RangeMarker myHighlighter;
  private final XLineBreakpointType<P> myType;
  private XSourcePosition mySourcePosition;

  public XLineBreakpointImpl(final XLineBreakpointType<P> type,
                             XBreakpointManagerImpl breakpointManager,
                             @Nullable final P properties, LineBreakpointState<P> state) {
    super(type, breakpointManager, properties, state);
    myType = type;
  }

  XLineBreakpointImpl(final XLineBreakpointType<P> type,
                      XBreakpointManagerImpl breakpointManager,
                      final LineBreakpointState<P> breakpointState) {
    super(type, breakpointManager, breakpointState);
    myType = type;
  }

  public void updateUI() {
    getBreakpointManager().getLineBreakpointManager().queueBreakpointUpdate(this);
  }

  @RequiresBackgroundThread
  void doUpdateUI(@NotNull Runnable callOnUpdate) {
    if (isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    VirtualFile file = getFile();
    if (file == null) {
      return;
    }

    ReadAction.nonBlocking(() -> {
      // try not to decompile files
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document == null) {
        // currently LazyRangeMarkerFactory creates document for non binary files
        if (file.getFileType().isBinary()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (myHighlighter == null) {
              myHighlighter = LazyRangeMarkerFactory.getInstance(getProject()).createRangeMarker(file, getLine(), 0, true);
              callOnUpdate.run();
            }
          });
          return;
        }
        document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
          return;
        }
      }

      if (myType instanceof XBreakpointTypeWithDocumentDelegation) {
        document = ((XBreakpointTypeWithDocumentDelegation)myType).getDocumentForHighlighting(document);
      }

      TextRange range = myType.getHighlightRange(this);

      Document finalDocument = document;
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myHighlighter != null && !(myHighlighter instanceof RangeHighlighter)) {
          removeHighlighter();
          myHighlighter = null;
        }

        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

        if (!isEnabled()) {
          attributes = attributes.clone();
          attributes.setBackgroundColor(null);
        }

        RangeHighlighter highlighter = (RangeHighlighter)myHighlighter;
        if (highlighter != null &&
            (!highlighter.isValid()
             || !DocumentUtil.isValidOffset(highlighter.getStartOffset(), finalDocument)
             || !Comparing.equal(highlighter.getTextAttributes(null), attributes)
             // it seems that this check is not needed - we always update line number from the highlighter
             // and highlighter is removed on line and file change anyway
              /*|| document.getLineNumber(highlighter.getStartOffset()) != getLine()*/)) {
          removeHighlighter();
          highlighter = null;
        }

        MarkupModelEx markupModel;
        if (highlighter == null) {
          int line = getLine();
          if (line >= finalDocument.getLineCount()) {
            callOnUpdate.run();
            return;
          }
          markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(finalDocument, getProject(), true);
          if (range != null && !range.isEmpty()) {
            TextRange lineRange = DocumentUtil.getLineTextRange(finalDocument, line);
            if (range.intersects(lineRange)) {
              highlighter = markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                            DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes,
                                                            HighlighterTargetArea.EXACT_RANGE);
            }
          }
          if (highlighter == null) {
            highlighter = markupModel.addPersistentLineHighlighter(line, DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes);
          }
          if (highlighter == null) {
            callOnUpdate.run();
            return;
          }

          highlighter.setGutterIconRenderer(createGutterIconRenderer());
          highlighter.putUserData(DebuggerColors.BREAKPOINT_HIGHLIGHTER_KEY, Boolean.TRUE);
          highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());
          myHighlighter = highlighter;
        }
        else {
          markupModel = null;
        }

        updateIcon();

        if (markupModel == null) {
          markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(finalDocument, getProject(), false);
          if (markupModel != null) {
            // renderersChanged false - we don't change gutter size
            markupModel.fireAttributesChanged((RangeHighlighterEx)highlighter, false, false);
          }
        }

        callOnUpdate.run();
      });
    }).executeSynchronously();
  }

  @Nullable
  public VirtualFile getFile() {
    return VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
  }

  @Override
  @NotNull
  public XLineBreakpointType<P> getType() {
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
    if (url != null && LocalFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(url))) {
      return FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(url));
    }
    return url != null ? url : "";
  }

  @Override
  public String getShortFilePath() {
    final String path = getPresentableFilePath();
    if (path.isEmpty()) return "";
    return new File(path).getName();
  }

  @Nullable
  public RangeHighlighter getHighlighter() {
    return myHighlighter instanceof RangeHighlighter ? (RangeHighlighter)myHighlighter : null;
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

  @Override
  public boolean isValid() {
    return myHighlighter != null && myHighlighter.isValid();
  }

  @Override
  protected void doDispose() {
    removeHighlighter();
  }

  private void removeHighlighter() {
    if (myHighlighter != null) {
      try {
        myHighlighter.dispose();
      }
      catch (Exception e) {
        LOG.error(e);
      }
      myHighlighter = null;
    }
  }

  @Override
  protected GutterDraggableObject createBreakpointDraggableObject() {
    return new GutterDraggableObject() {
      @Override
      public boolean copy(int line, VirtualFile file, int actionId) {
        if (canMoveTo(line, file)) {
          XDebuggerManagerImpl debuggerManager = (XDebuggerManagerImpl)XDebuggerManager.getInstance(getProject());
          XBreakpointManagerImpl breakpointManager = debuggerManager.getBreakpointManager();
          if (isCopyAction(actionId)) {
            WriteAction.run(() -> breakpointManager.copyLineBreakpoint(XLineBreakpointImpl.this, file.getUrl(), line));
          }
          else {
            setFileUrl(file.getUrl());
            setLine(line, true);
            XDebugSessionImpl session = debuggerManager.getCurrentSession();
            if (session != null) {
              session.checkActiveNonLineBreakpointOnRemoval(XLineBreakpointImpl.this);
            }
          }
          return true;
        }
        return false;
      }

      @Override
      public void remove() {
        XDebuggerUtilImpl.removeBreakpointWithConfirmation(getProject(), XLineBreakpointImpl.this);
      }

      @Override
      public Cursor getCursor(int line, VirtualFile file, int actionId) {
        if (canMoveTo(line, file)) {
          return isCopyAction(actionId) ? DragSource.DefaultCopyDrop : DragSource.DefaultMoveDrop;
        }

        return DragSource.DefaultMoveNoDrop;
      }

      private boolean isCopyAction(int actionId) {
        return (actionId & DnDConstants.ACTION_COPY) == DnDConstants.ACTION_COPY;
      }
    };
  }

  private boolean canMoveTo(int line, VirtualFile file) {
    if (file != null && myType.canPutAt(file, line, getProject())) {
      XLineBreakpoint<P> existing = getBreakpointManager().findBreakpointAtLine(myType, file, line);
      return existing == null || existing == this;
    }
    return false;
  }

  public void updatePosition() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      setLine(myHighlighter.getDocument().getLineNumber(myHighlighter.getStartOffset()), false);
      mySourcePosition = null; // need to clear this no matter what as the offset may be cached inside
    }
  }

  public void setFileUrl(final String newUrl) {
    if (!Objects.equals(getFileUrl(), newUrl)) {
      myState.setFileUrl(newUrl);
      mySourcePosition = null;
      removeHighlighter();
      fireBreakpointChanged();
    }
  }

  @ApiStatus.Internal
  public void setLine(final int line) {
    setLine(line, true);
  }

  private void setLine(final int line, boolean removeHighlighter) {
    if (getLine() != line) {
      myState.setLine(line);
      mySourcePosition = null;
      if (removeHighlighter) {
        removeHighlighter();
      }
      fireBreakpointChanged();
    }
  }

  @Override
  public boolean isTemporary() {
    return myState.isTemporary();
  }

  @Override
  public void setTemporary(boolean temporary) {
    if (isTemporary() != temporary) {
      myState.setTemporary(temporary);
      fireBreakpointChanged();
    }
  }

  @Override
  protected void updateIcon() {
    Icon icon = calculateSpecialIcon();
    if (icon == null) {
      icon = isTemporary() ? myType.getTemporaryIcon() : myType.getEnabledIcon();
    }
    setIcon(icon);
  }

  @Override
  public String toString() {
    return "XLineBreakpointImpl(" + myType.getId() + " at " + getShortFilePath() + ":" + getLine() + ")";
  }
}
