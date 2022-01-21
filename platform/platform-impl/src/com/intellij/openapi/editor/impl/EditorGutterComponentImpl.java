// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.NonHideableIconGutterMark;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDImage;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId3;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.impl.view.VisualLinesIterator;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.hover.HoverStateListener;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.paint.LinePainter2D.StrokeType;
import com.intellij.ui.paint.PaintUtil;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.BitUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.SmartList;
import com.intellij.util.animation.AlphaAnimationContext;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.JBValue.JBValueGroup;
import com.intellij.util.ui.accessibility.ScreenReader;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectIterable;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gutter content (left to right):
 * <ul>
 *   <li>GAP_BETWEEN_AREAS</li>
 *   <li>Line numbers area
 *     <ul>
 *       <li>Line numbers</li>
 *       <li>GAP_BETWEEN_AREAS</li>
 *       <li>Additional line numbers (used in diff)</li>
 *     </ul>
 *   </li>
 *   <li>GAP_BETWEEN_AREAS</li>
 *   <li>Annotations area
 *     <ul>
 *       <li>Annotations</li>
 *       <li>Annotations extra (used in distraction free mode)</li>
 *     </ul>
 *   </li>
 *   <li>GAP_BETWEEN_AREAS</li>
 *   <li>Line markers area
 *     <ul>
 *       <li>Left free painters</li>
 *       <li>Icons</li>
 *       <li>Gap (required by debugger to set breakpoints with mouse click - IDEA-137353) </li>
 *       <li>Free painters</li>
 *     </ul>
 *   </li>
 *   <li>Folding area</li>
 *</ul>
 */
@DirtyUI
final class EditorGutterComponentImpl extends EditorGutterComponentEx implements MouseListener, MouseMotionListener, DataProvider, Accessible {
  public static final String DISTRACTION_FREE_MARGIN = "editor.distraction.free.margin";
  private static final Logger LOG = Logger.getInstance(EditorGutterComponentImpl.class);

  private static final JBValueGroup JBVG = new JBValueGroup();
  private static final JBValue START_ICON_AREA_WIDTH = JBVG.value(17);
  private static final JBValue FREE_PAINTERS_LEFT_AREA_WIDTH = JBVG.value(8);
  private static final JBValue FREE_PAINTERS_RIGHT_AREA_WIDTH = JBVG.value(5);
  private static final JBValue GAP_BETWEEN_ICONS = JBVG.value(3);
  private static final JBValue GAP_BETWEEN_AREAS = JBVG.value(5);
  private static final JBValue GAP_BETWEEN_ANNOTATIONS = JBVG.value(5);
  private static final TooltipGroup GUTTER_TOOLTIP_GROUP = new TooltipGroup("GUTTER_TOOLTIP_GROUP", 0);

  private ClickInfo myLastActionableClick;
  @NotNull
  private final EditorImpl myEditor;
  private final FoldingAnchorsOverlayStrategy myAnchorsDisplayStrategy;
  @Nullable private Int2ObjectMap<List<GutterMark>> myLineToGutterRenderers;
  private boolean myLineToGutterRenderersCacheForLogicalLines;
  private boolean myHasInlaysWithGutterIcons;
  private int myStartIconAreaWidth = START_ICON_AREA_WIDTH.get();
  private int myIconsAreaWidth;
  int myLineNumberAreaWidth = getInitialLineNumberWidth();
  int myAdditionalLineNumberAreaWidth;
  @NotNull private List<FoldRegion> myActiveFoldRegions = Collections.emptyList();
  int myTextAnnotationGuttersSize;
  int myTextAnnotationExtraSize;
  final IntList myTextAnnotationGutterSizes = new IntArrayList();
  final ArrayList<TextAnnotationGutterProvider> myTextAnnotationGutters = new ArrayList<>();
  boolean myGapAfterAnnotations;
  private final Map<TextAnnotationGutterProvider, EditorGutterAction> myProviderToListener = new HashMap<>();
  private String myLastGutterToolTip;
  @NotNull private LineNumberConverter myLineNumberConverter = LineNumberConverter.DEFAULT;
  @Nullable private LineNumberConverter myAdditionalLineNumberConverter;
  private boolean myShowDefaultGutterPopup = true;
  private boolean myCanCloseAnnotations = true;
  @Nullable private ActionGroup myCustomGutterPopupGroup;
  private final Int2ObjectMap<Color> myTextFgColors = new Int2ObjectOpenHashMap<>();
  private boolean myPaintBackground = true;
  private boolean myLeftFreePaintersAreaShown;
  private boolean myRightFreePaintersAreaShown;
  boolean myForceLeftFreePaintersAreaShown;
  boolean myForceRightFreePaintersAreaShown;
  private short myForcedLeftFreePaintersAreaWidth = -1;
  private short myForcedRightFreePaintersAreaWidth = -1;
  private int myLastNonDumbModeIconAreaWidth;
  boolean myDnDInProgress;
  private final EditorGutterLayout myLayout = new EditorGutterLayout(this);
  @Nullable private AccessibleGutterLine myAccessibleGutterLine;
  private final AlphaAnimationContext myAlphaContext = new AlphaAnimationContext(composite -> {
    if (isShowing()) repaint();
  });

  EditorGutterComponentImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      installDnD();
    }
    setOpaque(true);
    myAnchorsDisplayStrategy = new FoldingAnchorsOverlayStrategy(editor);

    Project project = myEditor.getProject();
    if (project != null) {
      project.getMessageBus().connect(myEditor.getDisposable()).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void exitDumbMode(){
          updateSize();
        }
      });
    }
    if (ScreenReader.isActive()) {
      AccessibleGutterLine.installListeners(this);
    }
    else {
      ScreenReader.addPropertyChangeListener(ScreenReader.SCREEN_READER_ACTIVE_PROPERTY, editor.getDisposable(), e -> {
        if ((boolean)e.getNewValue()) {
          AccessibleGutterLine.installListeners(this);
        }
      });
    }
    setRenderingHints();
    HOVER_STATE_LISTENER.addTo(this);
  }

  @NotNull
  EditorImpl getEditor() {
    return myEditor;
  }

  private void installDnD() {
    DnDSupport.createBuilder(this)
      .setBeanProvider(info -> {
        final GutterIconRenderer renderer = getGutterRenderer(info.getPoint());
        if (renderer != null &&
            renderer.getDraggableObject() != null &&
            (info.isCopy() || info.isMove())) {
          myDnDInProgress = true;
          return new DnDDragStartBean(renderer);
        }
        return null;
      })
      .setDropHandlerWithResult(e -> {
          boolean success = true;
          final Object attachedObject = e.getAttachedObject();
          if (attachedObject instanceof GutterIconRenderer && checkDumbAware(attachedObject)) {
            final GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
            if (draggableObject != null) {
              final int line = convertPointToLineNumber(e.getPoint());
              if (line != -1) {





                draggableObject.copy(line, myEditor.getVirtualFile(), e.getAction().getActionId());
              }
            }
          }
          else if (attachedObject instanceof DnDNativeTarget.EventInfo && myEditor.getSettings().isDndEnabled()) {
            Transferable transferable = ((DnDNativeTarget.EventInfo)attachedObject).getTransferable();
            if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
              success = EditorImpl.handleDrop(myEditor, transferable, e.getAction().getActionId());
            }
          }
          myDnDInProgress = false;
          return success;
      })
      .setTargetChecker(e -> {
        final Object attachedObject = e.getAttachedObject();
        if (attachedObject instanceof GutterIconRenderer && checkDumbAware(attachedObject)) {
          final GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
          if (draggableObject != null) {
            final int line = convertPointToLineNumber(e.getPoint());
            if (line != -1) {
              e.setDropPossible(true);
              e.setCursor(draggableObject.getCursor(line, myEditor.getVirtualFile(), e.getAction().getActionId()));
            }
          }
        }
        else if (attachedObject instanceof DnDNativeTarget.EventInfo && myEditor.getSettings().isDndEnabled()) {
          Transferable transferable = ((DnDNativeTarget.EventInfo)attachedObject).getTransferable();
          if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            final int line = convertPointToLineNumber(e.getPoint());
            if (line != -1) {
              e.setDropPossible(true);
              myEditor.getCaretModel().moveToOffset(myEditor.getDocument().getLineStartOffset(line));
            }
          }
        }
        return true;
      })
      .setImageProvider(info -> {
        // [tav] temp workaround for JRE-224
        boolean inUserScale = !SystemInfo.isWindows || !StartupUiUtil.isJreHiDPI(myEditor.getComponent());
        Image image = ImageUtil.toBufferedImage(getDragImage(getGutterRenderer(info.getPoint())), inUserScale);
        return new DnDImage(image, new Point(image.getWidth(null) / 2, image.getHeight(null) / 2));
      })
      .enableAsNativeTarget() // required to accept dragging from editor (as editor component doesn't use DnDSupport to implement drag'n'drop)
      .install();
  }

  Image getDragImage(GutterMark renderer) {
    return IconUtil.toImage(scaleIcon(renderer.getIcon()));
  }

  private void fireResized() {
    processComponentEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));
  }

  @Override
  public Dimension getPreferredSize() {
    int w = myLayout.getWidth();
    Dimension size = new Dimension(w, myEditor.getPreferredHeight());
    JBInsets.addTo(size, getInsets());
    return size;
  }

  @Override
  protected void setUI(ComponentUI newUI) {
    super.setUI(newUI);
    reinitSettings(true);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setRenderingHints();
    reinitSettings(true);
  }

  private void setRenderingHints() {
    UISettings.setupEditorAntialiasing(this);
    putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getEditorFractionalMetricsHint());
  }

  public void reinitSettings(boolean updateGutterSize) {
    updateSize(false, updateGutterSize);
    repaint();
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public void paintComponent(Graphics g_) {
    Rectangle clip = g_.getClipBounds();
    if (clip == null || clip.isEmpty()) {
      return;
    }

    Graphics2D g = (Graphics2D)getComponentGraphics(g_);

    if (myEditor.isDisposed()) {
      g.setColor(EditorImpl.getDisposedBackground());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
      return;
    }

    AffineTransform old = setMirrorTransformIfNeeded(g, 0, getWidth());

    EditorUIUtil.setupAntialiasing(g);
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getEditorFractionalMetricsHint());

    Color backgroundColor = getBackground();

    int startVisualLine;
    int endVisualLine;

    int firstVisibleOffset;
    int lastVisibleOffset;

    Segment focusModeRange = myEditor.getFocusModeRange();
    if (focusModeRange == null) {
      startVisualLine = myEditor.yToVisualLine(clip.y);
      endVisualLine = myEditor.yToVisualLine(clip.y + clip.height - 1);

      firstVisibleOffset = myEditor.visualLineStartOffset(startVisualLine);
      lastVisibleOffset = myEditor.visualLineStartOffset(endVisualLine + 1);
    }
    else {
      firstVisibleOffset = focusModeRange.getStartOffset();
      lastVisibleOffset = focusModeRange.getEndOffset();

      startVisualLine = myEditor.offsetToVisualLine(firstVisibleOffset);
      endVisualLine = myEditor.offsetToVisualLine(lastVisibleOffset);
    }

    if (firstVisibleOffset > lastVisibleOffset) {
      LOG.error("Unexpected painting range: (" + firstVisibleOffset + ":" + lastVisibleOffset
                + "), visual line range: (" + startVisualLine + ":" + endVisualLine
                + "), clip: " + clip + ", focus range: " + focusModeRange);
    }

    // paint all backgrounds
    int gutterSeparatorX = getWhitespaceSeparatorOffset();
    Color caretRowColor = getCaretRowColor();
    paintBackground(g, clip, 0, gutterSeparatorX, backgroundColor, caretRowColor);
    paintBackground(g, clip, gutterSeparatorX, getFoldingAreaWidth(), myEditor.getBackgroundColor(), caretRowColor);

    paintEditorBackgrounds(g, firstVisibleOffset, lastVisibleOffset);

    Object hint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    if (!JreHiDpiUtil.isJreHiDPI(g)) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    try {
      paintAnnotations(g, startVisualLine, endVisualLine);

      if (focusModeRange != null) {
        int startY = Math.max(myEditor.visualLineToY(startVisualLine), clip.y);
        int endY = Math.min(myEditor.visualLineToY(endVisualLine), clip.y + clip.height);
        g.setClip(clip.x, startY, clip.width, endY - startY);
      }

      paintLineMarkers(g, firstVisibleOffset, lastVisibleOffset, startVisualLine, endVisualLine);

      g.setClip(clip);

      paintFoldingLines(g, clip);
      paintFoldingTree(g, clip, firstVisibleOffset, lastVisibleOffset);
      paintLineNumbers(g, startVisualLine, endVisualLine);
      paintCurrentAccessibleLine(g);

      if (ExperimentalUI.isNewUI()) {
        g.setColor(getEditor().getColorsScheme().getColor(EditorColors.INDENT_GUIDE_COLOR));
        double offsetX = getExpUIVerticalLineX();
        LinePainter2D.paint(g, offsetX, clip.y, offsetX, clip.y + clip.height);
      }
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
    }

    if (old != null) g.setTransform(old);
  }

  private double getExpUIVerticalLineX() {
    return getFoldingAreaOffset() + getFoldingAnchorWidth() - scale(1f);
  }

  private void paintEditorBackgrounds(Graphics g, int firstVisibleOffset, int lastVisibleOffset) {
    myTextFgColors.clear();
    Color defaultBackgroundColor = myEditor.getBackgroundColor();
    Color defaultForegroundColor = myEditor.getColorsScheme().getDefaultForeground();
    int startX = myEditor.isInDistractionFreeMode() ? 0
                                                    : ExperimentalUI.isNewUI() ? (int)getExpUIVerticalLineX() + 1 : getWhitespaceSeparatorOffset();
    IterationState state = new IterationState(myEditor, firstVisibleOffset, lastVisibleOffset, null, true, false, true, false);
    while (!state.atEnd()) {
      drawEditorBackgroundForRange(g, state.getStartOffset(), state.getEndOffset(), state.getMergedAttributes(),
                                   defaultBackgroundColor, defaultForegroundColor, startX);
      state.advance();
    }
  }

  private void drawEditorBackgroundForRange(Graphics g, int startOffset, int endOffset, TextAttributes attributes,
                                            Color defaultBackgroundColor, Color defaultForegroundColor, int startX) {
    Color bgColor = myEditor.getBackgroundColor(attributes);
    if (Comparing.equal(bgColor, defaultBackgroundColor)) return;

    VisualPosition visualStart = myEditor.offsetToVisualPosition(startOffset, true, false);
    VisualPosition visualEnd   = myEditor.offsetToVisualPosition(endOffset, false, false);
    int startVisualLine = visualStart.getLine() + (visualStart.getColumn() == 0 ? 0 : 1);
    int endVisualLine = visualEnd.getLine() - (visualEnd.getColumn() == 0 ? 1 : 0);
    if (startVisualLine <= endVisualLine) {
      int startY = myEditor.visualLineToY(startVisualLine);
      int endY = myEditor.visualLineToYRange(endVisualLine)[1];
      g.setColor(bgColor);
      g.fillRect(startX, startY, getWidth() - startX, endY - startY);

      Color fgColor = attributes.getForegroundColor();
      if (!Comparing.equal(fgColor, defaultForegroundColor)) {
        for (int line = startVisualLine; line <= endVisualLine; line++) {
          myTextFgColors.put(line, fgColor);
        }
      }
    }
  }

  private void processClose(final MouseEvent e) {
    final IdeEventQueue queue = IdeEventQueue.getInstance();

    // See IDEA-59553 for rationale on why this feature is disabled
    //if (isLineNumbersShown()) {
    //  if (e.getX() >= getLineNumberAreaOffset() && getLineNumberAreaOffset() + getLineNumberAreaWidth() >= e.getX()) {
    //    queue.blockNextEvents(e);
    //    myEditor.getSettings().setLineNumbersShown(false);
    //    e.consume();
    //    return;
    //  }
    //}

    if (getGutterRenderer(e) != null) return;

    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      queue.blockNextEvents(e);
      closeAllAnnotations();
      e.consume();
    }
  }

  private void paintAnnotations(Graphics2D g, int startVisualLine, int endVisualLine) {
    int x = getAnnotationsAreaOffset();
    int w = getAnnotationsAreaWidthEx();

    if (w == 0) return;

    int viewportStartY = myEditor.getScrollingModel().getVisibleArea().y;

    AffineTransform old = setMirrorTransformIfNeeded(g, x, w);
    try {
      Color color = myEditor.getColorsScheme().getColor(EditorColors.ANNOTATIONS_COLOR);
      g.setColor(color != null ? color : JBColor.blue);
      g.setFont(myEditor.getColorsScheme().getFont(EditorFontType.PLAIN));

      for (int i = 0; i < myTextAnnotationGutters.size(); i++) {
        TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(i);

        int lineHeight = myEditor.getLineHeight();
        int lastLine = myEditor.logicalToVisualPosition(new LogicalPosition(endLineNumber(), 0)).line;
        endVisualLine = Math.min(endVisualLine, lastLine);
        if (startVisualLine > endVisualLine) {
          break;
        }

        int annotationSize = myTextAnnotationGutterSizes.getInt(i);

        int logicalLine = -1;
        Color bg = null;
        VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, startVisualLine);
        while (!visLinesIterator.atEnd() && visLinesIterator.getVisualLine() <= endVisualLine) {
          if (!visLinesIterator.isCustomFoldRegionLine()) {
            int y = visLinesIterator.getY();
            int bgLineHeight = lineHeight;
            boolean paintText = !visLinesIterator.startsWithSoftWrap() || y <= viewportStartY;

            if (y < viewportStartY && visLinesIterator.endsWithSoftWrap()) {  // "sticky" line annotation
              y = viewportStartY;
            }
            else if (viewportStartY < y && y < viewportStartY + lineHeight && visLinesIterator.startsWithSoftWrap()) {
              // avoid drawing bg over the "sticky" line above, or over a possible gap in the gutter below (e.g. code vision)
              bgLineHeight = y - viewportStartY;
              y = viewportStartY + lineHeight;
            }

            if (paintText || logicalLine == -1) {
              logicalLine = visLinesIterator.getDisplayedLogicalLine();
              bg = gutterProvider.getBgColor(logicalLine, myEditor);
            }
            if (bg != null) {
              g.setColor(bg);
              g.fillRect(x, y, annotationSize, bgLineHeight);
            }
            if (paintText) {
              paintAnnotationLine(g, gutterProvider, logicalLine, x, y);
            }
          }
          visLinesIterator.advance();
        }

        x += annotationSize;
      }

    }
    finally {
      if (old != null) g.setTransform(old);
    }
  }

  private void paintAnnotationLine(Graphics g, TextAnnotationGutterProvider gutterProvider, int line, int x, int y) {
    String s = gutterProvider.getLineText(line, myEditor);
    if (!StringUtil.isEmpty(s)) {
      g.setColor(myEditor.getColorsScheme().getColor(gutterProvider.getColor(line, myEditor)));
      EditorFontType style = gutterProvider.getStyle(line, myEditor);
      Font font = getFontForText(s, style);
      g.setFont(font);
      g.drawString(s, (gutterProvider.useMargin() ? getGapBetweenAnnotations() / 2 : 0) + x, y + myEditor.getAscent());
    }
  }

  private Font getFontForText(String text, EditorFontType style) {
    Font font = ExperimentalUI.isNewUI() ? JBFont.regular().deriveFont((float)myEditor.getFontSize())
                                         : myEditor.getColorsScheme().getFont(style);
    return UIUtil.getFontWithFallbackIfNeeded(font, text);
  }

  private void paintFoldingTree(@NotNull Graphics g, @NotNull Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    if (isFoldingOutlineShown()) {
      doPaintFoldingTree((Graphics2D)g, clip, firstVisibleOffset, lastVisibleOffset);
    }
  }

  private void paintLineMarkers(Graphics2D g, int firstVisibleOffset, int lastVisibleOffset, int firstVisibleLine, int lastVisibleLine) {
    if (isLineMarkersShown()) {
      paintGutterRenderers(g, firstVisibleOffset, lastVisibleOffset, firstVisibleLine, lastVisibleLine);
    }
  }

  private void paintBackground(final Graphics g,
                               final Rectangle clip,
                               final int x,
                               final int width,
                               Color background,
                               Color caretRowColor) {
    g.setColor(background);
    g.fillRect(x, clip.y, width, clip.height);

    paintCaretRowBackground(g, x, width, caretRowColor);
  }

  private Color getCaretRowColor() {
    if (!myEditor.getSettings().isCaretRowShown()) {
      return null;
    }
    if (!Registry.is("highlight.caret.line.at.custom.fold") && isCaretAtCustomFolding()) {
      return null;
    }
    return myEditor.getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
  }

  private boolean isCaretAtCustomFolding() {
    FoldingModelImpl foldingModel = myEditor.getFoldingModel();
    FoldRegion[] topLevelRegions = foldingModel.fetchTopLevel();
    if (topLevelRegions == null) {
      return false;
    }
    int caretOffset = myEditor.getCaretModel().getOffset();
    int idx = foldingModel.getLastCollapsedRegionBefore(caretOffset);
    if (idx >= 0) {
      FoldRegion region = topLevelRegions[idx];
      if (region instanceof CustomFoldRegion && region.getEndOffset() == caretOffset) {
        return true;
      }
    }
    if (idx + 1 < topLevelRegions.length) {
      FoldRegion region = topLevelRegions[idx + 1];
      if (region instanceof CustomFoldRegion && region.getStartOffset() <= caretOffset) {
        return true;
      }
    }
    return false;
  }

  private void paintCaretRowBackground(final Graphics g, final int x, final int width, Color color) {
    if (color != null) {
      int caretLine = myEditor.getCaretModel().getVisualPosition().line;
      int[] yRange = myEditor.visualLineToYRange(caretLine);
      g.setColor(color);
      g.fillRect(x, yRange[0], width, yRange[1] - yRange[0]);
    }
  }

  private void paintLineNumbers(Graphics2D g, int startVisualLine, int endVisualLine) {
    if (isLineNumbersShown()) {
      int offset = getLineNumberAreaOffset() + myLineNumberAreaWidth;
      doPaintLineNumbers(g, startVisualLine, endVisualLine, offset, myLineNumberConverter);
      if (myAdditionalLineNumberConverter != null) {
        doPaintLineNumbers(g, startVisualLine, endVisualLine, offset + getAreaWidthWithGap(myAdditionalLineNumberAreaWidth),
                           myAdditionalLineNumberConverter);
      }
    }
  }

  private void paintCurrentAccessibleLine(Graphics2D g) {
    if (myAccessibleGutterLine != null) {
      myAccessibleGutterLine.paint(g);
    }
  }

  @Override
  public Color getBackground() {
    if (myEditor.isInDistractionFreeMode() || !myPaintBackground) {
      return myEditor.getBackgroundColor();
    }
    Color color = myEditor.getColorsScheme().getColor(EditorColors.GUTTER_BACKGROUND);
    if (ExperimentalUI.isNewEditorTabs()) {
      color = myEditor.getBackgroundColor();
    }
    return color != null ? color : EditorColors.GUTTER_BACKGROUND.getDefaultColor();
  }

  private Font getFontForLineNumbers() {
    Font editorFont = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
    float editorFontSize = editorFont.getSize2D();
    return editorFont.deriveFont(Math.max(1f, editorFontSize - 1f));
  }

  private int calcLineNumbersAreaWidth(int maxLineNumber) {
    return FontLayoutService.getInstance().stringWidth(getFontMetrics(getFontForLineNumbers()), Integer.toString(maxLineNumber));
  }

  private void doPaintLineNumbers(Graphics2D g, int startVisualLine, int endVisualLine, int offset,
                                  @NotNull LineNumberConverter converter) {
    int lastLine = myEditor.logicalToVisualPosition(
      new LogicalPosition(endLineNumber(), 0))
      .line;
    endVisualLine = Math.min(endVisualLine, lastLine);
    if (startVisualLine > endVisualLine) {
      return;
    }

    Color color = myEditor.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR);
    Color colorUnderCaretRow = myEditor.getColorsScheme().getColor(EditorColors.LINE_NUMBER_ON_CARET_ROW_COLOR);
    Font font = getFontForLineNumbers();
    g.setFont(font);
    int viewportStartY = myEditor.getScrollingModel().getVisibleArea().y;

    AffineTransform old = setMirrorTransformIfNeeded(g, getLineNumberAreaOffset(), getLineNumberAreaWidth());
    try {
      int caretLogicalLine = myEditor.getCaretModel().getLogicalPosition().line;
      VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, startVisualLine);
      while (!visLinesIterator.atEnd() && visLinesIterator.getVisualLine() <= endVisualLine) {
        if (!visLinesIterator.isCustomFoldRegionLine() &&
            (!visLinesIterator.startsWithSoftWrap() || visLinesIterator.getY() <= viewportStartY)) {
          int logicalLine = visLinesIterator.getDisplayedLogicalLine();
          Integer lineToDisplay = converter.convert(myEditor, logicalLine + 1);
          if (lineToDisplay != null) {
            int y = visLinesIterator.getY();
            if (y < viewportStartY && visLinesIterator.endsWithSoftWrap()) {  // "sticky" line number
              y = viewportStartY;
            }
            if (myEditor.isInDistractionFreeMode()) {
              Color fgColor = myTextFgColors.get(visLinesIterator.getVisualLine());
              g.setColor(fgColor != null ? fgColor : color != null ? color : JBColor.blue);
            } else {
              g.setColor(color);
            }

            if (colorUnderCaretRow != null && caretLogicalLine == logicalLine) {
              g.setColor(colorUnderCaretRow);
            }

            Icon iconOnTheLine = null;
            Icon hoverIcon = null;
            if (ExperimentalUI.isNewUI() && EditorUtil.isRealFileEditor(getEditor())) {
              VisualPosition visualPosition = myEditor.logicalToVisualPosition(new LogicalPosition(logicalLine, 0));
              Optional<GutterMark> breakpoint = getGutterRenderers(visualPosition.line).stream()
                .filter(r -> r instanceof GutterIconRenderer &&
                             ((GutterIconRenderer)r).getAlignment() == GutterIconRenderer.Alignment.LINE_NUMBERS)
                .findFirst();
              if (breakpoint.isPresent()) {
                iconOnTheLine = breakpoint.get().getIcon();
              }
                if (myAlphaContext.isVisible() && Objects.equals(getClientProperty("active.line.number"), visualPosition.line)) {
                  Object activeIcon = getClientProperty("line.number.hover.icon");
                  if (activeIcon instanceof Icon) {
                    hoverIcon = (Icon)activeIcon;
                  }
              }
            }

            if (iconOnTheLine == null && hoverIcon == null) {
              String s = String.valueOf(lineToDisplay);
              int textOffset = isMirrored() ?
                               offset - getLineNumberAreaWidth() - 1 :
                               offset - FontLayoutService.getInstance().stringWidth(g.getFontMetrics(), s);

              g.drawString(s, textOffset,y + myEditor.getAscent());
            } else if (hoverIcon != null && iconOnTheLine == null) {
              hoverIcon = scaleIcon(hoverIcon);
              int iconX = offset - hoverIcon.getIconWidth();
              int iconY = y + (visLinesIterator.getLineHeight() - hoverIcon.getIconHeight()) / 2;
              GraphicsConfig config = GraphicsUtil.paintWithAlpha(g, 0.5f); //todo[kb] move transparency to theming options
              hoverIcon.paintIcon(this, g, iconX, iconY);
              config.restore();
            }
          }
        }
        visLinesIterator.advance();
      }
    }
    finally {
      if (old != null) g.setTransform(old);
    }
  }

  private int endLineNumber() {
    return Math.max(0, myEditor.getDocument().getLineCount() - 1);
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (myEditor.isDisposed()) return null;

    if (EditorGutter.KEY.is(dataId)) {
      return this;
    }
    if (CommonDataKeys.EDITOR.is(dataId)) {
      return myEditor;
    }
    if (EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR.is(dataId)) {
      if (myLastActionableClick == null) return null;
      return myLastActionableClick.myLogicalLineAtCursor;
    }
    if (EditorGutterComponentEx.ICON_CENTER_POSITION.is(dataId)) {
      if (myLastActionableClick == null) return null;
      return myLastActionableClick.myIconCenterPosition;
    }
    return null;
  }

  boolean isShowGapAfterAnnotations() {
    return isAnnotationsShown() && (myGapAfterAnnotations || myTextAnnotationExtraSize > 0);
  }

  @FunctionalInterface
  interface RangeHighlighterProcessor {
    void process(@NotNull RangeHighlighter highlighter);
  }

  void processRangeHighlighters(int startOffset, int endOffset, @NotNull RangeHighlighterProcessor processor) {
    // we limit highlighters to process to between line starting at startOffset and line ending at endOffset
    MarkupIterator<RangeHighlighterEx> docHighlighters =
      myEditor.getFilteredDocumentMarkupModel().overlappingIterator(startOffset, endOffset, true);
    MarkupIterator<RangeHighlighterEx> editorHighlighters =
      myEditor.getMarkupModel().overlappingIterator(startOffset, endOffset, true);

    try {
      RangeHighlighterEx lastDocHighlighter = null;
      RangeHighlighterEx lastEditorHighlighter = null;
      while (true) {
        if (lastDocHighlighter == null && docHighlighters.hasNext()) {
          lastDocHighlighter = docHighlighters.next();
          if (lastDocHighlighter.getAffectedAreaStartOffset() > endOffset) {
            lastDocHighlighter = null;
            continue;
          }
          if (lastDocHighlighter.getAffectedAreaEndOffset() < startOffset) {
            lastDocHighlighter = null;
            continue;
          }
        }

        if (lastEditorHighlighter == null && editorHighlighters.hasNext()) {
          lastEditorHighlighter = editorHighlighters.next();
          if (lastEditorHighlighter.getAffectedAreaStartOffset() > endOffset) {
            lastEditorHighlighter = null;
            continue;
          }
          if (lastEditorHighlighter.getAffectedAreaEndOffset() < startOffset) {
            lastEditorHighlighter = null;
            continue;
          }
        }

        if (lastDocHighlighter == null && lastEditorHighlighter == null) return;

        final RangeHighlighterEx lowerHighlighter;
        if (less(lastDocHighlighter, lastEditorHighlighter)) {
          lowerHighlighter = lastDocHighlighter;
          lastDocHighlighter = null;
        }
        else {
          lowerHighlighter = lastEditorHighlighter;
          lastEditorHighlighter = null;
        }

        processor.process(lowerHighlighter);
      }
    }
    finally {
      docHighlighters.dispose();
      editorHighlighters.dispose();
    }
  }

  private static boolean isValidLine(@NotNull Document document, int line) {
    if (line < 0) return false;
    int lineCount = document.getLineCount();
    return lineCount == 0 ? line == 0 : line < lineCount;
  }

  private static boolean less(RangeHighlighter h1, RangeHighlighter h2) {
    return h1 != null && (h2 == null || h1.getStartOffset() < h2.getStartOffset());
  }

  @Override
  public void revalidateMarkup() {
    updateSize();
  }

  void updateSizeOnShowNotify() {
    updateSize(false, true);
  }

  public void updateSize() {
    updateSize(false, false);
  }

  void updateSize(boolean onLayout, boolean canShrink) {
    int prevHash = sizeHash();

    if (!onLayout) {
      clearLineToGutterRenderersCache();
      calcLineNumberAreaWidth();
      calcLineMarkerAreaWidth(canShrink);
      calcAnnotationsSize();
    }
    calcAnnotationExtraSize();

    if (prevHash != sizeHash()) {
      fireResized();
    }
    repaint();
  }

  private int sizeHash() {
    int result = getLineMarkerAreaWidth();
    result = 31 * result + myTextAnnotationGuttersSize;
    result = 31 * result + myTextAnnotationExtraSize;
    result = 31 * result + getLineNumberAreaWidth();
    return result;
  }

  private void calcAnnotationsSize() {
    myTextAnnotationGuttersSize = 0;
    myGapAfterAnnotations = false;
    final int lineCount = Math.max(myEditor.getDocument().getLineCount(), 1);
    final int guttersCount = myTextAnnotationGutters.size();
    for (int j = 0; j < guttersCount; j++) {
      TextAnnotationGutterProvider gutterProvider = myTextAnnotationGutters.get(j);
      int gutterSize = 0;
      for (int i = 0; i < lineCount; i++) {
        String lineText = gutterProvider.getLineText(i, myEditor);
        if (!StringUtil.isEmpty(lineText)) {
          EditorFontType style = gutterProvider.getStyle(i, myEditor);
          Font font = getFontForText(lineText, style);
          FontMetrics fontMetrics = getFontMetrics(font);
          gutterSize = Math.max(gutterSize, fontMetrics.stringWidth(lineText));
        }
      }
      if (gutterSize > 0) {
        boolean margin = gutterProvider.useMargin();
        myGapAfterAnnotations = margin;
        if (margin) {
          gutterSize += getGapBetweenAnnotations();
        }
      }
      myTextAnnotationGutterSizes.set(j, gutterSize);
      myTextAnnotationGuttersSize += gutterSize;
    }
  }

  private void calcAnnotationExtraSize() {
    myTextAnnotationExtraSize = 0;
    if (!myEditor.isInDistractionFreeMode() || isMirrored()) return;

    int marginFromSettings = AdvancedSettings.getInt(DISTRACTION_FREE_MARGIN);
    if (marginFromSettings != -1) {
      myTextAnnotationExtraSize = marginFromSettings;
      return;
    }

    Component outerContainer = ComponentUtil.findParentByCondition(myEditor.getComponent(), c -> EditorComposite.isEditorComposite(c));
    if (outerContainer == null) return;

    EditorSettings settings = myEditor.getSettings();
    Project project = myEditor.getProject();
    if (project != null && project.isDisposed()) return;
    int rightMargin = settings.getRightMargin(project);
    if (rightMargin <= 0) return;

    JComponent editorComponent = myEditor.getComponent();
    RelativePoint point = new RelativePoint(editorComponent, new Point(0, 0));
    Point editorLocationInWindow = point.getPoint(outerContainer);

    int editorLocationX = (int)editorLocationInWindow.getX();
    int rightMarginX = rightMargin * EditorUtil.getSpaceWidth(Font.PLAIN, myEditor) + editorLocationX;

    int width = editorLocationX + editorComponent.getWidth();
    if (rightMarginX < width && editorLocationX < width - rightMarginX) {
      int centeredSize = (width - rightMarginX - editorLocationX) / 2 - (getLineMarkerAreaWidth() + getLineNumberAreaWidth() +
                                                                         getFoldingAreaWidth() + 2 * getGapBetweenAreas());
      myTextAnnotationExtraSize = Math.max(0, centeredSize - myTextAnnotationGuttersSize);
    }
  }

  private boolean logicalLinesMatchVisualOnes() {
    return myEditor.getSoftWrapModel().getSoftWrapsIntroducedLinesNumber() == 0 &&
           myEditor.getFoldingModel().getTotalNumberOfFoldedLines() == 0;
  }

  void clearLineToGutterRenderersCache() {
    myLineToGutterRenderers = null;
  }

  private void buildGutterRenderersCache() {
    myLineToGutterRenderersCacheForLogicalLines = logicalLinesMatchVisualOnes();
    myLineToGutterRenderers = new Int2ObjectOpenHashMap<>();
    processRangeHighlighters(0, myEditor.getDocument().getTextLength(), highlighter -> {
      GutterMark renderer = highlighter.getGutterIconRenderer();
      if (!shouldBeShown(renderer)) {
        return;
      }
      if (!isHighlighterVisible(highlighter)) {
        return;
      }
      int line = myEditor.offsetToVisualLine(highlighter.getStartOffset());
      List<GutterMark> renderers = myLineToGutterRenderers.get(line);
      if (renderers == null) {
        renderers = new SmartList<>();
        myLineToGutterRenderers.put(line, renderers);
      }

      renderers.add(renderer);
    });

    FoldRegion[] topLevelRegions = myEditor.getFoldingModel().fetchTopLevel();
    if (topLevelRegions != null) {
      for (FoldRegion region : topLevelRegions) {
        if (region instanceof CustomFoldRegion) {
          GutterIconRenderer renderer = ((CustomFoldRegion)region).getGutterIconRenderer();
          int line = myEditor.offsetToVisualLine(region.getStartOffset());
          if (shouldBeShown(renderer)) {
            myLineToGutterRenderers.put(line, List.of(renderer));
          }
          else {
            myLineToGutterRenderers.remove(line);
          }
        }
      }
    }

    List<GutterMarkPreprocessor> gutterMarkPreprocessors = GutterMarkPreprocessor.EP_NAME.getExtensionList();
    for (Int2ObjectMap.Entry<List<GutterMark>> entry : Int2ObjectMaps.fastIterable(myLineToGutterRenderers)) {
      List<GutterMark> newValue = entry.getValue();
      for (GutterMarkPreprocessor preprocessor : gutterMarkPreprocessors) {
        newValue = preprocessor.processMarkers(entry.getValue());
      }
      // don't allow more than 4 icons per line
      entry.setValue(ContainerUtil.getFirstItems(newValue, 4));
    }
  }

  private boolean shouldBeShown(@Nullable GutterMark gutterIconRenderer) {
    return gutterIconRenderer != null && (areIconsShown() || gutterIconRenderer instanceof NonHideableIconGutterMark);
  }

  private void calcLineMarkerAreaWidth(boolean canShrink) {
    myLeftFreePaintersAreaShown = myForceLeftFreePaintersAreaShown;
    myRightFreePaintersAreaShown = myForceRightFreePaintersAreaShown;

    processRangeHighlighters(0, myEditor.getDocument().getTextLength(), highlighter -> {
      LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
      if (lineMarkerRenderer != null) {
        LineMarkerRendererEx.Position position = getLineMarkerPosition(lineMarkerRenderer);
        if (position == LineMarkerRendererEx.Position.LEFT && isLineMarkerVisible(highlighter)) myLeftFreePaintersAreaShown = true;
        if (position == LineMarkerRendererEx.Position.RIGHT && isLineMarkerVisible(highlighter)) myRightFreePaintersAreaShown = true;
      }
    });

    int minWidth = areIconsShown() ? scaleWidth(myStartIconAreaWidth) : 0;
    myIconsAreaWidth = canShrink ? minWidth : Math.max(myIconsAreaWidth, minWidth);

    for (Int2ObjectMap.Entry<List<GutterMark>> entry : processGutterRenderers()) {
      int width = 1;
      List<GutterMark> renderers = entry.getValue();
      for (int i = 0; i < renderers.size(); i++) {
        GutterMark renderer = renderers.get(i);
        if (!checkDumbAware(renderer)) continue;
        width += scaleIcon(renderer.getIcon()).getIconWidth();
        if (i > 0) width += getGapBetweenIcons();
      }
      if (myIconsAreaWidth < width) {
        myIconsAreaWidth = width + 1;
      }
    }

    myHasInlaysWithGutterIcons = false;
    myEditor.getInlayModel().getBlockElementsInRange(0, myEditor.getDocument().getTextLength()).forEach(inlay -> {
      GutterIconRenderer iconRenderer = inlay.getGutterIconRenderer();
      if (shouldBeShown(iconRenderer) && checkDumbAware(iconRenderer) && !EditorUtil.isInlayFolded(inlay)) {
        Icon icon = scaleIcon(iconRenderer.getIcon());
        if (icon.getIconHeight() <= inlay.getHeightInPixels()) {
          myHasInlaysWithGutterIcons = true;
          myIconsAreaWidth = Math.max(myIconsAreaWidth, icon.getIconWidth());
        }
      }
    });

    if (isDumbMode()) {
      myIconsAreaWidth = Math.max(myIconsAreaWidth, myLastNonDumbModeIconAreaWidth);
    }
    else {
      myLastNonDumbModeIconAreaWidth = myIconsAreaWidth;
    }
  }

  @Override
  @NotNull
  public List<GutterMark> getGutterRenderers(int line) {
    if (myLineToGutterRenderers == null || myLineToGutterRenderersCacheForLogicalLines != logicalLinesMatchVisualOnes()) {
      buildGutterRenderersCache();
    }

    Segment focusModeRange = myEditor.getFocusModeRange();
    if (focusModeRange != null) {
      int start = myEditor.offsetToVisualLine(focusModeRange.getStartOffset());
      int end = myEditor.offsetToVisualLine(focusModeRange.getEndOffset());
      if (line < start || line > end) return Collections.emptyList();
    }

    List<GutterMark> marks = myLineToGutterRenderers.get(line);
    return marks != null ? marks : Collections.emptyList();
  }

  private @NotNull ObjectIterable<Int2ObjectMap.Entry<List<GutterMark>>> processGutterRenderers() {
    if (myLineToGutterRenderers == null || myLineToGutterRenderersCacheForLogicalLines != logicalLinesMatchVisualOnes()) {
      buildGutterRenderersCache();
    }
    return Int2ObjectMaps.fastIterable(myLineToGutterRenderers);
  }

  private boolean isHighlighterVisible(RangeHighlighter highlighter) {
    return !FoldingUtil.isHighlighterFolded(myEditor, highlighter);
  }

  private void paintGutterRenderers(final Graphics2D g,
                                    int firstVisibleOffset, int lastVisibleOffset, int firstVisibleLine, int lastVisibleLine) {
    Object hint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    try {
      List<RangeHighlighter> highlighters = new ArrayList<>();
      processRangeHighlighters(firstVisibleOffset, lastVisibleOffset, highlighter -> {
        LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
        if (renderer != null) highlighters.add(highlighter);
      });

      ContainerUtil.sort(highlighters, Comparator.comparingInt(RangeHighlighter::getLayer));

      for (RangeHighlighter highlighter : highlighters) {
        paintLineMarkerRenderer(highlighter, g);
      }
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
    }

    paintIcons(firstVisibleLine, lastVisibleLine, g);
  }

  private void paintIcons(final int firstVisibleLine, final int lastVisibleLine, final Graphics2D g) {
    VisualLinesIterator visLinesIterator = new VisualLinesIterator(myEditor, firstVisibleLine);
    while (!visLinesIterator.atEnd()) {
      int visualLine = visLinesIterator.getVisualLine();
      if (visualLine > lastVisibleLine) break;
      int y = visLinesIterator.getY();

      List<GutterMark> renderers = getGutterRenderers(visualLine);
      paintIconRow(visualLine, y, renderers, g);

      if (myHasInlaysWithGutterIcons) {
        Rectangle clip = g.getClipBounds();
        int curY = y;
        for (Inlay<?> inlay : visLinesIterator.getBlockInlaysAbove()) {
          if (curY <= clip.y) break;
          int height = inlay.getHeightInPixels();
          if (height > 0) {
            int newY = curY - height;
            paintInlayIcon(inlay, g, newY);
            curY = newY;
          }
        }
        curY = y + visLinesIterator.getLineHeight();
        for (Inlay<?> inlay : visLinesIterator.getBlockInlaysBelow()) {
          if (curY >= clip.y + clip.height) break;
          int height = inlay.getHeightInPixels();
          if (height > 0) {
            paintInlayIcon(inlay, g, curY);
            curY += height;
          }
        }
      }

      visLinesIterator.advance();
    }
  }

  private void paintInlayIcon(Inlay<?> inlay, Graphics2D g, int y) {
    GutterIconRenderer iconRenderer = inlay.getGutterIconRenderer();
    if (shouldBeShown(iconRenderer) && checkDumbAware(iconRenderer)) {
      Icon icon = scaleIcon(iconRenderer.getIcon());
      if (icon.getIconHeight() <= inlay.getHeightInPixels()) {
        int iconWidth = icon.getIconWidth();
        int x = getIconAreaOffset() + myIconsAreaWidth - iconWidth;
        y += getTextAlignmentShiftForInlayIcon(icon, inlay);
        AffineTransform old = setMirrorTransformIfNeeded(g, x, iconWidth);
        icon.paintIcon(this, g, x, y);
        if (old != null) g.setTransform(old);
      }
    }
  }

  private void paintIconRow(int visualLine, int lineY, List<? extends GutterMark> row, final Graphics2D g) {
    processIconsRowForY(lineY, row, (x, y, renderer) -> {
      boolean isLoading = myLastActionableClick != null &&
                          myLastActionableClick.myProgressVisualLine == visualLine &&
                          myLastActionableClick.myProgressGutterMark == renderer;
      Icon icon = scaleIcon(renderer.getIcon());
      if (isLoading) {
        Icon loadingIcon = scaleIcon(AnimatedIcon.Default.INSTANCE);
        x -= (loadingIcon.getIconWidth() - icon.getIconWidth()) / 2;
        y -= (loadingIcon.getIconHeight() - icon.getIconHeight()) / 2;
        icon = loadingIcon;
      }

      AffineTransform old = setMirrorTransformIfNeeded(g, x, icon.getIconWidth());
      try {
        icon.paintIcon(this, g, x, y);
      }
      finally {
        if (old != null) g.setTransform(old);
      }
    });
  }

  private void paintLineMarkerRenderer(@NotNull RangeHighlighter highlighter, @NotNull Graphics g) {
    LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
    if (lineMarkerRenderer != null) {
      Rectangle rectangle = getLineRendererRectangle(highlighter);
      if (rectangle != null) {
        lineMarkerRenderer.paint(myEditor, g, rectangle);
      }
    }
  }

  private boolean isLineMarkerVisible(RangeHighlighter highlighter) {
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();

    FoldRegion startFoldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    FoldRegion endFoldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(endOffset);
    return startFoldRegion == null || !startFoldRegion.equals(endFoldRegion);
  }

  @Override
  public boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    if (ExperimentalUI.isNewUI()) {
      int x = e.getX();
      int offset = getLineMarkerFreePaintersAreaOffset();
      int width = myLayout.getAreaWidth(EditorGutterLayout.RIGHT_FREE_PAINTERS_AREA);
      return offset < x && x <= offset + width;
    }
    return e.getX() > getLineMarkerFreePaintersAreaOffset();
  }

  @Nullable
  Rectangle getLineRendererRectangle(RangeHighlighter highlighter) {
    if (!isLineMarkerVisible(highlighter)) return null;

    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();

    int startY = myEditor.visualLineToY(myEditor.offsetToVisualLine(startOffset));
    int endY = myEditor.visualLineToYRange(myEditor.offsetToVisualLine(endOffset))[1];

    LineMarkerRenderer renderer = Objects.requireNonNull(highlighter.getLineMarkerRenderer());
    LineMarkerRendererEx.Position position = getLineMarkerPosition(renderer);

    int w;
    int x;
    switch (position) {
      case LEFT:
        w = getLeftFreePaintersAreaWidth();
        x = getLeftFreePaintersAreaOffset();
        break;
      case RIGHT:
        w = getRightFreePaintersAreaWidth();
        x = getLineMarkerFreePaintersAreaOffset();
        break;
      case CUSTOM:
        w = getWidth();
        x = 0;
        break;
      default:
        throw new IllegalArgumentException(position.name());
    }

    int height = endY - startY;
    return new Rectangle(x, startY, w, height);
  }

  @FunctionalInterface
  interface LineGutterIconRendererProcessor {
    void process(int x, int y, @NotNull GutterMark renderer);
  }

  private float getEditorScaleFactor() {
    if (Registry.is("editor.scale.gutter.icons")) {
      float scale = myEditor.getScale();
      if (Math.abs(1f - scale) > 0.10f) {
        return scale;
      }
    }
    return 1f;
  }

  Icon scaleIcon(Icon icon) {
    float scale = getEditorScaleFactor();
    return scale == 1 ? icon : IconUtil.scale(icon, this, scale);
  }

  private int scaleWidth(int width) {
    return (int) (getEditorScaleFactor() * width);
  }

  void processIconsRow(int line, @NotNull List<? extends GutterMark> row, @NotNull LineGutterIconRendererProcessor processor) {
    processIconsRowForY(myEditor.visualLineToY(line), row, processor);
  }

  // y should be equal to visualLineToY(visualLine)
  private void processIconsRowForY(int y, @NotNull List<? extends GutterMark> row, @NotNull LineGutterIconRendererProcessor processor) {
    if (row.isEmpty()) return;
    int middleCount = 0;
    int middleSize = 0;
    int x = getIconAreaOffset() + 2;

    for (GutterMark r : row) {
      if (!checkDumbAware(r)) continue;
      final GutterIconRenderer.Alignment alignment = ((GutterIconRenderer)r).getAlignment();
      final Icon icon = scaleIcon(r.getIcon());
      if (alignment == GutterIconRenderer.Alignment.LEFT) {
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x += icon.getIconWidth() + getGapBetweenIcons();
      }
      else if (alignment == GutterIconRenderer.Alignment.CENTER) {
        middleCount++;
        middleSize += icon.getIconWidth() + getGapBetweenIcons();
      } else if (alignment == GutterIconRenderer.Alignment.LINE_NUMBERS) {
        processor.process(getLineNumberAreaOffset() + getLineNumberAreaWidth() - icon.getIconWidth(), y + getTextAlignmentShift(icon), r);
      }
    }

    final int leftSize = x - getIconAreaOffset();

    x = getIconAreaOffset() + myIconsAreaWidth;
    for (GutterMark r : row) {
      if (!checkDumbAware(r)) continue;
      if (((GutterIconRenderer)r).getAlignment() == GutterIconRenderer.Alignment.RIGHT) {
        Icon icon = scaleIcon(r.getIcon());
        x -= icon.getIconWidth();
        processor.process(x, y + getTextAlignmentShift(icon), r);
        x -= getGapBetweenIcons();
      }
    }

    int rightSize = myIconsAreaWidth + getIconAreaOffset() - x + 1;

    if (middleCount > 0) {
      middleSize -= getGapBetweenIcons();
      x = getIconAreaOffset() + leftSize + (myIconsAreaWidth - leftSize - rightSize - middleSize) / 2;
      for (GutterMark r : row) {
        if (!checkDumbAware(r)) continue;
        if (((GutterIconRenderer)r).getAlignment() == GutterIconRenderer.Alignment.CENTER) {
          Icon icon = scaleIcon(r.getIcon());
          processor.process(x, y + getTextAlignmentShift(icon), r);
          x += icon.getIconWidth() + getGapBetweenIcons();
        }
      }
    }
  }

  private int getTextAlignmentShiftForInlayIcon(Icon icon, Inlay<?> inlay) {
    return Math.min(getTextAlignmentShift(icon), inlay.getHeightInPixels() - icon.getIconHeight());
  }

  private int getTextAlignmentShift(Icon icon) {
    int centerRelative = (myEditor.getLineHeight() - icon.getIconHeight()) / 2;
    int baselineRelative = myEditor.getAscent() - icon.getIconHeight();
    return Math.max(centerRelative, baselineRelative);
  }

  private Color getOutlineColor(boolean isActive) {
    ColorKey key = isActive ? EditorColors.SELECTED_TEARLINE_COLOR : EditorColors.TEARLINE_COLOR;
    Color color = myEditor.getColorsScheme().getColor(key);
    return color != null ? color : JBColor.black;
  }

  @Override
  public void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider) {
    myTextAnnotationGutters.add(provider);
    myTextAnnotationGutterSizes.add(0);
    updateSize();
  }

  @Override
  public void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider, @NotNull EditorGutterAction action) {
    myTextAnnotationGutters.add(provider);
    myProviderToListener.put(provider, action);
    myTextAnnotationGutterSizes.add(0);
    updateSize();
  }

  @NotNull
  @Override
  public List<TextAnnotationGutterProvider> getTextAnnotations() {
    return new ArrayList<>(myTextAnnotationGutters);
  }

  private void doPaintFoldingTree(@NotNull Graphics2D g, @NotNull Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    final double width = getFoldingAnchorWidth2D();

    Collection<DisplayedFoldingAnchor> anchorsToDisplay =
      myAnchorsDisplayStrategy.getAnchorsToDisplay(firstVisibleOffset, lastVisibleOffset, myActiveFoldRegions);
    for (DisplayedFoldingAnchor anchor : anchorsToDisplay) {
      boolean active = myActiveFoldRegions.contains(anchor.foldRegion);
      if (ExperimentalUI.isNewEditorTabs()) {
        active = myAlphaContext.isVisible();
      }
      drawFoldingAnchor(width, clip, g, anchor.visualLine, anchor.type, active);
    }
  }

  private void paintFoldingLines(final Graphics2D g, final Rectangle clip) {
    if (ExperimentalUI.isNewEditorTabs()) {
      return;
    }
    boolean shown = isFoldingOutlineShown();
    double x = getWhitespaceSeparatorOffset2D();

    if ((shown || myEditor.isInDistractionFreeMode() && Registry.is("editor.distraction.gutter.separator")) && myPaintBackground) {
      g.setColor(getOutlineColor(false));
      LinePainter2D.paint(g, x, clip.y, x, clip.y + clip.height, StrokeType.CENTERED, getStrokeWidth());
    }

    if (!shown) return;

    myActiveFoldRegions.forEach(region -> {
      if (region.isValid() && region.isExpanded()) {
        int foldStart = myEditor.offsetToVisualLine(region.getStartOffset());
        int foldEnd = myEditor.offsetToVisualLine(region.getEndOffset());
        if (foldStart < foldEnd) {
          int startY = getLineCenterY(foldStart);
          int endY = getLineCenterY(foldEnd);
          if (startY <= clip.y + clip.height && endY + 1 + myEditor.getDescent() >= clip.y) {
            g.setColor(getOutlineColor(true));
            LinePainter2D.paint(g, x, startY, x, endY, StrokeType.CENTERED, getStrokeWidth());
          }
        }
      }
    });
  }

  @Override
  public int getWhitespaceSeparatorOffset() {
    return (int)Math.round(getWhitespaceSeparatorOffset2D());
  }

  private double getWhitespaceSeparatorOffset2D() {
    return PaintUtil.alignToInt(getFoldingAreaOffset() + getFoldingAnchorWidth() / 2.,
                                ScaleContext.create(myEditor.getComponent()), RoundingMode.ROUND, null);
  }

  void setActiveFoldRegions(@NotNull List<FoldRegion> activeFoldRegions) {
    if (!myActiveFoldRegions.equals(activeFoldRegions)) {
      myActiveFoldRegions = activeFoldRegions;
      repaint();
    }
  }

  private int getLineCenterY(int line) {
    return myEditor.visualLineToY(line) + myEditor.getLineHeight() / 2;
  }

  private double getFoldAnchorY(int line, double width) {
    return myEditor.visualLineToY(line) + myEditor.getAscent() - width;
  }

  private void drawFoldingAnchor(double width, @NotNull Rectangle clip, @NotNull Graphics2D g, int visualLine,
                                 @NotNull DisplayedFoldingAnchor.Type type, boolean active) {
    double off = width / 4;
    double height = width + off;
    double baseHeight = height - width / 2;
    double y = getFoldAnchorY(visualLine, width);
    double centerX = LinePainter2D.getStrokeCenter(g, getWhitespaceSeparatorOffset2D(), StrokeType.CENTERED, getStrokeWidth());
    double strokeOff = centerX - getWhitespaceSeparatorOffset2D();
    // need to have the same sub-device-pixel offset as centerX for the square_with_plus rect to have equal dev width/height
    double centerY = PaintUtil.alignToInt(y + width / 2, g) + strokeOff;
    switch (type) {
      case COLLAPSED:
      case COLLAPSED_SINGLE_LINE:
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawSquareWithPlusOrMinus(g, centerX, centerY, width, true, active, visualLine);
        }
        break;
      case EXPANDED_SINGLE_LINE:
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawSquareWithPlusOrMinus(g, centerX, centerY, width, false, active, visualLine);
        }
        break;
      case EXPANDED_TOP:
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawDirectedBox(g, centerX, centerY, width, height, baseHeight, active, visualLine);
        }
        break;
      case EXPANDED_BOTTOM:
        y += width;
        if (y - height <= clip.y + clip.height && y >= clip.y) {
          drawDirectedBox(g, centerX, centerY, width, -height, -baseHeight, active, visualLine);
        }
        break;
    }
  }

  private void drawDirectedBox(Graphics2D g,
                               double centerX,
                               double centerY,
                               double width,
                               double height,
                               double baseHeight,
                               boolean active,
                               int visualLine)
  {
    double sw = getStrokeWidth();
    Rectangle2D rect = RectanglePainter2D.align(g,
                                                EnumSet.of(LinePainter2D.Align.CENTER_X, LinePainter2D.Align.CENTER_Y),
                                                centerX, centerY, width, width, StrokeType.CENTERED, sw);

    double x1 = rect.getX();
    double x2 = x1 + rect.getWidth() - 1;
    double y = height > 0 ? rect.getY() : rect.getY() + rect.getHeight() - 1;
    double[] dxPoints = {x1, x1, x2, x2, centerX};
    double[] dyPoints = {y + baseHeight, y, y, y + baseHeight, y + height + (height < 0 ? 1 : 0)};

    if (ExperimentalUI.isNewEditorTabs()) {
      if (height > 0) {
        myAlphaContext.paintWithComposite(g, () -> {
          Icon icon = scaleIcon(UIUtil.getTreeExpandedIcon());
          icon.paintIcon(this, g, (int)dxPoints[0], getFoldingIconY(visualLine, icon));
        });
      }
      return;
    }

    g.setColor(myEditor.getBackgroundColor());
    LinePainter2D.fillPolygon(g, dxPoints, dyPoints, 5, StrokeType.CENTERED_CAPS_SQUARE, sw, RenderingHints.VALUE_ANTIALIAS_ON);

    g.setColor(getOutlineColor(active));
    LinePainter2D.paintPolygon(g, dxPoints, dyPoints, 5, StrokeType.CENTERED_CAPS_SQUARE, sw, RenderingHints.VALUE_ANTIALIAS_ON);

    drawLine(g, false, centerX, centerY, width, sw);
  }

  private void drawLine(Graphics2D g, boolean vertical, double centerX, double centerY, double width, double strokeWidth) {
    double length = width - getSquareInnerOffset(width) * 2;
    Line2D line = LinePainter2D.align(g,
                                      EnumSet.of(LinePainter2D.Align.CENTER_X, LinePainter2D.Align.CENTER_Y),
                                      centerX, centerY, length, vertical, StrokeType.CENTERED, strokeWidth);

    LinePainter2D.paint(g, line, StrokeType.CENTERED, strokeWidth, RenderingHints.VALUE_ANTIALIAS_OFF);
  }

  private void drawSquareWithPlusOrMinus(@NotNull Graphics2D g,
                                         double centerX,
                                         double centerY,
                                         double width,
                                         boolean plus,
                                         boolean active,
                                         int visualLine) {
    double sw = getStrokeWidth();
    Rectangle2D rect = RectanglePainter2D.align(g,
                                                EnumSet.of(LinePainter2D.Align.CENTER_X, LinePainter2D.Align.CENTER_Y),
                                                centerX, centerY, width, width, StrokeType.CENTERED, sw);
    if (ExperimentalUI.isNewEditorTabs()) {
      Icon icon = scaleIcon(UIUtil.getTreeCollapsedIcon());
      icon.paintIcon(this, g, (int)rect.getX(), getFoldingIconY(visualLine, icon));
      return;
    }
    g.setColor(myEditor.getBackgroundColor());
    RectanglePainter2D.FILL.paint(g, rect, null, StrokeType.CENTERED, sw, RenderingHints.VALUE_ANTIALIAS_OFF);

    g.setColor(getOutlineColor(active));
    RectanglePainter2D.DRAW.paint(g, rect, null, StrokeType.CENTERED, sw, RenderingHints.VALUE_ANTIALIAS_OFF);

    drawLine(g, false, centerX, centerY, width, sw);
    if (plus) {
      drawLine(g, true, centerX, centerY, width, sw);
    }
  }

  private int getFoldingIconY(int visualLine, Icon icon) {
    return (int)(myEditor.visualLineToY(visualLine) + (myEditor.getLineHeight() - icon.getIconHeight()) / 2f + 0.5f);
  }

  /**
   * Returns the gap between the sign and the square itself
   */
  private double getSquareInnerOffset(double width) {
    if (ExperimentalUI.isNewEditorTabs()) return 0;
    return Math.max(width / 5, scale(2));
  }

  private double scale(double v) {
    return JBUIScale.scale((float)v) * myEditor.getScale();
  }

  private int getFoldingAnchorWidth() {
    return (int)Math.round(getFoldingAnchorWidth2D());
  }

  private double getFoldingAnchorWidth2D() {
    if (ExperimentalUI.isNewEditorTabs()) {
      return getEditorScaleFactor() * (UIUtil.getTreeCollapsedIcon().getIconWidth() + JBUIScale.scale(6f)) ;
    }
    return Math.min(scale(4f), myEditor.getLineHeight() / 2f - JBUIScale.scale(2f)) * 2;
  }

  private double getStrokeWidth() {
    double sw = JreHiDpiUtil.isJreHiDPIEnabled() || scale(1f) < 2 ? 1 : 2;
    ScaleContext ctx = ScaleContext.create(myEditor.getComponent());
    return PaintUtil.alignToInt(sw, ctx, PaintUtil.devValue(1, ctx) > 2 ? RoundingMode.FLOOR : RoundingMode.ROUND, null);
  }

  private int getFoldingAreaOffset() {
    return myLayout.getFoldingAreaOffset();
  }

  int getFoldingAreaWidth() {
    return isFoldingOutlineShown() ? getFoldingAnchorWidth() + JBUIScale.scale(2) :
           isRealEditor() ? getFoldingAnchorWidth() : 0;
  }

  private boolean isRealEditor() {
    return EditorUtil.isRealFileEditor(myEditor);
  }

  boolean isLineMarkersShown() {
    return myEditor.getSettings().isLineMarkerAreaShown();
  }

  boolean areIconsShown() {
    return myEditor.getSettings().areGutterIconsShown();
  }

  boolean isLineNumbersShown() {
    return myEditor.getSettings().isLineNumbersShown();
  }

  @Override
  public boolean isAnnotationsShown() {
    return !myTextAnnotationGutters.isEmpty();
  }

  private boolean isFoldingOutlineShown() {
    return myEditor.getSettings().isFoldingOutlineShown() &&
           myEditor.getFoldingModel().isFoldingEnabled() &&
           !myEditor.isInPresentationMode();
  }

  static int getGapBetweenAreas() {
    return GAP_BETWEEN_AREAS.get();
  }

  private static int getAreaWidthWithGap(int width) {
    if (width > 0) {
      return width + getGapBetweenAreas();
    }
    return 0;
  }

  private static int getGapBetweenIcons() {
    return GAP_BETWEEN_ICONS.get();
  }

  private static int getGapBetweenAnnotations() {
    return GAP_BETWEEN_ANNOTATIONS.get();
  }

  int getLineNumberAreaWidth() {
    if (isLineNumbersShown()) {
      return myLineNumberAreaWidth + getAreaWidthWithGap(myAdditionalLineNumberAreaWidth);
    }
    if (ExperimentalUI.isNewUI() && isRealEditor()) {
      //todo[kb] recalculate gutters renderers and return 0 if there are none in EditorMouseEventArea.LINE_NUMBERS_AREA
      return 14;
    }
    return 0;
  }

  private int getLineMarkerAreaWidth() {
    return isLineMarkersShown() ? getLeftFreePaintersAreaWidth() + myIconsAreaWidth +
                                  getGapAfterIconsArea() + getRightFreePaintersAreaWidth() : 0;
  }

  private void calcLineNumberAreaWidth() {
    if (!isLineNumbersShown()) return;

    Integer maxLineNumber = myLineNumberConverter.getMaxLineNumber(myEditor);
    myLineNumberAreaWidth = Math.max(getInitialLineNumberWidth(), maxLineNumber == null ? 0 : calcLineNumbersAreaWidth(maxLineNumber));

    myAdditionalLineNumberAreaWidth = 0;
    if (myAdditionalLineNumberConverter != null) {
      Integer maxAdditionalLineNumber = myAdditionalLineNumberConverter.getMaxLineNumber(myEditor);
      myAdditionalLineNumberAreaWidth = maxAdditionalLineNumber == null ? 0 : calcLineNumbersAreaWidth(maxAdditionalLineNumber);
    }
  }

  @Nullable
  EditorMouseEventArea getEditorMouseAreaByOffset(int offset) {
    return myLayout.getEditorMouseAreaByOffset(offset);
  }

  int getLineNumberAreaOffset() {
    return myLayout.getLineNumberAreaOffset();
  }

  @Override
  public int getAnnotationsAreaOffset() {
    return myLayout.getAnnotationsAreaOffset();
  }

  @Override
  public int getAnnotationsAreaWidth() {
    return myTextAnnotationGuttersSize;
  }

  private int getAnnotationsAreaWidthEx() {
    return myTextAnnotationGuttersSize + myTextAnnotationExtraSize;
  }

  @Override
  public int getLineMarkerAreaOffset() {
    return myLayout.getLineMarkerAreaOffset();
  }

  @Override
  public int getIconAreaOffset() {
    return myLayout.getIconAreaOffset();
  }

  private int getLeftFreePaintersAreaOffset() {
    return getLineMarkerAreaOffset();
  }

  @Override
  public int getLineMarkerFreePaintersAreaOffset() {
    return myLayout.getLineMarkerFreePaintersAreaOffset();
  }

  int getLeftFreePaintersAreaWidth() {
    if (!myLeftFreePaintersAreaShown) return 0;
    if (myForcedLeftFreePaintersAreaWidth >= 0) return myForcedLeftFreePaintersAreaWidth;

    return FREE_PAINTERS_LEFT_AREA_WIDTH.get();
  }

  int getRightFreePaintersAreaWidth() {
    int width = myRightFreePaintersAreaShown ? myForcedRightFreePaintersAreaWidth < 0 ? FREE_PAINTERS_RIGHT_AREA_WIDTH.get()
                                                                                  : myForcedRightFreePaintersAreaWidth
                                         : 0;
    if (ExperimentalUI.isNewEditorTabs()) {
      if (width == 0) return 0;
      return Math.max(FREE_PAINTERS_RIGHT_AREA_WIDTH.get(), JBUI.getInt("Gutter.VcsChanges.width", 3));
    }
    return width;
  }

  @Override
  public int getIconsAreaWidth() {
    return myIconsAreaWidth;
  }

  int getGapAfterIconsArea() {
    return isRealEditor() && areIconsShown() ? getGapBetweenAreas() : 0;
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() != EditorEx.VERTICAL_SCROLLBAR_RIGHT;
  }

  @Nullable
  private AffineTransform setMirrorTransformIfNeeded(Graphics2D g, int offset, int width) {
    if (isMirrored()) {
      AffineTransform old = g.getTransform();
      AffineTransform transform = new AffineTransform(old);

      transform.scale(-1, 1);
      transform.translate(-offset * 2 - width, 0);
      g.setTransform(transform);
      return old;
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public FoldRegion findFoldingAnchorAt(int x, int y) {
    if (!myEditor.getSettings().isFoldingOutlineShown()) return null;

    int anchorX = getFoldingAreaOffset();
    int anchorWidth = getFoldingAnchorWidth();

    int visualLine = myEditor.yToVisualLine(y);
    int neighbourhoodStartOffset = myEditor.visualPositionToOffset(new VisualPosition(visualLine, 0));
    int neighbourhoodEndOffset = myEditor.visualPositionToOffset(new VisualPosition(visualLine, Integer.MAX_VALUE));

    Collection<DisplayedFoldingAnchor> displayedAnchors = myAnchorsDisplayStrategy.getAnchorsToDisplay(neighbourhoodStartOffset,
                                                                                                       neighbourhoodEndOffset,
                                                                                                       Collections.emptyList());
    x = convertX(x);
    for (DisplayedFoldingAnchor anchor : displayedAnchors) {
      Rectangle r = rectangleByFoldOffset(anchor.visualLine, anchorWidth, anchorX);
      if (r.x < x && x <= r.x + r.width && r.y < y && y <= r.y + r.height) return anchor.foldRegion;
    }

    return null;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private Rectangle rectangleByFoldOffset(int foldStart, int anchorWidth, int anchorX) {
    return new Rectangle(anchorX, (int)getFoldAnchorY(foldStart, anchorWidth), anchorWidth, anchorWidth);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    TooltipController.getInstance().cancelTooltips();
  }

  @Override
  public void mouseMoved(final MouseEvent e) {
    Point point = e.getPoint();
    PointInfo pointInfo = getPointInfo(point);
    if (pointInfo == null) {
      TextAnnotationGutterProvider provider = getProviderAtPoint(point);
      String toolTip = null;
      if (provider == null) {
        ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
        if (lineRenderer != null) {
          toolTip = lineRenderer.getTooltipText();
        }
      }
      else {
        final int line = getLineNumAtPoint(point);
        if (line >= 0) {
          toolTip = provider.getToolTip(line, myEditor);
          if (!Objects.equals(toolTip, myLastGutterToolTip)) {
            TooltipController.getInstance().cancelTooltip(GUTTER_TOOLTIP_GROUP, e, true);
            myLastGutterToolTip = toolTip;
          }
        }
      }
      showToolTip(toolTip, point, Balloon.Position.below);
    }
    else {
      computeTooltipInBackground(pointInfo);
    }
  }

  private GutterIconRenderer myCalculatingInBackground;
  private ProgressIndicator myBackgroundIndicator = new EmptyProgressIndicator();
  private void computeTooltipInBackground(@NotNull PointInfo pointInfo) {
    GutterIconRenderer renderer = pointInfo.renderer;
    if (myCalculatingInBackground == renderer && !myBackgroundIndicator.isCanceled()) return; // not yet calculated
    myCalculatingInBackground = renderer;
    myBackgroundIndicator.cancel();
    myBackgroundIndicator = new ProgressIndicatorBase();
    myBackgroundIndicator.setModalityProgress(null);
    Point point = pointInfo.iconCenterPosition;
    Balloon.Position relativePosition = pointInfo.renderersInLine > 1 && pointInfo.rendererPosition == 0 ? Balloon.Position.below
                                                                                                         : Balloon.Position.atRight;
    AtomicReference<@NlsContexts.Tooltip String> tooltip = new AtomicReference<>();
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(new Task.Backgroundable(myEditor.getProject(), IdeBundle.message("progress.title.constructing.tooltip")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Callable<@NlsContexts.Tooltip String> callable = () -> renderer.getTooltipText();
        tooltip.set(ReadAction.nonBlocking(callable).wrapProgress(indicator).executeSynchronously());
      }

      @Override
      public void onSuccess() {
        showToolTip(tooltip.get(), point, relativePosition);
      }
    }, myBackgroundIndicator);
  }

  void showToolTip(@Nullable @NlsContexts.Tooltip String toolTip, @NotNull Point location, @NotNull Balloon.Position relativePosition) {
    myCalculatingInBackground = null;
    TooltipController controller = TooltipController.getInstance();
    if (toolTip == null || toolTip.isEmpty() || myEditor.isDisposed()) {
      controller.cancelTooltip(GUTTER_TOOLTIP_GROUP, null, false);
    }
    else {
      RelativePoint showPoint = new RelativePoint(this, location);
      TooltipRenderer tr =
        ((EditorMarkupModel)myEditor.getMarkupModel()).getErrorStripTooltipRendererProvider().calcTooltipRenderer(toolTip);
      HintHint hint =
        new HintHint(this, location)
          .setAwtTooltip(true)
          .setPreferredPosition(relativePosition)
          .setRequestFocus(ScreenReader.isActive());
      if (myEditor.getComponent().getRootPane() != null) {
        controller.showTooltipByMouseMove(myEditor, showPoint, tr, false, GUTTER_TOOLTIP_GROUP, hint);
      }
    }
  }

  void resetMousePointer() {
    if (IdeGlassPaneImpl.hasPreProcessedCursor(this)) return;

    UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  void validateMousePointer(@NotNull MouseEvent e) {
    if (IdeGlassPaneImpl.hasPreProcessedCursor(this)) return;

    FoldRegion foldingAtCursor = findFoldingAnchorAt(e.getX(), e.getY());
    setActiveFoldRegions(getGroupRegions(foldingAtCursor));
    Cursor cursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
    if (foldingAtCursor != null) {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }
    GutterIconRenderer renderer = getGutterRenderer(e);
    if (renderer != null) {
      if (renderer.isNavigateAction()) {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
      else {
        TextAnnotationGutterProvider provider = getProviderAtPoint(e.getPoint());
        if (provider != null) {
          EditorGutterAction action = myProviderToListener.get(provider);
          if (action != null) {
            int line = getLineNumAtPoint(e.getPoint());
            if (line >= 0) {
              cursor = action.getCursor(line);
            }
          }
        }
      }
    }
    UIUtil.setCursor(this, cursor);
  }

  @NotNull
  private List<FoldRegion> getGroupRegions(@Nullable FoldRegion foldingAtCursor) {
    if (foldingAtCursor == null) {
      return Collections.emptyList();
    }
    else {
      FoldingGroup group = foldingAtCursor.getGroup();
      if (group == null) {
        return Collections.singletonList(foldingAtCursor);
      }
      return myEditor.getFoldingModel().getGroupedRegions(group);
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    if (e.isPopupTrigger()) {
      try {
        invokePopup(e);
      }
      finally {
        removeLoadingIconForGutterMark();
      }
    }
  }

  private void fireEventToTextAnnotationListeners(final MouseEvent e) {
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      final Point clickPoint = e.getPoint();

      final TextAnnotationGutterProvider provider = getProviderAtPoint(clickPoint);

      if (provider == null) {
        return;
      }

      EditorGutterAction action = myProviderToListener.get(provider);
      if (action != null) {
        int line = getLineNumAtPoint(clickPoint);

        if (line >= 0 && line < myEditor.getDocument().getLineCount() && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)) {
          UIEventLogger.EditorAnnotationClicked.log(myEditor.getProject(), provider.getClass());
          action.doAction(line);
        }
      }
    }
  }

  private int getLineNumAtPoint(final Point clickPoint) {
    return EditorUtil.yToLogicalLineNoCustomRenderers(myEditor, clickPoint.y);
  }

  @Nullable
  private TextAnnotationGutterProvider getProviderAtPoint(final Point clickPoint) {
    int current = getAnnotationsAreaOffset();
    if (clickPoint.x < current) return null;
    for (int i = 0; i < myTextAnnotationGutterSizes.size(); i++) {
      current += myTextAnnotationGutterSizes.getInt(i);
      if (clickPoint.x <= current) return myTextAnnotationGutters.get(i);
    }

    return null;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (e.isPopupTrigger() || isPopupAction(e)) {
      try {
        invokePopup(e);
      }
      finally {
        removeLoadingIconForGutterMark();
      }
    }
    else if (UIUtil.isCloseClick(e)) {
      processClose(e);
    }
  }

  private boolean isPopupAction(MouseEvent e) {
    GutterIconRenderer renderer = getGutterRenderer(e);
    return renderer != null && renderer.getClickAction() == null && renderer.getPopupMenuActions() != null;
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    try {
      if (e.isPopupTrigger()) {
        invokePopup(e);
      }
      else {
        invokeGutterAction(e);
      }
    }
    finally {
      removeLoadingIconForGutterMark();
    }
  }

  private void invokeGutterAction(MouseEvent e) {
    PointInfo info = getPointInfo(e.getPoint());
    GutterIconRenderer renderer = info == null ? null : info.renderer;
    AnAction clickAction = null;
    if (renderer != null && e.getButton() < 4) {
      clickAction = BitUtil.isSet(e.getModifiers(), InputEvent.BUTTON2_MASK)
                    ? renderer.getMiddleButtonClickAction()
                    : renderer.getClickAction();
    }
    if (clickAction != null) {
      myLastActionableClick = new ClickInfo(EditorUtil.yPositionToLogicalLine(myEditor, e), info.iconCenterPosition);
      logGutterIconClick(renderer);

      e.consume();
      performAction(clickAction, e, ActionPlaces.EDITOR_GUTTER, DataManager.getInstance().getDataContext(this), info);
      repaint();
    }
    else {
      ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
      if (lineRenderer != null) {
        lineRenderer.doAction(myEditor, e);
      }
      else {
        fireEventToTextAnnotationListeners(e);
      }
    }
  }

  private void logGutterIconClick(@NotNull GutterIconRenderer renderer) {
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(renderer.getClass());
    Project project = myEditor.getProject();
    Language language = null;
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument());
      if (file != null) {
        language = file.getLanguage();
      }
    }

    GutterIconClickCollectors.CLICKED.log(project, language, renderer.getFeatureId(), pluginInfo);
  }

  private boolean isDumbMode() {
    Project project = myEditor.getProject();
    return project != null && !project.isDisposed() && DumbService.isDumb(project);
  }

  private boolean checkDumbAware(@NotNull Object possiblyDumbAware) {
    return !isDumbMode() || DumbService.isDumbAware(possiblyDumbAware);
  }

  private void notifyNotDumbAware() {
    Project project = myEditor.getProject();
    if (project != null) {
      DumbService.getInstance(project).showDumbModeNotification(
        IdeBundle.message("message.this.functionality.is.not.available.during.indexing"));
    }
  }

  private void performAction(@NotNull AnAction action,
                             @NotNull InputEvent e,
                             @NotNull String place,
                             @NotNull DataContext context,
                             @NotNull PointInfo info) {
    if (!checkDumbAware(action)) {
      notifyNotDumbAware();
      return;
    }
    addLoadingIconForGutterMark(info);

    AnActionEvent actionEvent = AnActionEvent.createFromAnAction(action, e, place, context);
    if (ActionUtil.lastUpdateAndCheckDumb(action, actionEvent, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, actionEvent);
    }
  }

  @Override
  public @Nullable Runnable setLoadingIconForCurrentGutterMark() {
    EDT.assertIsEdt();
    if (myLastActionableClick == null || myLastActionableClick.myProgressRemover == null) {
      return null;
    }
    Runnable remover = myLastActionableClick.myProgressRemover;
    myLastActionableClick.myProgressRemover = null;
    return remover;
  }

  @Nullable
  private ActiveGutterRenderer getActiveRendererByMouseEvent(final MouseEvent e) {
    if (findFoldingAnchorAt(e.getX(), e.getY()) != null) {
      return null;
    }
    if (e.isConsumed() || e.getX() > getWhitespaceSeparatorOffset()) {
      return null;
    }
    final ActiveGutterRenderer[] gutterRenderer = {null};
    final int[] layer = {-1};
    Rectangle clip = myEditor.getScrollingModel().getVisibleArea();
    int firstVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y - myEditor.getLineHeight())));
    int lastVisibleOffset = myEditor.logicalPositionToOffset(
      myEditor.xyToLogicalPosition(new Point(0, clip.y + clip.height + myEditor.getLineHeight())));

    processRangeHighlighters(firstVisibleOffset, lastVisibleOffset, highlighter -> {
      LineMarkerRenderer renderer = highlighter.getLineMarkerRenderer();
      if (renderer == null) return;
      if (gutterRenderer[0] != null && layer[0] >= highlighter.getLayer()) return;
      Rectangle rectangle = getLineRendererRectangle(highlighter);
      if (rectangle == null) return;

      int startY = rectangle.y;
      int endY = startY + rectangle.height;
      if (startY == endY) {
        endY += myEditor.getLineHeight();
      }

      if (startY < e.getY() &&
          e.getY() <= endY &&
          renderer instanceof ActiveGutterRenderer &&
          ((ActiveGutterRenderer)renderer).canDoAction(myEditor, e)) {
        gutterRenderer[0] = (ActiveGutterRenderer)renderer;
        layer[0] = highlighter.getLayer();
      }
    });
    return gutterRenderer[0];
  }

  @Override
  public void closeAllAnnotations() {
    closeTextAnnotations(myTextAnnotationGutters);
  }

  @Override
  public void closeTextAnnotations(@NotNull Collection<? extends TextAnnotationGutterProvider> annotations) {
    if (!myCanCloseAnnotations) return;

    ReferenceOpenHashSet<TextAnnotationGutterProvider> toClose = new ReferenceOpenHashSet<>(annotations);
    for (int i = myTextAnnotationGutters.size() - 1; i >= 0; i--) {
      TextAnnotationGutterProvider provider = myTextAnnotationGutters.get(i);
      if (toClose.contains(provider)) {
        provider.gutterClosed();
        myTextAnnotationGutters.remove(i);
        myTextAnnotationGutterSizes.removeInt(i);
        myProviderToListener.remove(provider);
      }
    }

    updateSize();
  }

  private class CloseAnnotationsAction extends DumbAwareAction {
    CloseAnnotationsAction() {
      super(EditorBundle.messagePointer("close.editor.annotations.action.name"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      closeAllAnnotations();
    }
  }

  @Override
  @Nullable
  public Point getCenterPoint(final GutterIconRenderer renderer) {
    if (!areIconsShown()) {
      for (Int2ObjectMap.Entry<List<GutterMark>> entry : processGutterRenderers()) {
        if (ContainerUtil.find(entry.getValue(), renderer) != null) {
          return new Point(getIconAreaOffset(), getLineCenterY(entry.getIntKey()));
        }
      }
    }
    else {
      Ref<Point> result = Ref.create();
      for (Int2ObjectMap.Entry<List<GutterMark>> entry : processGutterRenderers()) {
        processIconsRow(entry.getIntKey(), entry.getValue(), (x, y, r) -> {
          if (result.isNull() && r.equals(renderer)) {
            Icon icon = scaleIcon(r.getIcon());
            result.set(new Point(x + icon.getIconWidth() / 2, y + icon.getIconHeight() / 2));
          }
        });
        if (!result.isNull()) {
          return result.get();
        }
      }
    }
    return null;
  }

  @Override
  public void setLineNumberConverter(@NotNull LineNumberConverter primaryConverter, @Nullable LineNumberConverter additionalConverter) {
    myLineNumberConverter = primaryConverter;
    myAdditionalLineNumberConverter = additionalConverter;
    repaint();
  }

  @Override
  public void setShowDefaultGutterPopup(boolean show) {
    myShowDefaultGutterPopup = show;
  }

  @Override
  public void setCanCloseAnnotations(boolean canCloseAnnotations) {
    myCanCloseAnnotations = canCloseAnnotations;
  }

  @Override
  public void setGutterPopupGroup(@Nullable ActionGroup group) {
    myCustomGutterPopupGroup = group;
  }

  @Override
  public boolean isPaintBackground() {
    return myPaintBackground;
  }

  @Override
  public void setPaintBackground(boolean value) {
    myPaintBackground = value;
  }

  @Override
  public void setForceShowLeftFreePaintersArea(boolean value) {
    myForceLeftFreePaintersAreaShown = value;
  }

  @Override
  public void setForceShowRightFreePaintersArea(boolean value) {
    myForceRightFreePaintersAreaShown = value;
  }

  @Override
  public void setLeftFreePaintersAreaWidth(int widthInPixels) {
    if (widthInPixels < 0 || widthInPixels > Short.MAX_VALUE) throw new IllegalArgumentException();
    myForcedLeftFreePaintersAreaWidth = (short)widthInPixels;
  }

  @Override
  public void setRightFreePaintersAreaWidth(int widthInPixels) {
    if (widthInPixels < 0 || widthInPixels > Short.MAX_VALUE) throw new IllegalArgumentException();
    myForcedRightFreePaintersAreaWidth = (short)widthInPixels;
  }

  @Override
  public void setInitialIconAreaWidth(int width) {
    myStartIconAreaWidth = width;
  }

  private void invokePopup(MouseEvent e) {
    int logicalLineAtCursor = EditorUtil.yPositionToLogicalLine(myEditor, e);
    Point point = e.getPoint();
    PointInfo info = getPointInfo(point);
    if (info != null) {
      logGutterIconClick(info.renderer);
    }
    myLastActionableClick = new ClickInfo(logicalLineAtCursor, info == null ? point : info.iconCenterPosition);
    final ActionManager actionManager = ActionManager.getInstance();
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      final List<AnAction> addActions = new ArrayList<>();
      if (myCanCloseAnnotations) addActions.add(new CloseAnnotationsAction());
      //if (line >= myEditor.getDocument().getLineCount()) return;

      for (TextAnnotationGutterProvider gutterProvider : myTextAnnotationGutters) {
        final List<AnAction> list = gutterProvider.getPopupActions(logicalLineAtCursor, myEditor);
        if (list != null) {
          for (AnAction action : list) {
            if (! addActions.contains(action)) {
              addActions.add(action);
            }
          }
        }
      }
      if (!addActions.isEmpty()) {
        e.consume();
        DefaultActionGroup actionGroup = DefaultActionGroup.createPopupGroup(EditorBundle.messagePointer("editor.annotations.action.group.name"));
        for (AnAction addAction : addActions) {
          actionGroup.add(addAction);
        }
        JPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_ANNOTATIONS_AREA_POPUP, actionGroup).getComponent();
        menu.show(this, e.getX(), e.getY());
      }
    }
    else {
      if (info != null) {
        AnAction rightButtonAction = info.renderer.getRightButtonClickAction();
        if (rightButtonAction != null) {
          e.consume();
          performAction(rightButtonAction, e, ActionPlaces.EDITOR_GUTTER_POPUP, myEditor.getDataContext(), info);
        }
        else {
          ActionGroup actionGroup = info.renderer.getPopupMenuActions();
          if (actionGroup != null) {
            e.consume();
            if (checkDumbAware(actionGroup)) {
              addLoadingIconForGutterMark(info);
              actionManager.createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, actionGroup).getComponent().show(this, e.getX(), e.getY());
            }
            else {
              notifyNotDumbAware();
            }
          }
        }
      }
      else {
        ActionGroup group = myCustomGutterPopupGroup;
        if (group == null && myShowDefaultGutterPopup) {
          group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_GUTTER);
        }
        if (group != null) {
          e.consume();
          ActionPopupMenu popupMenu = actionManager.createActionPopupMenu(ActionPlaces.EDITOR_GUTTER_POPUP, group);
          popupMenu.getComponent().show(this, e.getX(), e.getY());
        }
      }
    }
  }

  private void addLoadingIconForGutterMark(@NotNull PointInfo info) {
    ClickInfo clickInfo = myLastActionableClick;
    if (clickInfo == null) return;
    boolean[] removed = { false };
    EdtScheduledExecutorService.getInstance().schedule(() -> {
      if (myLastActionableClick != clickInfo || removed[0]) return;
      clickInfo.myProgressVisualLine = info.visualLine;
      clickInfo.myProgressGutterMark = info.renderer;
      repaint();
    }, Registry.intValue("actionSystem.popup.progress.icon.delay", 500), TimeUnit.MILLISECONDS);
    myLastActionableClick.myProgressRemover = () -> {
      EDT.assertIsEdt();
      removed[0] = true;
      if (myLastActionableClick == clickInfo) {
        clickInfo.myProgressVisualLine = -1;
        clickInfo.myProgressGutterMark = null;
        repaint();
      }
    };
  }

  private void removeLoadingIconForGutterMark() {
    Runnable remover = myLastActionableClick == null ? null : myLastActionableClick.myProgressRemover;
    if (remover == null) return;
    myLastActionableClick.myProgressRemover = null;
    remover.run();
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
    TooltipController.getInstance().cancelTooltip(GUTTER_TOOLTIP_GROUP, e, false);
  }

  private int convertPointToLineNumber(final Point p) {
    DocumentEx document = myEditor.getDocument();
    int line = EditorUtil.yPositionToLogicalLine(myEditor, p);
    if (!isValidLine(document, line)) return -1;

    int startOffset = document.getLineStartOffset(line);
    final FoldRegion region = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    if (region != null) {
      return document.getLineNumber(region.getEndOffset());
    }
    return line;
  }

  @Override
  @Nullable
  public GutterIconRenderer getGutterRenderer(final Point p) {
    PointInfo info = getPointInfo(p);
    return info == null ? null : info.renderer;
  }

  @Nullable
  private PointInfo getPointInfo(@NotNull Point p) {
    int cX = convertX((int)p.getX());
    int line = myEditor.yToVisualLine(p.y);
    int[] yRange = myEditor.visualLineToYRange(line);
    if (p.y >= yRange[0] && p.y < yRange[0] + myEditor.getLineHeight()) {
      List<GutterMark> renderers = getGutterRenderers(line);
      final PointInfo[] result = {null};
      Int2IntRBTreeMap xPos = new Int2IntRBTreeMap();
      processIconsRowForY(yRange[0], renderers, (x, y, renderer) -> {
        Icon icon = scaleIcon(renderer.getIcon());
        int iconWidth = icon.getIconWidth();
        int centerX = x + iconWidth / 2;
        xPos.put(x, centerX);
        if (x <= cX && cX <= x + iconWidth) {
          int iconHeight = icon.getIconHeight();
          result[0] = new PointInfo((GutterIconRenderer)renderer, new Point(centerX, y + iconHeight / 2));
        }
      });
      if (result[0] != null) {
        result[0].renderersInLine = xPos.size();
        result[0].rendererPosition = new ArrayList<>(xPos.values()).indexOf(result[0].iconCenterPosition.x);
        result[0].visualLine = line;
      }
      return result[0];
    }
    if (myHasInlaysWithGutterIcons) {
      if (p.y < yRange[0]) {
        List<Inlay<?>> inlays = myEditor.getInlayModel().getBlockElementsForVisualLine(line, true);
        int yDiff = yRange[0] - p.y;
        for (int i = inlays.size() - 1; i >= 0; i--) {
          Inlay<?> inlay = inlays.get(i);
          int height = inlay.getHeightInPixels();
          if (yDiff <= height) {
            return getPointInfo(inlay, p.y + yDiff - height, cX, p.y);
          }
          yDiff -= height;
        }
      }
      else if (p.y >= yRange[1]) {
        List<Inlay<?>> inlays = myEditor.getInlayModel().getBlockElementsForVisualLine(line, false);
        int yDiff = p.y - yRange[1];
        for (Inlay<?> inlay : inlays) {
          int height = inlay.getHeightInPixels();
          if (yDiff < height) {
            return getPointInfo(inlay, p.y - yDiff, cX, p.y);
          }
          yDiff -= height;
        }
      }
    }
    return null;
  }

  @Nullable
  private PointInfo getPointInfo(@NotNull Inlay<?> inlay, int inlayY, int x, int y) {
    GutterIconRenderer renderer = inlay.getGutterIconRenderer();
    if (!shouldBeShown(renderer) || !checkDumbAware(renderer)) return null;
    Icon icon = scaleIcon(renderer.getIcon());
    int iconHeight = icon.getIconHeight();
    if ((y - inlayY) >= Math.max(iconHeight, myEditor.getLineHeight()) || iconHeight > inlay.getHeightInPixels()) return null;
    int iconWidth = icon.getIconWidth();
    int rightX = getIconAreaOffset() + getIconsAreaWidth();
    if (x < rightX - iconWidth || x > rightX) return null;
    PointInfo pointInfo = new PointInfo(renderer, new Point(rightX - iconWidth / 2,
                                                            inlayY + getTextAlignmentShiftForInlayIcon(icon, inlay) + iconHeight / 2));
    pointInfo.renderersInLine = 1;
    return pointInfo;
  }

  @Nullable
  private GutterIconRenderer getGutterRenderer(final MouseEvent e) {
    return getGutterRenderer(e.getPoint());
  }

  @NotNull
  static LineMarkerRendererEx.Position getLineMarkerPosition(@NotNull LineMarkerRenderer renderer) {
    if (renderer instanceof LineMarkerRendererEx) {
      return ((LineMarkerRendererEx)renderer).getPosition();
    }
    return LineMarkerRendererEx.Position.RIGHT;
  }

  int convertX(int x) {
    if (!isMirrored()) return x;
    return getWidth() - x;
  }

  public void dispose() {
    for (TextAnnotationGutterProvider gutterProvider : myTextAnnotationGutters) {
      gutterProvider.gutterClosed();
    }
    myProviderToListener.clear();
  }

  @Override
  public boolean isFocusable() {
    return ScreenReader.isActive();
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJComponent() {};
    }
    return accessibleContext;
  }

  void setCurrentAccessibleLine(@Nullable AccessibleGutterLine line) {
    myAccessibleGutterLine = line;
  }

  @Nullable
  AccessibleGutterLine getCurrentAccessibleLine() {
    return myAccessibleGutterLine;
  }

  void escapeCurrentAccessibleLine() {
    if (myAccessibleGutterLine != null) {
      myAccessibleGutterLine.escape(true);
    }
  }

  private static int getInitialLineNumberWidth() {
    if (ExperimentalUI.isNewUI()) {
      //have a placeholder for breakpoints
      return 24;
    }
    return 0;
  }

  private static final class ClickInfo {
    final int myLogicalLineAtCursor;
    final Point myIconCenterPosition;
    int myProgressVisualLine;
    GutterMark myProgressGutterMark;
    Runnable myProgressRemover;

    ClickInfo(int logicalLineAtCursor, Point iconCenterPosition) {
      myLogicalLineAtCursor = logicalLineAtCursor;
      myIconCenterPosition = iconCenterPosition;
    }
  }

  private static final class PointInfo {
    final @NotNull GutterIconRenderer renderer;
    final @NotNull Point iconCenterPosition;
    int renderersInLine;
    int rendererPosition;
    int visualLine;

    PointInfo(@NotNull GutterIconRenderer renderer, @NotNull Point iconCenterPosition) {
      this.renderer = renderer;
      this.iconCenterPosition = iconCenterPosition;
    }
  }

  private static final HoverStateListener HOVER_STATE_LISTENER = new HoverStateListener() {
    @Override
    protected void hoverChanged(@NotNull Component component, boolean hovered) {
      if (component instanceof EditorGutterComponentImpl && ExperimentalUI.isNewEditorTabs()) {
        EditorGutterComponentImpl gutter = (EditorGutterComponentImpl)component;
        gutter.myAlphaContext.setVisible(hovered);
      }
    }
  };

  private static final class GutterIconClickCollectors extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("gutter.icon.click", 3);
    private static final EventId3<Language, String, PluginInfo> CLICKED =
      GROUP.registerEvent("clicked",
                          EventFields.Language,
                          EventFields.StringValidatedByCustomRule("icon_id", "gutter_icon"),
                          EventFields.PluginInfo);

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }
  }
}
