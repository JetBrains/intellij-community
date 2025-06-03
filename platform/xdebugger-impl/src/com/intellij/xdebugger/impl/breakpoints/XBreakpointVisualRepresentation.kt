// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LazyRangeMarkerFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.util.concurrent.ExecutorService;

@ApiStatus.Internal
public class XBreakpointVisualRepresentation {
  private static final Logger LOG = Logger.getInstance(XBreakpointVisualRepresentation.class);
  private final XLightLineBreakpointProxy myBreakpoint;
  static final ExecutorService redrawInlaysExecutor =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("XLineBreakpointImpl Inlay Redraw", 1);
  private final Project myProject;
  private final XBreakpointManagerProxy myBreakpointManager;
  private final boolean myIsEnabled;

  private @Nullable RangeMarker myRangeMarker;

  @ApiStatus.Internal
  public XBreakpointVisualRepresentation(
    XLightLineBreakpointProxy xBreakpoint,
    boolean isEnabled,
    XBreakpointManagerProxy breakpointManagerProxy
  ) {
    myBreakpoint = xBreakpoint;
    myProject = xBreakpoint.getProject();
    myBreakpointManager = breakpointManagerProxy;
    myIsEnabled = isEnabled;
  }

  public @Nullable RangeMarker getRangeMarker() {
    return myRangeMarker;
  }

  public @Nullable RangeHighlighter getHighlighter() {
    return myRangeMarker instanceof RangeHighlighter ? (RangeHighlighter)myRangeMarker : null;
  }

  @ApiStatus.Internal
  public void updateUI() {
    myBreakpointManager.getLineBreakpointManager().queueBreakpointUpdateCallback(myBreakpoint, () -> {
      doUpdateUI(() -> {
      });
    });
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  public void doUpdateUI(@NotNull Runnable callOnUpdate) {
    if (myBreakpoint.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    if (!myIsEnabled) {
      return;
    }

    VirtualFile file = myBreakpoint.getFile();
    if (file == null) {
      return;
    }

    ReadAction.nonBlocking(() -> {
      if (myBreakpoint.isDisposed()) return;

      // try not to decompile files
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document == null) {
        // currently LazyRangeMarkerFactory creates document for non binary files
        if (file.getFileType().isBinary()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (myRangeMarker == null) {
              myRangeMarker = LazyRangeMarkerFactory.getInstance(myProject).createRangeMarker(file, myBreakpoint.getLine(), 0, true);
              callOnUpdate.run();
            }
          }, myProject.getDisposed());
          return;
        }
        document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) {
          return;
        }
      }

      // TODO IJPL-185322 support XBreakpointTypeWithDocumentDelegation
      if (myBreakpoint.getType() instanceof XBreakpointTypeWithDocumentDelegation) {
        document = ((XBreakpointTypeWithDocumentDelegation)myBreakpoint.getType()).getDocumentForHighlighting(document);
      }

      TextRange range = myBreakpoint.getHighlightRange();

      Document finalDocument = document;
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myBreakpoint.isDisposed()) return;

        if (myRangeMarker != null && !(myRangeMarker instanceof RangeHighlighter)) {
          removeHighlighter();
          assert getHighlighter() == null;
        }

        TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES);

        if (!myBreakpoint.isEnabled()) {
          attributes = attributes.clone();
          attributes.setBackgroundColor(null);
        }

        RangeHighlighter highlighter = getHighlighter();
        if (highlighter != null &&
            (!highlighter.isValid()
             || range != null && !highlighter.getTextRange().equals(range)
             //breakpoint range marker is out-of-sync with actual breakpoint text range
             || !DocumentUtil.isValidOffset(highlighter.getStartOffset(), finalDocument)
             || !Comparing.equal(highlighter.getTextAttributes(null), attributes)
             // it seems that this check is not needed - we always update line number from the highlighter
             // and highlighter is removed on line and file change anyway
              /*|| document.getLineNumber(highlighter.getStartOffset()) != getLine()*/)) {
          removeHighlighter();
          redrawInlineInlays();
          highlighter = null;
        }

        myBreakpoint.updateIcon();

        if (highlighter == null) {
          int line = myBreakpoint.getLine();
          if (line >= finalDocument.getLineCount()) {
            callOnUpdate.run();
            return;
          }
          MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(finalDocument, myProject, true);
          if (range != null && !range.isEmpty()) {
            TextRange lineRange = DocumentUtil.getLineTextRange(finalDocument, line);
            if (range.intersectsStrict(lineRange)) {
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

          highlighter.setGutterIconRenderer(myBreakpoint.createGutterIconRenderer());
          highlighter.putUserData(DebuggerColors.BREAKPOINT_HIGHLIGHTER_KEY, Boolean.TRUE);
          highlighter.setEditorFilter(XBreakpointVisualRepresentation::isHighlighterAvailableIn);
          myRangeMarker = highlighter;

          redrawInlineInlays();
        }
        else {
          MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(finalDocument, myProject, false);
          if (markupModel != null) {
            // renderersChanged false - we don't change gutter size
            MarkupEditorFilter filter = highlighter.getEditorFilter();
            highlighter.setEditorFilter(MarkupEditorFilter.EMPTY);
            highlighter.setEditorFilter(filter); // to fireChanged
          }
        }

        callOnUpdate.run();
      }, myProject.getDisposed());
    }).executeSynchronously();
  }

  public void removeHighlighter() {
    if (getHighlighter() != null) {
      try {
        getHighlighter().dispose();
      }
      catch (Exception e) {
        LOG.error(e);
      }
      myRangeMarker = null;
    }
  }

  private void redrawInlineInlays() {
    redrawInlineInlays(myBreakpoint.getFile(), myBreakpoint.getLine());
  }

  public void redrawInlineInlays(@Nullable VirtualFile file, int line) {
    if (file == null) return;
    if (!XDebuggerUtil.areInlineBreakpointsEnabled(file)) return;

    ReadAction.nonBlocking(() -> {
        var document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return null;

        if (myBreakpoint.getType() instanceof XBreakpointTypeWithDocumentDelegation) {
          document = ((XBreakpointTypeWithDocumentDelegation)myBreakpoint.getType()).getDocumentForHighlighting(document);
        }

        return document;
      })
      .expireWith(myProject)
      .submit(redrawInlaysExecutor)
      .onSuccess(document -> {
        if (document == null) return;
        InlineBreakpointInlayManager.getInstance(myProject).redrawLine(document, line);
      });
  }

  @ApiStatus.Internal
  public @NotNull GutterDraggableObject createBreakpointDraggableObject() {
    return new GutterDraggableObject() {
      @Override
      public boolean copy(int line, VirtualFile file, int actionId) {
        if (canMoveTo(line, file)) {
          XDebuggerManagerImpl debuggerManager = (XDebuggerManagerImpl)XDebuggerManager.getInstance(myProject);
          XBreakpointManagerImpl breakpointManager = debuggerManager.getBreakpointManager();
          // TODO IJPL-185322 implement DnD for light breakpoints?
          if (!(myBreakpoint instanceof XLineBreakpointProxy breakpoint)) {
            return false;
          }
          if (isCopyAction(actionId) && breakpoint instanceof XLineBreakpointProxy.Monolith monolithBreakpointProxy) {
            // TODO IJPL-185322 support copy through gutter DnD
            breakpointManager.copyLineBreakpoint(monolithBreakpointProxy.getBreakpoint(), file.getUrl(), line);
          }
          else {
            breakpoint.setFileUrl(file.getUrl());
            breakpoint.setLine(line);
            XDebugSessionImpl session = debuggerManager.getCurrentSession();
            if (session != null && breakpoint instanceof XLineBreakpointProxy.Monolith monolithBreakpointProxy) {
              // TODO IJPL-185322 support active breakpoint update on DnD
              session.checkActiveNonLineBreakpointOnRemoval(monolithBreakpointProxy.getBreakpoint());
            }
            return true;
          }
        }
        return false;
      }

      @Override
      public void remove() {
        // TODO IJPL-185322 implement DnD remove for light breakpoints?
        if (myBreakpoint instanceof XLineBreakpointProxy proxy) {
          XDebuggerUtilImpl.removeBreakpointWithConfirmation(proxy);
        }
      }

      @Override
      public Cursor getCursor(int line, VirtualFile file, int actionId) {
        if (canMoveTo(line, file)) {
          return isCopyAction(actionId) ? DragSource.DefaultCopyDrop : DragSource.DefaultMoveDrop;
        }

        return DragSource.DefaultMoveNoDrop;
      }

      private static boolean isCopyAction(int actionId) {
        return (actionId & DnDConstants.ACTION_COPY) == DnDConstants.ACTION_COPY;
      }
    };
  }

  private boolean canMoveTo(int line, VirtualFile file) {
    if (file != null && myBreakpoint.getType().canPutAtFast(file, line, myProject) == ThreeState.YES) {
      if (myBreakpoint instanceof XLineBreakpointProxy.Monolith monolithBreakpointProxy) {
        XLineBreakpointImpl<?> monolithBreakpoint = monolithBreakpointProxy.getBreakpoint();
        XLineBreakpoint<?> existing =
          monolithBreakpoint.getBreakpointManager().findBreakpointAtLine(monolithBreakpoint.getType(), file, line);
        return existing == null || existing == monolithBreakpoint;
      }
      else {
        // TODO IJPL-185322 support findBreakpointAtLine check for split
        return true;
      }
    }
    return false;
  }

  private static boolean isHighlighterAvailableIn(Editor editor) {
    if (editor instanceof EditorImpl editorImpl && editorImpl.isStickyLinePainting()) {
      // suppress breakpoints on sticky lines panel
      return false;
    }
    return !DiffUtil.isDiffEditor(editor);
  }
}
