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
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
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
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.io.File;
import java.util.List;

/**
 * @author nik
 */
public class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, LineBreakpointState<P>>
  implements XLineBreakpoint<P> {
  @Nullable private RangeHighlighter myHighlighter;
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
    if (isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    Document document = getDocument();
    if (document == null) {
      return;
    }

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    RangeHighlighter highlighter = myHighlighter;
    if (highlighter != null &&
        (!highlighter.isValid()
         || !DocumentUtil.isValidOffset(highlighter.getStartOffset(), document)
         || !Comparing.equal(highlighter.getTextAttributes(), attributes)
         // it seems that this check is not needed - we always update line number from the highlighter
         // and highlighter is removed on line and file change anyway
         /*|| document.getLineNumber(highlighter.getStartOffset()) != getLine()*/)) {
      removeHighlighter();
      highlighter = null;
    }

    MarkupModelEx markupModel;
    if (highlighter == null) {
      markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
      TextRange range = myType.getHighlightRange(this);
      if (range != null && !range.isEmpty()) {
        range = range.intersection(DocumentUtil.getLineTextRange(document, getLine()));
        if (range != null && !range.isEmpty()) {
          highlighter = markupModel.addRangeHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                        DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes,
                                                        HighlighterTargetArea.EXACT_RANGE);
        }
      }
      if (highlighter == null) {
        highlighter = markupModel.addPersistentLineHighlighter(getLine(), DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes);
      }
      if (highlighter == null) {
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
      markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), false);
      if (markupModel != null) {
        // renderersChanged false - we don't change gutter size
        markupModel.fireAttributesChanged((RangeHighlighterEx)highlighter, false, false);
      }
    }
  }

  @Nullable
  public Document getDocument() {
    VirtualFile file = getFile();
    if (file == null) return null;
    return FileDocumentManager.getInstance().getDocument(file);
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
    return myHighlighter;
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
      myHighlighter.dispose();
      myHighlighter = null;
    }
  }

  @Override
  protected GutterDraggableObject createBreakpointDraggableObject() {
    return new GutterDraggableObject() {
      @Override
      public boolean copy(int line, VirtualFile file, int actionId) {
        if (canMoveTo(line, file)) {
          final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(getProject()).getBreakpointManager();
          if (isCopyAction(actionId)) {
            WriteAction
              .run(() -> ((XBreakpointManagerImpl)breakpointManager).copyLineBreakpoint(XLineBreakpointImpl.this, file.getUrl(), line));
          }
          else {
            setFileUrl(file.getUrl());
            setLine(line, true);
          }
          return true;
        }
        return false;
      }

      public void remove() {
        XBreakpointManager breakpointManager = XDebuggerManager.getInstance(getProject()).getBreakpointManager();
        WriteAction.run(() -> breakpointManager.removeBreakpoint(XLineBreakpointImpl.this));
      }

      @Override
      public Cursor getCursor(int line, int actionId) {
        if (canMoveTo(line, getFile())) {
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
    }
  }

  public void setFileUrl(final String newUrl) {
    if (!Comparing.equal(getFileUrl(), newUrl)) {
      myState.setFileUrl(newUrl);
      mySourcePosition = null;
      removeHighlighter();
      fireBreakpointChanged();
    }
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
  protected List<? extends AnAction> getAdditionalPopupMenuActions(final XDebugSession session) {
    return getType().getAdditionalPopupMenuActions(this, session);
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
