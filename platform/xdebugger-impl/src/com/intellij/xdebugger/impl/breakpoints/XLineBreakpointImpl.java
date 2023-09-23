// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.codeInsight.hints.presentation.InputHandler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.BitUtil;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

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
      if (isDisposed()) return;

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
        if (isDisposed()) return;

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
            MarkupEditorFilter filter = highlighter.getEditorFilter();
            highlighter.setEditorFilter(MarkupEditorFilter.EMPTY);
            highlighter.setEditorFilter(filter); // to fireChanged
          }
        }

        // FIXME[inline-bp]: fix this, it's quadratic redraw of all inlays
        redrawInlineBreakpoints(getBreakpointManager().getLineBreakpointManager(), getProject(), file, finalDocument);

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

    // FIXME[inline-bp]: this part seems very dirty, we should modify only one inlay here or remove all of them in a line
    if (XLineBreakpointManager.shouldShowBreakpointsInline()) {
      var file = getFile();
      if (file != null) {
        var document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          if (myType instanceof XBreakpointTypeWithDocumentDelegation) {
            document = ((XBreakpointTypeWithDocumentDelegation)myType).getDocumentForHighlighting(document);
          }
          redrawInlineBreakpoints(getBreakpointManager().getLineBreakpointManager(), getProject(), file, document);
        }
      }
    }
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
        XDebuggerUtilImpl.removeBreakpointWithConfirmation(XLineBreakpointImpl.this);
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

  int getOffset() {
    return myHighlighter != null && myHighlighter.isValid() ? myHighlighter.getStartOffset() : -1;
  }

  public void updatePosition() {
    if (myHighlighter != null && myHighlighter.isValid()) {
      mySourcePosition = null; // reset the source position even if the line number has not changed, as the offset may be cached inside
      setLine(myHighlighter.getDocument().getLineNumber(getOffset()), false);
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

  // FIXME[inline-bp]: it's super inefficient
  static void redrawInlineBreakpoints(XLineBreakpointManager lineBreakpointManager, @NotNull Project project, @NotNull VirtualFile file, @NotNull Document document) {
    if (!XLineBreakpointManager.shouldShowBreakpointsInline()) return;

    Collection<XLineBreakpointImpl> allBreakpoints = lineBreakpointManager.getDocumentBreakpoints(document);
    Map<Integer, List<XLineBreakpointImpl>> breakpointsByLine = allBreakpoints.stream().collect(Collectors.groupingBy(b -> b.getLine()));

    // FIXME[inline-bp]: it's super inefficient
    // remove all previous inlays
    for (var editor : EditorFactory.getInstance().getEditors(document, project)) {
      var inlayModel = editor.getInlayModel();
      var inlays = inlayModel.getInlineElementsInRange(Integer.MIN_VALUE, Integer.MAX_VALUE, InlineBreakpointInlayRenderer.class);
      inlays.forEach(Disposable::dispose);
    }

    for (Map.Entry<Integer, List<XLineBreakpointImpl>> entry : breakpointsByLine.entrySet()) {
      var line = entry.getKey();
      List<XLineBreakpointImpl> breakpoints = entry.getValue();

      if (line < 0) continue;

      var linePosition = XSourcePositionImpl.create(file, line);
      var breakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null);
      XDebuggerUtilImpl.getLineBreakpointVariants(project, breakpointTypes, linePosition).onProcessed(variants -> {
        if (variants == null) {
          variants = Collections.emptyList();
        } else if (ContainerUtil.exists(variants, XLineBreakpointImpl::isAllVariant)) {
          // No need to show "all" variant in case of the inline breakpoints approach, it's useful only for the popup based one.
          variants = variants.stream().filter(v -> !isAllVariant(v)).toList();
        }

        var codeStartOffset = DocumentUtil.getLineStartIndentedOffset(document, line);

        if (breakpoints.size() == 1 && variants.size() == 1 &&
            areMatching(variants.get(0), breakpoints.get(0), codeStartOffset)) {
          // No need to show inline variants when there is only one breakpoint and one matching variant.
          return;
        }

        for (var variant : variants) {
          var breakpointsHere = ContainerUtil.findAll(breakpoints, b -> areMatching(variant, b, codeStartOffset));
          if (!breakpointsHere.isEmpty()) {
            for (XLineBreakpointImpl breakpointHere : breakpointsHere) {
              breakpoints.remove(breakpointHere);
              EditorFactory.getInstance().editors(document, project).forEach(editor -> {
                addInlineBreakpointInlay(editor, breakpointHere, variant, codeStartOffset);
              });
            }
          } else {
            EditorFactory.getInstance().editors(document, project).forEach(editor -> {
              addInlineBreakpointInlay(editor, variant, codeStartOffset);
            });
          }
        }

        for (XLineBreakpointImpl remainingBreakpoint : breakpoints) {
          EditorFactory.getInstance().editors(document, project).forEach(editor -> {
            addInlineBreakpointInlay(editor, remainingBreakpoint, null, codeStartOffset);
          });
        }
      });
    }
  }

  private static boolean isAllVariant(XLineBreakpointType<?>.XLineBreakpointVariant variant) {
    // Currently, it's the easiest way to check that it's really multi-location variant.
    // Don't try to check whether the variant is an instance of XLineBreakpointAllVariant, they all are.
    // FIXME[inline-bp]: introduce better way for this or completely get rid of multi-location variants
    return variant.getIcon() == AllIcons.Debugger.MultipleBreakpoints;
  }

  private static void addInlineBreakpointInlay(Editor editor, @NotNull XLineBreakpointImpl<?> breakpoint, @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant, int codeStartOffset) {
    var offset = getBreakpointRangeStartOffset(breakpoint, codeStartOffset);
    addInlineBreakpointInlayImpl(editor, offset, breakpoint, variant);
  }

  private static void addInlineBreakpointInlay(Editor editor, @NotNull XLineBreakpointType<?>.XLineBreakpointVariant variant, int codeStartOffset) {
    var offset = getBreakpointVariantRangeStartOffset(variant, codeStartOffset);
    addInlineBreakpointInlayImpl(editor, offset, null, variant);
  }

  private static void addInlineBreakpointInlayImpl(Editor editor, int offset, @Nullable XLineBreakpointImpl<?> breakpoint, @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant) {
    var inlayModel = editor.getInlayModel();
    var renderer = new InlineBreakpointInlayRenderer(breakpoint, variant);
    var inlay = inlayModel.addInlineElement(offset, renderer);
    renderer.setInlay(inlay);
  }

  private static boolean areMatching(XLineBreakpointType<?>.XLineBreakpointVariant variant, XLineBreakpointImpl<?> breakpoint, int codeStartOffset) {
    return variant.getType().equals(breakpoint.getType()) &&
           getBreakpointVariantRangeStartOffset(variant, codeStartOffset) == getBreakpointRangeStartOffset(breakpoint, codeStartOffset);
  }

  private static int getBreakpointVariantRangeStartOffset(XLineBreakpointType<?>.XLineBreakpointVariant variant, int codeStartOffset) {
    var variantRange = variant.getHighlightRange();
    return getLineRangeStartNormalized(variantRange, codeStartOffset);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static int getBreakpointRangeStartOffset(XLineBreakpointImpl<?> breakpoint, int codeStartOffset) {
    var breakpointRange = breakpoint.getType().getHighlightRange((XLineBreakpointImpl)breakpoint);
    return getLineRangeStartNormalized(breakpointRange, codeStartOffset);
  }

  private static int getLineRangeStartNormalized(TextRange range, int codeStartOffset) {
    // Null range represents the whole line.
    // Any start offset from the line start until first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character for ease of comparison of various ranges coming from variants and breakpoints.
    return range != null ? Math.max(range.getStartOffset(), codeStartOffset) : codeStartOffset;
  }

  // FIXME[inline-bp]: extract me somewhere
  static class InlineBreakpointInlayRenderer implements EditorCustomElementRenderer, InputHandler {
    // There could be three states:
    // * not-null breakpoint and not-null variant -- we have a breakpoint and a matching variant (normal case)
    // * null breakpoint and not-null variant -- we have a variant where breakpoint could be set (normal case)
    // * not-null breakpoint and null variant -- we have a breakpoint but no matching variant (outdated breakpoint?)
    private final @Nullable XLineBreakpointImpl<?> breakpoint;
    private final @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant;

    // EditorCustomElementRenderer's methods have inlay as parameter,
    // but InputHandler's methods do not have it.
    private Inlay<InlineBreakpointInlayRenderer> inlay;

    boolean hovered;

    InlineBreakpointInlayRenderer(@Nullable XLineBreakpointImpl<?> breakpoint, @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant) {
      if (breakpoint == null && variant == null) {
        throw new IllegalArgumentException();
      }
      this.breakpoint = breakpoint;
      this.variant = variant;
    }

    private void setInlay(Inlay<InlineBreakpointInlayRenderer> inlay) {
      this.inlay = inlay;
    }

    private float scale() {
      return 0.75f // FIXME[inline-bp]: introduce option to make inline icons slightly smaller than gutter ones
             * editorScale();
    }

    private float editorScale() {
      var editor = inlay.getEditor();
      return (inlay.getEditor() instanceof EditorImpl) ? ((EditorImpl)editor).getScale() : 1;
    }

    @NotNull
    private static FontMetrics getEditorFontMetrics(@NotNull Editor editor) {
      var colorsScheme = editor.getColorsScheme();
      var font = UIUtil.getFontWithFallback(colorsScheme.getFont(EditorFontType.PLAIN));
      var context = FontInfo.getFontRenderContext(editor.getContentComponent());
      return FontInfo.getFontMetrics(font, context);
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      var fontMetrics = getEditorFontMetrics(inlay.getEditor());
      var twoChars = "nn"; // Use two average width characters (might be important for non-monospaced fonts).
      return fontMetrics.stringWidth(twoChars);
    }

    @Override
    public void paint(@NotNull Inlay inlay,
                      @NotNull Graphics g,
                      @NotNull Rectangle targetRegion,
                      @NotNull TextAttributes textAttributes) {
      JComponent component = inlay.getEditor().getComponent();

      Icon baseIcon;
      float alpha;
      if (breakpoint != null) {
        baseIcon = breakpoint.getIcon();
        alpha = 1;
      }
      else {
        assert variant != null;
        baseIcon = variant.getType().getEnabledIcon();

        // FIXME[inline-bp]: do we need to rename the property?
        alpha = JBUI.getFloat("Breakpoint.iconHoverAlpha", 0.5f);
        alpha = Math.max(0, Math.min(alpha, 1));
        if (hovered) {
          // Slightly increase visibility (e.g. 0.5 -> 0.625).
          // FIXME[inline-bp]: ask Yulia Zozulya if we really need it?
          alpha = (3 * alpha + 1) / 4;
        }
      }

      // FIXME[inline-bp]: limit icon size to region size with some padding
      Icon scaledIcon = IconUtil.scale(baseIcon, component, scale());

      // Draw icon in the center of the region.
      var x = targetRegion.x + targetRegion.width / 2 - scaledIcon.getIconWidth() / 2;
      var y = targetRegion.y + targetRegion.height / 2 - scaledIcon.getIconHeight() / 2;
      GraphicsUtil.paintWithAlpha(g, alpha, () -> scaledIcon.paintIcon(component, g, x, y));
    }

    @Override
    public void mousePressed(@NotNull MouseEvent event, @NotNull Point translated) {
      invokePopupIfNeeded(event);
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent event, @NotNull Point translated) {
      invokePopupIfNeeded(event);
    }

    private void invokePopupIfNeeded(@NotNull MouseEvent event) {
      if (event.isPopupTrigger()) {
        if (breakpoint != null) {
          var bounds = inlay.getBounds();
          if (bounds == null) return;
          Point center = new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
          DebuggerUIUtil.showXBreakpointEditorBalloon(breakpoint.getProject(), center, inlay.getEditor().getContentComponent(), false,
                                                      breakpoint);
        } else {
          // FIXME[inline-bp]: show context like in gutter (XDebugger.Hover.Breakpoint.Context.Menu),
          //                   but actions should be adapted for inline breakpoints.
        }
        event.consume();
      }
    }

    private enum ClickAction {
      SET, ENABLE_DISABLE, REMOVE
    }

    @Override
    public void mouseClicked(@NotNull MouseEvent event, @NotNull Point translated) {
      event.consume();

      var button = event.getButton();
      ClickAction action = null;
      if (breakpoint != null) {
        // mimic gutter icon
        if (button == MouseEvent.BUTTON2 ||
            (button == MouseEvent.BUTTON1 && BitUtil.isSet(event.getModifiersEx(), InputEvent.ALT_DOWN_MASK))) {
          action = !Registry.is("debugger.click.disable.breakpoints")
                   ? ClickAction.ENABLE_DISABLE
                   : ClickAction.REMOVE;
        }
        else if (button == MouseEvent.BUTTON1) {
          action = Registry.is("debugger.click.disable.breakpoints")
                   ? ClickAction.ENABLE_DISABLE
                   : ClickAction.REMOVE;
        }
      }
      else {
        assert variant != null;
        if (button == MouseEvent.BUTTON1) {
          action = ClickAction.SET;
        }
      }

      if (action == null) {
        return;
      }
      // FIXME[inline-bp]: what about removal by drag and drop?

      var editor = inlay.getEditor();
      var project = editor.getProject();
      assert project != null; // FIXME[inline-bp]: replace by if?
      var file = editor.getVirtualFile();
      assert file != null; // FIXME[inline-bp]: replace by if?
      var offset = inlay.getOffset();

      switch (action) {
        case SET -> {
          WriteAction.run(() -> {
            // FIXME[inline-bp]: is it ok to keep variant so long or should we obtain fresh variants and find similar one?
            var breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
            var line = editor.getDocument().getLineNumber(offset);
            //noinspection unchecked
            breakpointManager.addLineBreakpoint((XLineBreakpointType)variant.getType(), file.getUrl(), line, variant.createProperties(),
                                                false);
          });
        }
        case ENABLE_DISABLE -> {
          breakpoint.setEnabled(!breakpoint.isEnabled());
        }
        case REMOVE -> {
          if (XDebuggerUtilImpl.removeBreakpointWithConfirmation(breakpoint)) {
            // FIXME[inline-bp]: it's a dirty hack to render inlay as "hovered" just after we clicked on set breakpoint
            //       The problem is that after breakpoint removal we currently recreate all inlays and new ones would not be "hovered".
            //       So we manually propogate this property to future inlay at the same position.
            //       Otherwise there will be flickering:
            //       transparent -> (move mouse) -> hovered -> (click) -> set -> (click) -> transparent -> (move mouse 1px) -> hovered
            //                                                                              ^^^^^^^^^^^ this is bad
            //       One day we would keep old inlays and this hack would gone.
            for (var newInlay : editor.getInlayModel().getInlineElementsInRange(offset, offset, InlineBreakpointInlayRenderer.class)) {
              newInlay.getRenderer().hovered = true;
            }
          }
        }
      }
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent event, @NotNull Point translated) {
      event.consume();
      setHovered(true);
    }

    @Override
    public void mouseExited() {
      setHovered(false);
    }

    private void setHovered(boolean hovered) {
      var wasHovered = this.hovered;
      this.hovered = hovered;
      var cursor = hovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null;
      ((EditorEx)inlay.getEditor()).setCustomCursor(InlineBreakpointInlayRenderer.class, cursor);
      if (wasHovered != hovered) {
        inlay.repaint();
      }
    }

  }
}
