/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.GutterDraggableObject;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.dnd.DragSource;
import java.util.List;

/**
 * @author nik
 */
public class XLineBreakpointImpl<P extends XBreakpointProperties> extends XBreakpointBase<XLineBreakpoint<P>, P, LineBreakpointState<P>> implements XLineBreakpoint<P> {
  @Nullable private RangeHighlighter myHighlighter;
  private final XLineBreakpointType<P> myType;
  private XSourcePosition mySourcePosition;
  private boolean myDisposed;

  public XLineBreakpointImpl(final XLineBreakpointType<P> type, XBreakpointManagerImpl breakpointManager, String url, int line, @Nullable final P properties) {
    super(type, breakpointManager, properties, new LineBreakpointState<P>(true, type.getId(), url, line));
    myType = type;
  }

  XLineBreakpointImpl(final XLineBreakpointType<P> type,
                      XBreakpointManagerImpl breakpointManager,
                      final LineBreakpointState<P> breakpointState) {
    super(type, breakpointManager, breakpointState);
    myType = type;
  }

  public void updateUI() {
    if (myDisposed) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    Document document = getDocument();
    if (document == null) return;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

    removeHighlighter();
    MarkupModelEx markupModel = (MarkupModelEx)document.getMarkupModel(getProject());
    RangeHighlighter highlighter = markupModel.addPersistentLineHighlighter(getLine(), DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER,
                                                                            attributes);
    if (highlighter != null) {
      updateIcon();
      highlighter.setGutterIconRenderer(createGutterIconRenderer());
    }
    myHighlighter = highlighter;
  }

  @Nullable
  public Document getDocument() {
    VirtualFile file = getFile();
    if (file == null) return null;
    return FileDocumentManager.getInstance().getDocument(file);
  }

  @Nullable
  private VirtualFile getFile() {
    return VirtualFileManager.getInstance().findFileByUrl(getFileUrl());
  }

  @NotNull
  public XLineBreakpointType<P> getType() {
    return myType;
  }

  public int getLine() {
    return myState.getLine();
  }

  public String getFileUrl() {
    return myState.getFileUrl();
  }

  public String getPresentableFilePath() {
    String url = getFileUrl();
    if (url != null && LocalFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(url))) {
      return FileUtil.toSystemDependentName(VfsUtil.urlToPath(url));
    }
    return url != null ? url : "";
  }

  @Nullable
  public RangeHighlighter getHighlighter() {
    return myHighlighter;
  }

  public XSourcePosition getSourcePosition() {
    if (mySourcePosition == null) {
      new ReadAction() {
        protected void run(final Result result) {
          mySourcePosition = XSourcePositionImpl.create(getFile(), getLine());
        }
      }.execute();
    }
    return mySourcePosition;
  }

  public boolean isValid() {
    return myHighlighter != null && myHighlighter.isValid();
  }

  public void dispose() {
    removeHighlighter();
    myDisposed = true;
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
      public boolean copy(int line, VirtualFile file) {
        if (canMoveTo(line, file)) {
          setFileUrl(file.getUrl());
          setLine(line);
          return true;
        }
        return false;
      }

      public Cursor getCursor(int line) {
        return canMoveTo(line, getFile()) ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop;
      }
    };
  }

  private boolean canMoveTo(int line, VirtualFile file) {
    return file != null && myType.canPutAt(file, line, getProject()) && getBreakpointManager().findBreakpointAtLine(myType, file, line) == null;
  }

  public void updatePosition() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      Document document = myHighlighter.getDocument();
      setLine(document.getLineNumber(myHighlighter.getStartOffset()));
    }
  }

  public void setFileUrl(final String newUrl) {
    if (!Comparing.equal(getFileUrl(), newUrl)) {
      myState.setFileUrl(newUrl);
      mySourcePosition = null;
      fireBreakpointChanged();
    }
  }

  private void setLine(final int line) {
    if (getLine() != line) {
      myState.setLine(line);
      mySourcePosition = null;
      fireBreakpointChanged();
    }
  }

  protected List<? extends AnAction> getAdditionalPopupMenuActions(final XDebugSession session) {
    return getType().getAdditionalPopupMenuActions(this, session);
  }
}
