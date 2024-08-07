// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.NonHideableIconGutterMark;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.DistractionFreeModeController;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDImage;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.ide.dnd.DnDSupport;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorPreciseContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.internal.statistic.collectors.fus.PluginInfoValidationRule;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.BooleanEventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.event.EditorGutterHoverEvent;
import com.intellij.openapi.editor.impl.stickyLines.ui.StickyLineComponent;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.impl.view.VisualLinesIterator;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.impl.EditorCompositeKt;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
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
import com.intellij.util.*;
import com.intellij.util.animation.AlphaAnimationContext;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.JBValue.JBValueGroup;
import com.intellij.util.ui.accessibility.ScreenReader;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterable;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static com.intellij.openapi.ui.ex.lineNumber.LineNumberConvertersKt.getStandardLineNumberConverter;

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
 * </ul>
 */
@DirtyUI
final class EditorGutterComponentImpl extends EditorGutterComponentEx
  implements MouseListener, MouseMotionListener, UiCompatibleDataProvider,
             Accessible, UiInspectorPreciseContextProvider {

  static final String DISTRACTION_FREE_MARGIN = "editor.distraction.free.margin";
  private static final Logger LOG = Logger.getInstance(EditorGutterComponentImpl.class);

  private static final JBValueGroup JBVG = new JBValueGroup();
  static final JBValue START_ICON_AREA_WIDTH = JBVG.value(17);
  private static final JBValue FREE_PAINTERS_EXTRA_LEFT_AREA_WIDTH = JBVG.value(8); // to the left of the line numbers in the new UI
  private static final JBValue FREE_PAINTERS_LEFT_AREA_WIDTH = JBVG.value(8);
  private static final JBValue FREE_PAINTERS_RIGHT_AREA_WIDTH = JBVG.value(5);
  private static final JBValue GAP_BETWEEN_ICONS = JBVG.value(3);
  private static final JBValue GAP_BETWEEN_AREAS = JBVG.value(5);
  private static final JBValue GAP_BETWEEN_ANNOTATIONS = JBVG.value(5);
  static final JBValue EMPTY_ANNOTATION_AREA_WIDTH = JBVG.value(() -> (float)JBUI.CurrentTheme.Editor.Gutter.emptyAnnotationAreaWidth());
  static final JBValue GAP_AFTER_VCS_MARKERS_WIDTH = JBVG.value(() -> (float)JBUI.CurrentTheme.Editor.Gutter.gapAfterVcsMarkersWidth());
  static final JBValue GAP_AFTER_LINE_NUMBERS_WIDTH = JBVG.value(() -> (float)JBUI.CurrentTheme.Editor.Gutter.gapAfterLineNumbersWidth());
  private static final JBValue GAP_AFTER_ICONS_WIDTH = JBVG.value(() -> (float)JBUI.CurrentTheme.Editor.Gutter.gapAfterIconsWidth());
  private static final TooltipGroup GUTTER_TOOLTIP_GROUP = new TooltipGroup("GUTTER_TOOLTIP_GROUP", 0);

  private ClickInfo myLastActionableClick;
  private final @NotNull EditorImpl myEditor;
  private final FoldingAnchorsOverlayStrategy myAnchorDisplayStrategy;
  private @Nullable Int2ObjectMap<List<GutterMark>> myLineToGutterRenderers;
  private boolean myLineToGutterRenderersCacheForLogicalLines;
  private boolean myHasInlaysWithGutterIcons;
  private int myStartIconAreaWidth = ExperimentalUI.isNewUI() ? 0 : START_ICON_AREA_WIDTH.get();
  private int myIconsAreaWidth;
  int myLineNumberAreaWidth = getInitialLineNumberWidth();
  int myAdditionalLineNumberAreaWidth;
  private @NotNull List<FoldRegion> myActiveFoldRegions = Collections.emptyList();
  int myTextAnnotationGuttersSize;
  int myTextAnnotationExtraSize;
  private record TextAnnotationGutterProviderInfo(@NotNull TextAnnotationGutterProvider provider, int size){}
  private final List<TextAnnotationGutterProviderInfo> myTextAnnotationGutterProviders = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myGapAfterAnnotations;
  private final Map<TextAnnotationGutterProvider, EditorGutterAction> myProviderToListener = new HashMap<>();
  private String myLastGutterToolTip;
  private @Nullable LineNumberConverter myLineNumberConverter;
  private @Nullable LineNumberConverter myAdditionalLineNumberConverter;
  private boolean myShowDefaultGutterPopup = true;
  private boolean myCanCloseAnnotations = true;
  private @Nullable ActionGroup myCustomGutterPopupGroup;
  private final Int2ObjectMap<Color> myTextFgColors = new Int2ObjectOpenHashMap<>();
  private boolean myPaintBackground = true;
  private boolean myLeftFreePaintersAreaShown;
  private boolean myRightFreePaintersAreaShown;
  private @NotNull EditorGutterFreePainterAreaState myLeftFreePaintersAreaState = EditorGutterFreePainterAreaState.ON_DEMAND;
  private @NotNull EditorGutterFreePainterAreaState myRightFreePaintersAreaState = EditorGutterFreePainterAreaState.ON_DEMAND;
  private int myLeftFreePaintersAreaReserveWidth = 0; // Unscaled
  private int myRightFreePaintersAreaReserveWidth = 0;
  private int myLastNonDumbModeIconAreaWidth;
  boolean myDnDInProgress;
  private final EditorGutterLayout myLayout = new EditorGutterLayout(this);
  private @Nullable AccessibleGutterLine myAccessibleGutterLine;
  private final AlphaAnimationContext myAlphaContext = new AlphaAnimationContext(composite -> {
    if (isShowing()) repaint();
  });
  private boolean myHovered = false;
  private final @NotNull EventDispatcher<EditorGutterListener> myEditorGutterListeners = EventDispatcher.create(EditorGutterListener.class);
  private int myHoveredFreeMarkersLine = -1;
  private @Nullable GutterIconRenderer myCurrentHoveringGutterRenderer;

  EditorGutterComponentImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      installDnD();
    }
    setOpaque(true);
    myAnchorDisplayStrategy = new FoldingAnchorsOverlayStrategy(editor);

    Project project = myEditor.getProject();
    if (project != null) {
      project.getMessageBus().connect(myEditor.getDisposable()).subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
        @Override
        public void exitDumbMode() {
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
    myEditor.getCaretModel().addCaretListener(new LineNumbersRepainter());
    Disposer.register(editor.getDisposable(), myAlphaContext.getDisposable());
  }

  public @NotNull EditorImpl getEditor() {
    return myEditor;
  }

  private void installDnD() {
    DnDSupport.createBuilder(this)
      .setBeanProvider(info -> {
        GutterIconRenderer renderer = getGutterRenderer(info.getPoint());
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
        Object attachedObject = e.getAttachedObject();
        if (attachedObject instanceof GutterIconRenderer && checkDumbAware(attachedObject)) {
          GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
          if (draggableObject != null) {
            int line = convertPointToLineNumber(e.getPoint());
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
        Object attachedObject = e.getAttachedObject();
        if (attachedObject instanceof GutterIconRenderer && checkDumbAware(attachedObject)) {
          GutterDraggableObject draggableObject = ((GutterIconRenderer)attachedObject).getDraggableObject();
          if (draggableObject != null) {
            int line = convertPointToLineNumber(e.getPoint());
            if (line != -1) {
              e.setDropPossible(true);
              e.setCursor(draggableObject.getCursor(line, myEditor.getVirtualFile(), e.getAction().getActionId()));
            }
          }
        }
        else if (attachedObject instanceof DnDNativeTarget.EventInfo && myEditor.getSettings().isDndEnabled()) {
          Transferable transferable = ((DnDNativeTarget.EventInfo)attachedObject).getTransferable();
          if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            int line = convertPointToLineNumber(e.getPoint());
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
      .enableAsNativeTarget() // required to accept dragging from editor (as editor component doesn't use DnDSupport to implement drag-n-drop)
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
    JBVG.updateCachedValues();
    super.updateUI();
    setRenderingHints();
    reinitSettings(true);
  }

  private void setRenderingHints() {
    UISettings.setupEditorAntialiasing(this);
    putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, UISettings.getEditorFractionalMetricsHint());
  }

  public void reinitSettings(boolean updateGutterSize) {
    updateFoldingOutlineVisibility();
    updateSize(false, updateGutterSize);
    repaint();
  }

  private void updateFoldingOutlineVisibility() {
    myAlphaContext.setVisible(
      !ExperimentalUI.isNewUI() ||
      myHovered ||
      !EditorSettingsExternalizable.getInstance().isFoldingOutlineShownOnlyOnHover()
    );
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  public void paintComponent(Graphics g_) {
    ReadAction.run(() -> {
      Rectangle clip = g_.getClipBounds();
      if (clip == null || clip.isEmpty()) {
        return;
      }

      Graphics2D g = (Graphics2D)getComponentGraphics(g_);

      if (myEditor.isDisposed()) {
        g.setColor(myEditor.getDisposedBackground());
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
      paintBackground(g, clip, gutterSeparatorX, getWidth() - gutterSeparatorX, myEditor.getBackgroundColor(), caretRowColor);
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

        if (ExperimentalUI.isNewUI() && myPaintBackground && !DistractionFreeModeController.shouldMinimizeCustomHeader()) {
          if (!myEditor.isStickyLinePainting()) { // suppress vertical line between gutter and editor on the sticky lines panel
            g.setColor(getEditor().getColorsScheme().getColor(EditorColors.INDENT_GUIDE_COLOR));
            LinePainter2D.paint(g, gutterSeparatorX, clip.y, gutterSeparatorX, clip.y + clip.height);
          }
        }
      }
      finally {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
      }

      if (old != null) g.setTransform(old);

      debugGutterAreas(g);
    });
  }

  private void debugGutterAreas(Graphics2D g) {
    if (!debug()) return;
    Point p = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(p, this);
    if (p.x >= 0 && p.x <= getWidth()) {
      int off = 0;
      List<EditorGutterLayout.GutterArea> layout = isMirrored() ? ContainerUtil.reverse(myLayout.getLayout()) : myLayout.getLayout();
      for (EditorGutterLayout.GutterArea area : layout) {
        int x = off;
        off += area.width();
        if (off >= p.x) {
          g.setPaint(ColorUtil.withAlpha(JBColor.GREEN, 0.15));
          g.fillRect(x, 0, area.width(), getHeight());
          g.setPaint(myEditor.getColorsScheme().getColor(EditorColors.LINE_NUMBERS_COLOR));
          g.setFont(JBUI.Fonts.smallFont().lessOn(2f));
          int y = SwingUtilities.convertPoint(myEditor.getComponent(), p, myEditor.getScrollPane()).y;
          g.drawString(String.valueOf(area.width()), x, y + g.getClipBounds().y - 10);

          showToolTip(area.toString(), p, Balloon.Position.below); //NON-NLS
          break;
        }
      }
    }
  }

  private void paintEditorBackgrounds(Graphics g, int firstVisibleOffset, int lastVisibleOffset) {
    myTextFgColors.clear();
    Color defaultBackgroundColor = myEditor.getBackgroundColor();
    Color defaultForegroundColor = myEditor.getColorsScheme().getDefaultForeground();
    int startX = myEditor.isInDistractionFreeMode() ? 0 : ExperimentalUI.isNewUI() ? getWhitespaceSeparatorOffset() + 1
                                                                                   : getWhitespaceSeparatorOffset();
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
    VisualPosition visualEnd = myEditor.offsetToVisualPosition(endOffset, false, false);
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

  private void processClose(MouseEvent e) {
    IdeEventQueue queue = IdeEventQueue.getInstance();

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

      for (TextAnnotationGutterProviderInfo info : myTextAnnotationGutterProviders) {
        TextAnnotationGutterProvider gutterProvider = info.provider();

        int lineHeight = myEditor.getLineHeight();
        int lastLine = myEditor.logicalToVisualPosition(new LogicalPosition(endLineNumber(), 0)).line;
        endVisualLine = Math.min(endVisualLine, lastLine);
        if (startVisualLine > endVisualLine) {
          break;
        }

        int annotationSize = info.size();

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
              // avoid drawing bg over the "sticky" line above, or over a possible gap in the gutter below (e.g., code vision)
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
    var text = gutterProvider.getLineText(line, myEditor);
    if (!StringUtil.isEmpty(text)) {
      g.setColor(myEditor.getColorsScheme().getColor(gutterProvider.getColor(line, myEditor)));
      var style = gutterProvider.getStyle(line, myEditor);
      var font = getFontForText(text, style);
      g.setFont(font);
      int offset = 0;
      if (gutterProvider.useMargin()) {
        if (gutterProvider.getLeftMargin() >= 0) {
          offset = gutterProvider.getLeftMargin();
        }
        else {
          offset = getGapBetweenAnnotations() / 2;
        }
      }
      g.drawString(text, offset + x, y + myEditor.getAscent());
    }
  }

  private Font getFontForText(String text, EditorFontType style) {
    var font = ExperimentalUI.isNewUI() ? JBFont.regular() : myEditor.getColorsScheme().getFont(style);
    return UIUtil.getFontWithFallbackIfNeeded(font, text);
  }

  private void paintFoldingTree(@NotNull Graphics g, @NotNull Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    if (myEditor.isStickyLinePainting()) {
      // suppress folding icons on the sticky lines panel
      return;
    }
    if (isFoldingOutlineShown()) {
      doPaintFoldingTree((Graphics2D)g, clip, firstVisibleOffset, lastVisibleOffset);
    }
  }

  private void paintLineMarkers(Graphics2D g, int firstVisibleOffset, int lastVisibleOffset, int firstVisibleLine, int lastVisibleLine) {
    if (isLineMarkersShown()) {
      paintGutterRenderers(g, firstVisibleOffset, lastVisibleOffset, firstVisibleLine, lastVisibleLine);
    }
  }

  private void paintBackground(Graphics g,
                               Rectangle clip,
                               int x,
                               int width,
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
    if (myEditor.isStickyLinePainting()) {
      // suppress the gutter caret row background on the sticky lines panel
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
      return region instanceof CustomFoldRegion && region.getStartOffset() <= caretOffset;
    }
    return false;
  }

  private void paintCaretRowBackground(Graphics g, int x, int width, Color color) {
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
      doPaintLineNumbers(g, startVisualLine, endVisualLine, offset, getPrimaryLineNumberConverter());
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
    return EditorGutterColor.getEditorGutterBackgroundColor(myEditor, myPaintBackground);
  }

  private Font getFontForLineNumbers() {
    Font editorFont = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
    float editorFontSize = editorFont.getSize2D();
    float delta = (float)AdvancedSettings.getInt("editor.gutter.linenumber.font.size.delta");
    return editorFont.deriveFont(Math.max(1f, editorFontSize + delta));
  }

  private int calcLineNumbersAreaWidth(@NotNull String maxLineNumberText) {
    return FontLayoutService.getInstance().stringWidth(getFontMetrics(getFontForLineNumbers()), maxLineNumberText);
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
          String lineToDisplay = converter.convertLineNumberToString(myEditor, logicalLine + 1);
          if (lineToDisplay != null) {
            int y = visLinesIterator.getY();
            if (y < viewportStartY && visLinesIterator.endsWithSoftWrap()) {  // "sticky" line number
              if (!myEditor.isStickyLinePainting()) { // suppress shifting line number of wrapped line on sticky lines panel IDEA-345706
                y = viewportStartY;
              }
            }
            if (myEditor.isInDistractionFreeMode()) {
              Color fgColor = myTextFgColors.get(visLinesIterator.getVisualLine());
              g.setColor(fgColor != null ? fgColor : color != null ? color : JBColor.blue);
            }
            else {
              g.setColor(color);
            }

            if (colorUnderCaretRow != null && caretLogicalLine == logicalLine) {
              if (!myEditor.isStickyLinePainting()) { // suppress gutter line number under caret on sticky lines panel
                g.setColor(colorUnderCaretRow);
              }
            }

            Icon iconOnTheLine = null;
            Icon hoverIcon = null;
            if (ExperimentalUI.isNewUI() /*&& EditorUtil.isRealFileEditor(getEditor())*/ && EditorUtil.isBreakPointsOnLineNumbers()) {
              VisualPosition visualPosition = myEditor.logicalToVisualPosition(new LogicalPosition(logicalLine, 0));
              Optional<GutterMark> breakpoint = getGutterRenderers(visualPosition.line).stream()
                .filter(r -> r instanceof GutterIconRenderer &&
                             ((GutterIconRenderer)r).getAlignment() == GutterIconRenderer.Alignment.LINE_NUMBERS)
                .findFirst();
              if (breakpoint.isPresent()) {
                iconOnTheLine = breakpoint.get().getIcon();
              }
              if ((myAlphaContext.isVisible() || isGutterContextMenuShown()) &&
                  Objects.equals(getClientProperty("active.line.number"), logicalLine)) {
                Object activeIcon = getClientProperty("line.number.hover.icon");
                if (activeIcon instanceof Icon) {
                  hoverIcon = (Icon)activeIcon;
                }
              }
            }
            if (myEditor.isStickyLinePainting()) {
              // suppress breakpoint icon to print line number on the sticky lines panel
              iconOnTheLine = null;
            }

            if (iconOnTheLine == null && hoverIcon == null) {
              int textOffset = isMirrored() ?
                               offset - getLineNumberAreaWidth() - 1 :
                               offset - FontLayoutService.getInstance().stringWidth(g.getFontMetrics(), lineToDisplay);

              g.drawString(lineToDisplay, textOffset, y + myEditor.getAscent());
            }
            else if (hoverIcon != null && iconOnTheLine == null) {
              Icon icon = scaleIcon(hoverIcon);
              int iconX = offset - icon.getIconWidth();
              int iconY = y + (visLinesIterator.getLineHeight() - icon.getIconHeight()) / 2;
              float alpha = JBUI.getFloat("Breakpoint.iconHoverAlpha", 0.5f);
              alpha = Math.max(0, Math.min(alpha, 1));
              GraphicsUtil.paintWithAlpha(g, alpha, () -> icon.paintIcon(this, g, iconX, iconY));
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

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (myEditor.isDisposed()) return;

    sink.set(KEY, this);
    sink.set(CommonDataKeys.EDITOR, myEditor);
    sink.set(LOGICAL_LINE_AT_CURSOR, myLastActionableClick == null ? null : myLastActionableClick.myLogicalLineAtCursor);
    sink.set(ICON_CENTER_POSITION, myLastActionableClick == null ? null : myLastActionableClick.myIconCenterPosition);
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
      new FilteringMarkupIterator<>(myEditor.getFilteredDocumentMarkupModel().overlappingIterator(startOffset, endOffset),
                                    h -> h.isRenderedInGutter());
    MarkupIterator<RangeHighlighterEx> editorHighlighters =
      new FilteringMarkupIterator<>(myEditor.getMarkupModel().overlappingIterator(startOffset, endOffset), h -> h.isRenderedInGutter());

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

        RangeHighlighterEx lowerHighlighter;
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

  private static boolean less(RangeHighlighter h1, RangeHighlighter h2) {
    return h1 != null && (h2 == null || h1.getStartOffset() < h2.getStartOffset());
  }

  boolean canImpactSize(@NotNull RangeHighlighterEx highlighter) {
    if (highlighter.getGutterIconRenderer() != null) return true;
    LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
    if (lineMarkerRenderer == null) return false;
    LineMarkerRendererEx.Position position = getLineMarkerPosition(lineMarkerRenderer);
    return position == LineMarkerRendererEx.Position.LEFT && myLeftFreePaintersAreaState == EditorGutterFreePainterAreaState.ON_DEMAND ||
           position == LineMarkerRendererEx.Position.RIGHT && myRightFreePaintersAreaState == EditorGutterFreePainterAreaState.ON_DEMAND;
  }

  @Override
  public void revalidateMarkup() {
    updateSize();
  }

  void updateSizeOnShowNotify() {
    updateSize(false, true);
  }

  void updateSize() {
    updateSize(false, false);
  }

  @RequiresEdt
  void updateSize(boolean onLayout, boolean canShrink) {
    int prevHash = sizeHash();

    if (!onLayout) {
      clearLineToGutterRenderersCache();
      calcLineNumberAreaWidth();
      calcLineMarkerAreaWidth(canShrink);
      myTextAnnotationGuttersSize = calcAnnotationsSize();
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

  private int calcAnnotationsSize() {
    myGapAfterAnnotations = false;
    int lineCount = Math.max(myEditor.getDocument().getLineCount(), 1);
    myTextAnnotationGutterProviders.replaceAll(info -> {
      TextAnnotationGutterProvider gutterProvider = info.provider();
      int gutterSize = 0;
      for (int i = 0; i < lineCount; i++) {
        String lineText = gutterProvider.getLineText(i, myEditor);
        if (!StringUtil.isEmpty(lineText)) {
          EditorFontType style = gutterProvider.getStyle(i, myEditor);
          Font font = getFontForText(lineText, style);
          FontMetrics fontMetrics = getFontMetrics(font);
          gutterSize = Math.max(gutterSize, fontMetrics.stringWidth(lineText));
        }
        else if (gutterProvider instanceof TextAnnotationGutterProvider.Filler) {
          gutterSize = Math.max(gutterSize, ((TextAnnotationGutterProvider.Filler)gutterProvider).getWidth());
        }
      }
      if (gutterSize > 0) {
        boolean margin = gutterProvider.useMargin();
        myGapAfterAnnotations = margin;
        if (margin) {
          gutterSize += getGapBetweenAnnotations();
        }
      }
      return new TextAnnotationGutterProviderInfo(gutterProvider, gutterSize);
    });
    // separate loop because the operation in myTextAnnotationGutterProviders.replaceAll() can retry on contention
    int totalSize = 0;
    for (TextAnnotationGutterProviderInfo info : myTextAnnotationGutterProviders) {
      totalSize += info.size();
    }
    return totalSize;
  }

  private void calcAnnotationExtraSize() {
    myTextAnnotationExtraSize = 0;
    if (!myEditor.isInDistractionFreeMode() || isMirrored()) return;

    int marginFromSettings = AdvancedSettings.getInt(DISTRACTION_FREE_MARGIN);
    if (marginFromSettings != -1) {
      myTextAnnotationExtraSize = marginFromSettings;
      return;
    }

    Component outerContainer = ComponentUtil.findParentByCondition(myEditor.getComponent(), c -> EditorCompositeKt.isEditorComposite(c));
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

  private Int2ObjectMap<List<GutterMark>> buildGutterRenderersCache() {
    myLineToGutterRenderersCacheForLogicalLines = logicalLinesMatchVisualOnes();
    Int2ObjectMap<List<GutterMark>> lineToGutterRenderers = new Int2ObjectOpenHashMap<>();
    processRangeHighlighters(0, myEditor.getDocument().getTextLength(), highlighter -> {
      GutterMark renderer = highlighter.getGutterIconRenderer();
      if (!shouldBeShown(renderer)) {
        return;
      }
      if (!isHighlighterVisible(highlighter)) {
        return;
      }
      int line = myEditor.offsetToVisualLine(highlighter.getStartOffset());
      List<GutterMark> renderers = lineToGutterRenderers.get(line);
      if (renderers == null) {
        renderers = new SmartList<>();
        lineToGutterRenderers.put(line, renderers);
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
            lineToGutterRenderers.put(line, List.of(renderer));
          }
          else {
            lineToGutterRenderers.remove(line);
          }
        }
      }
    }

    List<GutterMarkPreprocessor> gutterMarkPreprocessors = GutterMarkPreprocessor.EP_NAME.getExtensionList();
    for (Int2ObjectMap.Entry<List<GutterMark>> entry : Int2ObjectMaps.fastIterable(lineToGutterRenderers)) {
      List<GutterMark> newValue = entry.getValue();
      for (GutterMarkPreprocessor preprocessor : gutterMarkPreprocessors) {
        newValue = preprocessor.processMarkers(newValue);
      }
      // don't allow more than 4 icons per line
      entry.setValue(ContainerUtil.getFirstItems(newValue, 4));
    }
    return lineToGutterRenderers;
  }

  private boolean shouldBeShown(@Nullable GutterMark gutterIconRenderer) {
    return gutterIconRenderer != null && (areIconsShown() || gutterIconRenderer instanceof NonHideableIconGutterMark);
  }

  private void calcLineMarkerAreaWidth(boolean canShrink) {
    boolean leftPainterOnDemand = myLeftFreePaintersAreaState == EditorGutterFreePainterAreaState.ON_DEMAND;
    myLeftFreePaintersAreaShown = myLeftFreePaintersAreaState == EditorGutterFreePainterAreaState.SHOW;

    boolean rightPainterOnDemand = myRightFreePaintersAreaState == EditorGutterFreePainterAreaState.ON_DEMAND;
    myRightFreePaintersAreaShown = myRightFreePaintersAreaState == EditorGutterFreePainterAreaState.SHOW;

    if (leftPainterOnDemand || rightPainterOnDemand) {
      processRangeHighlighters(0, myEditor.getDocument().getTextLength(), highlighter -> {
        LineMarkerRenderer lineMarkerRenderer = highlighter.getLineMarkerRenderer();
        if (lineMarkerRenderer != null) {
          LineMarkerRendererEx.Position position = getLineMarkerPosition(lineMarkerRenderer);
          if (leftPainterOnDemand && position == LineMarkerRendererEx.Position.LEFT && isLineMarkerVisible(highlighter)) {
            myLeftFreePaintersAreaShown = true;
          }
          if (rightPainterOnDemand && position == LineMarkerRendererEx.Position.RIGHT && isLineMarkerVisible(highlighter)) {
            myRightFreePaintersAreaShown = true;
          }
        }
      });
    }

    int minWidth = areIconsShown() ? EditorUIUtil.scaleWidth(myStartIconAreaWidth, myEditor) : 0;
    myIconsAreaWidth = canShrink ? minWidth : Math.max(myIconsAreaWidth, minWidth);

    for (Int2ObjectMap.Entry<List<GutterMark>> entry : processGutterRenderers()) {
      int width = 1;
      List<GutterMark> renderers = entry.getValue();
      for (int i = 0; i < renderers.size(); i++) {
        GutterMark renderer = renderers.get(i);
        if (!checkDumbAware(renderer)) continue;
        if (isMergedWithLineNumbers(renderer)) continue;
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

  private boolean isMergedWithLineNumbers(GutterMark renderer) {
    return isLineNumbersShown() &&
           renderer instanceof GutterIconRenderer &&
           ((GutterIconRenderer)renderer).getAlignment() == GutterIconRenderer.Alignment.LINE_NUMBERS;
  }

  @Override
  public @NotNull List<GutterMark> getGutterRenderers(int line) {
    if (myLineToGutterRenderers == null || myLineToGutterRenderersCacheForLogicalLines != logicalLinesMatchVisualOnes()) {
      myLineToGutterRenderers = buildGutterRenderersCache();
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

  @Override
  public @NotNull List<Pair<GutterMark, Rectangle>> getGutterRenderersAndRectangles(int visualLine) {
    List<GutterMark> renderers = getGutterRenderers(visualLine);
    int lineY = myEditor.visualLineToY(visualLine);
    List<Pair<GutterMark, Rectangle>> result = new ArrayList<>();
    processIconsRowForY(lineY, renderers, (x, y, renderer) ->
      result.add(Pair.pair(renderer, new Rectangle(x, y, renderer.getIcon().getIconWidth(), renderer.getIcon().getIconHeight()))));
    return result;
  }

  private @NotNull ObjectIterable<Int2ObjectMap.Entry<List<GutterMark>>> processGutterRenderers() {
    if (myLineToGutterRenderers == null || myLineToGutterRenderersCacheForLogicalLines != logicalLinesMatchVisualOnes()) {
      myLineToGutterRenderers = buildGutterRenderersCache();
    }
    return Int2ObjectMaps.fastIterable(myLineToGutterRenderers);
  }

  @VisibleForTesting
  public Collection<GutterIconWithLocation> getLineGutterMarks() {
    List<GutterIconWithLocation> list = new ArrayList<>();
    for (Int2ObjectMap.Entry<List<GutterMark>> entry : processGutterRenderers()) {
      List<GutterMark> marks = entry.getValue();
      int line = entry.getIntKey();
      for (GutterMark mark : marks) {
        if (mark instanceof GutterIconRenderer) {
          Point markLocation = getCenterPoint((GutterIconRenderer)mark);
          list.add(new GutterIconWithLocation(mark, line, markLocation));
        }
      }
    }
    return list;
  }

  private boolean isHighlighterVisible(RangeHighlighter highlighter) {
    return !FoldingUtil.isHighlighterFolded(myEditor, highlighter);
  }

  private void paintGutterRenderers(Graphics2D g,
                                    int firstVisibleOffset, int lastVisibleOffset, int firstVisibleLine, int lastVisibleLine) {
    Object hint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    boolean stickyLinePainting = myEditor.isStickyLinePainting();
    try {
      List<RangeHighlighter> highlighters = new ArrayList<>();
      processRangeHighlighters(firstVisibleOffset, lastVisibleOffset, highlighter -> {
        LineMarkerRenderer r = highlighter.getLineMarkerRenderer();
        if (r != null) {
          // suppress gutter line markers on sticky lines panel
          if (!stickyLinePainting || (r instanceof LineMarkerRendererEx rx && rx.isSticky())) {
            highlighters.add(highlighter);
          }
        }
      });

      ContainerUtil.sort(highlighters, Comparator.comparingInt(RangeHighlighter::getLayer));

      for (RangeHighlighter highlighter : highlighters) {
        paintLineMarkerRenderer(highlighter, g);
      }
    }
    finally {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hint);
    }

    if (!stickyLinePainting) { // suppress gutter icons on sticky lines panel
      paintIcons(firstVisibleLine, lastVisibleLine, g);
    }
  }

  private void paintIcons(int firstVisibleLine, int lastVisibleLine, Graphics2D g) {
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

  private void paintIconRow(int visualLine, int lineY, List<? extends GutterMark> row, Graphics2D g) {
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
    if (startFoldRegion == null) return true; // Marker is visible if start of the region is not folded

    FoldRegion endFoldRegion = myEditor.getFoldingModel().getCollapsedRegionAtOffset(endOffset);
    if (!startFoldRegion.equals(endFoldRegion)) return true; // Start and end are folded, but the middle highlighter part is visible

    if (startOffset == endOffset) {
      // Show highlighters at the edge of the folded area
      return startFoldRegion.getStartOffset() == startOffset || startFoldRegion.getEndOffset() == startOffset;
    }
    return false; // Marker is folded
  }

  @Override
  public boolean isInsideMarkerArea(@NotNull MouseEvent e) {
    if (ExperimentalUI.isNewUI()) {
      int x = e.getX();
      int offset = getExtraLineMarkerFreePaintersAreaOffset();
      int width = getExtraLeftFreePaintersAreaWidth();
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
    int visualLine = myEditor.offsetToVisualLine(endOffset);
    int blockInlaysBelowHeight = EditorUtil.getInlaysHeight(myEditor, visualLine, false);
    int endY = myEditor.visualLineToYRange(visualLine)[1] + blockInlaysBelowHeight;

    LineMarkerRenderer renderer = Objects.requireNonNull(highlighter.getLineMarkerRenderer());
    LineMarkerRendererEx.Position position = getLineMarkerPosition(renderer);

    int w;
    int x;
    switch (position) {
      case LEFT -> {
        w = getLeftFreePaintersAreaWidth();
        x = getLeftFreePaintersAreaOffset();
      }
      case RIGHT -> {
        w = getRightFreePaintersAreaWidth();
        x = getLineMarkerFreePaintersAreaOffset();
      }
      case CUSTOM -> {
        w = getWidth();
        x = 0;
      }
      default -> throw new IllegalArgumentException(position.name());
    }

    int height = endY - startY;
    return new Rectangle(x, startY, w, height);
  }

  @FunctionalInterface
  interface LineGutterIconRendererProcessor {
    void process(int x, int y, @NotNull GutterMark renderer);
  }

  Icon scaleIcon(Icon icon) {
    return EditorUIUtil.scaleIcon(icon, myEditor);
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
      Icon icon = scaleIcon(r.getIcon());
      GutterIconRenderer.Alignment alignment = ((GutterIconRenderer)r).getAlignment();
      if (alignment == GutterIconRenderer.Alignment.LINE_NUMBERS && !isLineNumbersShown()) {
        alignment = GutterIconRenderer.Alignment.LEFT;
      }
      switch (alignment) {
        case LEFT -> {
          processor.process(x, y + getTextAlignmentShift(icon), r);
          x += icon.getIconWidth() + getGapBetweenIcons();
        }
        case CENTER -> {
          middleCount++;
          middleSize += icon.getIconWidth() + getGapBetweenIcons();
        }
        case LINE_NUMBERS -> processor.process(getLineNumberAreaOffset() + getLineNumberAreaWidth() - icon.getIconWidth(),
                                               y + getTextAlignmentShift(icon), r);
      }
    }

    int leftSize = x - getIconAreaOffset();

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
  public void addEditorGutterListener(@NotNull EditorGutterListener listener, @NotNull Disposable parentDisposable) {
    myEditorGutterListeners.addListener(listener, parentDisposable);
  }

  private void fireTextAnnotationGutterProviderAdded(@NotNull TextAnnotationGutterProvider provider) {
    myEditorGutterListeners.getMulticaster().textAnnotationAdded(provider);
  }

  private void fireTextAnnotationGutterProviderRemoved(@NotNull TextAnnotationGutterProvider provider) {
    myEditorGutterListeners.getMulticaster().textAnnotationRemoved(provider);
  }

  @Override
  public void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider) {
    myTextAnnotationGutterProviders.add(new TextAnnotationGutterProviderInfo(provider, 0));
    fireTextAnnotationGutterProviderAdded(provider);
    updateSize();
  }

  @Override
  public void registerTextAnnotation(@NotNull TextAnnotationGutterProvider provider, @NotNull EditorGutterAction action) {
    myTextAnnotationGutterProviders.add(new TextAnnotationGutterProviderInfo(provider, 0));
    myProviderToListener.put(provider, action);
    fireTextAnnotationGutterProviderAdded(provider);
    updateSize();
  }

  @Override
  public @NotNull List<TextAnnotationGutterProvider> getTextAnnotations() {
    return ContainerUtil.map(myTextAnnotationGutterProviders, i->i.provider());
  }

  @Override
  public @Nullable EditorGutterAction getAction(@NotNull TextAnnotationGutterProvider provider) {
    return myProviderToListener.get(provider);
  }

  private void doPaintFoldingTree(@NotNull Graphics2D g, @NotNull Rectangle clip, int firstVisibleOffset, int lastVisibleOffset) {
    double width = getFoldingAnchorWidth2D();

    Collection<DisplayedFoldingAnchor> anchorsToDisplay =
      myAnchorDisplayStrategy.getAnchorsToDisplay(firstVisibleOffset, lastVisibleOffset, myActiveFoldRegions);
    for (DisplayedFoldingAnchor anchor : anchorsToDisplay) {
      boolean active = myActiveFoldRegions.contains(anchor.foldRegion);
      if (ExperimentalUI.isNewUI()) {
        active = myAlphaContext.isVisible();
      }
      drawFoldingAnchor(width, clip, g, anchor.visualLine, anchor.type, active);
    }
  }

  private void paintFoldingLines(Graphics2D g, Rectangle clip) {
    if (ExperimentalUI.isNewUI()) {
      return;
    }
    boolean shown = isFoldingOutlineShown();
    double x = getFoldingMarkerCenterOffset2D();

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
    if (ExperimentalUI.isNewUI()) {
      return getWidth() - 3;
    }
    else {
      return (int)Math.round(getFoldingMarkerCenterOffset2D());
    }
  }

  private double getFoldingMarkerCenterOffset2D() {
    ScaleContext ctx = ScaleContext.create(myEditor.getComponent());
    if (ExperimentalUI.isNewUI()) {
      return PaintUtil.alignToInt(getFoldingAreaOffset() + getFoldingAnchorWidth(), ctx, RoundingMode.ROUND, null);
    }
    return PaintUtil.alignToInt(getFoldingAreaOffset() + getFoldingAnchorWidth() / 2.0, ctx, RoundingMode.ROUND, null);
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
    double centerX = LinePainter2D.getStrokeCenter(g, getFoldingMarkerCenterOffset2D(), StrokeType.CENTERED, getStrokeWidth());
    double strokeOff = centerX - getFoldingMarkerCenterOffset2D();
    // need to have the same sub-device-pixel offset as centerX for the square_with_plus rect to have equal dev width/height
    double centerY = PaintUtil.alignToInt(y + width / 2, g) + strokeOff;
    switch (type) {
      case COLLAPSED, COLLAPSED_SINGLE_LINE -> {
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawSquareWithPlusOrMinus(g, centerX, centerY, width, true, active, visualLine);
        }
      }
      case EXPANDED_SINGLE_LINE -> {
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawSquareWithPlusOrMinus(g, centerX, centerY, width, false, active, visualLine);
        }
      }
      case EXPANDED_TOP -> {
        if (y <= clip.y + clip.height && y + height >= clip.y) {
          drawDirectedBox(g, centerX, centerY, width, height, baseHeight, active, visualLine);
        }
      }
      case EXPANDED_BOTTOM -> {
        y += width;
        if (y - height <= clip.y + clip.height && y >= clip.y) {
          drawDirectedBox(g, centerX, centerY, width, -height, -baseHeight, active, visualLine);
        }
      }
    }
  }

  private void drawDirectedBox(Graphics2D g,
                               double centerX,
                               double centerY,
                               double width,
                               double height,
                               double baseHeight,
                               boolean active,
                               int visualLine) {
    double sw = getStrokeWidth();
    Rectangle2D rect = RectanglePainter2D.align(g,
                                                EnumSet.of(LinePainter2D.Align.CENTER_X, LinePainter2D.Align.CENTER_Y),
                                                centerX, centerY, width, width, StrokeType.CENTERED, sw);

    double x1 = rect.getX();
    double x2 = x1 + rect.getWidth() - 1;
    double y = height > 0 ? rect.getY() : rect.getY() + rect.getHeight() - 1;
    double[] dxPoints = {x1, x1, x2, x2, centerX};
    double[] dyPoints = {y + baseHeight, y, y, y + baseHeight, y + height + (height < 0 ? 1 : 0)};

    if (ExperimentalUI.isNewUI()) {
      if (height <= 0 && !EditorSettingsExternalizable.getInstance().isFoldingEndingsShown()) {
        //do not paint folding endings in the new UI by default
        return;
      }
      myAlphaContext.paintWithComposite(g, () -> {
        Icon icon = scaleIcon(height > 0 ? AllIcons.Gutter.Fold : AllIcons.Gutter.FoldBottom);
        icon.paintIcon(this, g, getFoldingAreaOffset(), getFoldingIconY(visualLine, icon));
      });
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
    if (ExperimentalUI.isNewUI()) {
      Icon icon = scaleIcon(AllIcons.Gutter.Unfold);
      icon.paintIcon(this, g, getFoldingAreaOffset(), getFoldingIconY(visualLine, icon));
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
    if (ExperimentalUI.isNewUI()) return 0;
    return Math.max(width / 5, scale(2));
  }

  /**
   * Scale the value by BOTH user scale and editor line height scale
   */
  private double scale(double v) {
    return JBUIScale.scale((float)v) * myEditor.getScale();
  }

  /**
   * Scale the value by editor line height scale
   */
  private int scaleWithEditor(int v) {
    return RoundingMode.ROUND.round(v * myEditor.getScale());
  }

  /**
   * Scale the value by editor line height scale
   */
  private int scaleWithEditor(float v) {
    return RoundingMode.ROUND.round(v * myEditor.getScale());
  }

  private int getFoldingAnchorWidth() {
    return (int)Math.round(getFoldingAnchorWidth2D());
  }

  private double getFoldingAnchorWidth2D() {
    if (ExperimentalUI.isNewUI()) {
      return scale(AllIcons.Gutter.Fold.getIconWidth());
    }
    return Math.min(scale(4f), myEditor.getLineHeight() / 2f - JBUIScale.scale(2f)) * 2;
  }

  private double getStrokeWidth() {
    double sw = JreHiDpiUtil.isJreHiDPIEnabled() || scale(1f) < 2 ? 1 : 2;
    ScaleContext ctx = ScaleContext.create(myEditor.getComponent());
    return PaintUtil.alignToInt(sw, ctx, PaintUtil.devValue(1, ctx) > 2 ? RoundingMode.FLOOR : RoundingMode.ROUND, null);
  }

  @Override
  public int getFoldingAreaOffset() {
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

  boolean isLineNumbersAfterIcons() {
    return myEditor.getSettings().isLineNumbersAfterIcons();
  }

  boolean areIconsShown() {
    return myEditor.getSettings().areGutterIconsShown();
  }

  boolean isLineNumbersShown() {
    return myEditor.getSettings().isLineNumbersShown();
  }

  @Override
  public @NotNull LineNumberConverter getPrimaryLineNumberConverter() {
    if (myLineNumberConverter != null) return myLineNumberConverter;

    EditorSettings.LineNumerationType type = myEditor.getSettings().getLineNumerationType();
    return getStandardLineNumberConverter(type);
  }

  @Override
  public @Nullable LineNumberConverter getAdditionalLineNumberConverter() {
    return myAdditionalLineNumberConverter;
  }

  @Override
  public boolean isAnnotationsShown() {
    return !myTextAnnotationGutterProviders.isEmpty();
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

  @Override
  public int getLineNumberAreaWidth() {
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

    String maxLineNumber = getPrimaryLineNumberConverter().getMaxLineNumberString(myEditor);
    myLineNumberAreaWidth = Math.max(getInitialLineNumberWidth(), maxLineNumber == null ? 0 : calcLineNumbersAreaWidth(maxLineNumber));

    myAdditionalLineNumberAreaWidth = 0;
    if (myAdditionalLineNumberConverter != null) {
      String maxAdditionalLineNumber = myAdditionalLineNumberConverter.getMaxLineNumberString(myEditor);
      myAdditionalLineNumberAreaWidth = maxAdditionalLineNumber == null ? 0 : calcLineNumbersAreaWidth(maxAdditionalLineNumber);
    }
  }

  @Nullable
  EditorMouseEventArea getEditorMouseAreaByOffset(int offset) {
    return myLayout.getEditorMouseAreaByOffset(offset);
  }

  @Override
  public int getLineNumberAreaOffset() {
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

  @Override
  public int getExtraLineMarkerFreePaintersAreaOffset() {
    return myLayout.getExtraLeftFreePaintersAreaOffset();
  }

  int getExtraLeftFreePaintersAreaWidth() {
    float width = FREE_PAINTERS_EXTRA_LEFT_AREA_WIDTH.getFloat();
    return scaleWithEditor(width);
  }

  int getLeftFreePaintersAreaWidth() {
    if (!myLeftFreePaintersAreaShown) return 0;

    if (ExperimentalUI.isNewUI()) {
      return scaleWithEditor(Math.max(FREE_PAINTERS_LEFT_AREA_WIDTH.get(), JBUI.scale(myLeftFreePaintersAreaReserveWidth))) + 2;
    }
    return Math.max(FREE_PAINTERS_LEFT_AREA_WIDTH.get(), myLeftFreePaintersAreaReserveWidth);
  }

  int getRightFreePaintersAreaWidth() {
    if (!myRightFreePaintersAreaShown) return 0;

    if (ExperimentalUI.isNewUI()) {
      return Math.max(0, myRightFreePaintersAreaReserveWidth);
    }
    return Math.max(FREE_PAINTERS_RIGHT_AREA_WIDTH.get(), myRightFreePaintersAreaReserveWidth);
  }

  @Override
  public int getIconsAreaWidth() {
    return myIconsAreaWidth;
  }

  int getGapAfterIconsArea() {
    return isRealEditor() && areIconsShown()
           ? ExperimentalUI.isNewUI()
             ? EditorUIUtil.scaleWidth(GAP_AFTER_ICONS_WIDTH.get(), myEditor)
             : getGapBetweenAreas()
           : 0;
  }

  private boolean isMirrored() {
    return myEditor.getVerticalScrollbarOrientation() != EditorEx.VERTICAL_SCROLLBAR_RIGHT;
  }

  private @Nullable AffineTransform setMirrorTransformIfNeeded(Graphics2D g, int offset, int width) {
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

  @Override
  public @Nullable FoldRegion findFoldingAnchorAt(int x, int y) {
    if (!myEditor.getSettings().isFoldingOutlineShown()) return null;

    int anchorX = getFoldingAreaOffset();
    int anchorWidth = getFoldingAnchorWidth();

    int visualLine = myEditor.yToVisualLine(y);
    int neighbourhoodStartOffset = myEditor.visualPositionToOffset(new VisualPosition(visualLine, 0));
    int neighbourhoodEndOffset = myEditor.visualPositionToOffset(new VisualPosition(visualLine, Integer.MAX_VALUE));

    Collection<DisplayedFoldingAnchor> displayedAnchors = myAnchorDisplayStrategy.getAnchorsToDisplay(neighbourhoodStartOffset,
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
    if (ExperimentalUI.isNewUI()) {
      //in the new ui folding anchor click area has full line height, see IDEA-296393
      return new Rectangle(anchorX, myEditor.visualLineToY(foldStart), anchorWidth, myEditor.getLineHeight());
    }
    return new Rectangle(anchorX, (int)getFoldAnchorY(foldStart, anchorWidth), anchorWidth, anchorWidth);
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    TooltipController.getInstance().cancelTooltips();
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    updateFreePainters(e);

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
        int line = getLineNumAtPoint(point);
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

    updateHover(pointInfo == null ? null : pointInfo.renderer);

    if (debug()) {
      repaint();
    }
  }

  private void updateFreePainters(MouseEvent e) {
    if (!isLineMarkersShown() ||
        !ExperimentalUI.isNewUI() ||
        !Registry.is("ide.gutter.update.free.markers.on.hover")) {
      return;
    }

    Point point = e.getPoint();
    int x = convertX(point.x);

    int hoveredLine;
    if (x >= getExtraLineMarkerFreePaintersAreaOffset() &&
        x <= getExtraLineMarkerFreePaintersAreaOffset() + getExtraLeftFreePaintersAreaWidth()) {
      hoveredLine = getEditor().xyToLogicalPosition(point).line;
    }
    else {
      hoveredLine = -1;
    }

    if (myHoveredFreeMarkersLine != hoveredLine) {
      myHoveredFreeMarkersLine = hoveredLine;
      repaint();
    }
  }

  private static boolean debug() {
    return Registry.is("ide.debug.gutter.area", false);
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
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      new Task.Backgroundable(myEditor.getProject(), IdeBundle.message("progress.title.constructing.tooltip")) {
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
          .setRequestFocus(ScreenReader.isActive())
          .setStatus(HintHint.Status.Info, JBUI.insets(0, 2, 4, 0));
      if (myEditor.getComponent().getRootPane() != null) {
        controller.showTooltipByMouseMove(myEditor, showPoint, tr, false, GUTTER_TOOLTIP_GROUP, hint);
      }
    }
  }

  void resetMousePointer() {
    if (IdeGlassPaneImpl.Companion.hasPreProcessedCursor(this)) {
      return;
    }

    UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  private void updateHover(@Nullable GutterIconRenderer hoverGutterRenderer) {
    if (hoverGutterRenderer == myCurrentHoveringGutterRenderer) {
      return;
    }
    if (myCurrentHoveringGutterRenderer != null) {
      myEditorGutterListeners.getMulticaster().hoverEnded(new EditorGutterHoverEvent(this, myCurrentHoveringGutterRenderer));
      myCurrentHoveringGutterRenderer = null;
    }
    if (hoverGutterRenderer != null) {
      myCurrentHoveringGutterRenderer = hoverGutterRenderer;
      myEditorGutterListeners.getMulticaster().hoverStarted(new EditorGutterHoverEvent(this, myCurrentHoveringGutterRenderer));
    }
  }

  void validateMousePointer(@NotNull MouseEvent e) {
    if (IdeGlassPaneImpl.Companion.hasPreProcessedCursor(this)) {
      return;
    }

    Cursor cursor = updateCursorAtMousePointer(e);
    UIUtil.setCursor(this, cursor);
  }

  private Cursor updateCursorAtMousePointer(@NotNull MouseEvent e) {
    FoldRegion foldingAtCursor = findFoldingAnchorAt(e.getX(), e.getY());
    setActiveFoldRegions(getGroupRegions(foldingAtCursor));

    GutterIconRenderer renderer = getGutterRenderer(e);
    if (renderer != null) {
      if (renderer.isNavigateAction()) {
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
      }
    }

    ActiveGutterRenderer lineRenderer = getActiveRendererByMouseEvent(e);
    if (lineRenderer != null) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    TextAnnotationGutterProvider provider = getProviderAtPoint(e.getPoint());
    if (provider != null) {
      EditorGutterAction action = myProviderToListener.get(provider);
      if (action != null) {
        int line = getLineNumAtPoint(e.getPoint());
        if (line >= 0) {
          return action.getCursor(line);
        }
      }
    }

    Object contextMenu = getClientProperty("line.number.hover.icon.context.menu");
    if (contextMenu instanceof ActionGroup) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    if (foldingAtCursor != null) {
      return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    return Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
  }

  private @NotNull List<FoldRegion> getGroupRegions(@Nullable FoldRegion foldingAtCursor) {
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

  private void fireEventToTextAnnotationListeners(MouseEvent e) {
    if (myEditor.getMouseEventArea(e) == EditorMouseEventArea.ANNOTATIONS_AREA) {
      Point clickPoint = e.getPoint();

      TextAnnotationGutterProvider provider = getProviderAtPoint(clickPoint);

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

  private int getLineNumAtPoint(Point clickPoint) {
    return EditorUtil.yToLogicalLineNoCustomRenderers(myEditor, clickPoint.y);
  }

  private @Nullable TextAnnotationGutterProvider getProviderAtPoint(Point clickPoint) {
    int current = getAnnotationsAreaOffset();
    if (clickPoint.x < current) return null;
    for (TextAnnotationGutterProviderInfo info : myTextAnnotationGutterProviders) {
      current += info.size();
      if (clickPoint.x <= current) {
        return info.provider();
      }
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
    else {
      int logicalLineAtCursor = EditorUtil.yPositionToLogicalLine(myEditor, e);
      myLastActionableClick = new ClickInfo(logicalLineAtCursor, e.getPoint());
    }
  }

  private boolean isPopupAction(MouseEvent e) {
    GutterIconRenderer renderer = getGutterRenderer(e);
    return renderer != null && renderer.getClickAction() == null && getPopupMenuActions(renderer) != null;
  }

  @Override
  public void mouseReleased(MouseEvent e) {
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
    if (renderer != null) {
      var btn = e.getButton();
      if (btn == MouseEvent.BUTTON2 ||
          (btn == MouseEvent.BUTTON1 && BitUtil.isSet(e.getModifiersEx(), InputEvent.ALT_DOWN_MASK))) {
        clickAction = renderer.getMiddleButtonClickAction();
      }
      else if (btn == MouseEvent.BUTTON1) {
        clickAction = renderer.getClickAction();
      }
    }
    if (clickAction != null) {
      myLastActionableClick = new ClickInfo(EditorUtil.yPositionToLogicalLine(myEditor, e), info.iconCenterPosition);
      logGutterIconClick(renderer);

      e.consume();
      performAction(clickAction, e, ActionPlaces.EDITOR_GUTTER, DataManager.getInstance().getDataContext(this), info);
      repaint();
    }
    else {
      ActiveGutterRenderer lineRenderer = e.isConsumed() ? null : getActiveRendererByMouseEvent(e);
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

    GutterIconClickCollectors.logClick(project, language, renderer.getFeatureId(), isDumbMode(), pluginInfo);
  }

  private boolean isDumbMode() {
    Project project = myEditor.getProject();
    return project != null && !project.isDisposed() && DumbService.isDumb(project);
  }

  private boolean checkDumbAware(@NotNull Object possiblyDumbAware) {
    Project project = myEditor.getProject();
    return project != null && DumbService.getInstance(project).isUsableInCurrentContext(possiblyDumbAware);
  }

  private void notifyNotDumbAware() {
    Project project = myEditor.getProject();
    if (project != null) {
      DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
        IdeBundle.message("message.this.functionality.is.not.available.during.indexing"),
        DumbModeBlockedFunctionality.EditorGutterComponent);
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

  private @Nullable ActiveGutterRenderer getActiveRendererByMouseEvent(MouseEvent e) {
    if (findFoldingAnchorAt(e.getX(), e.getY()) != null) {
      return null;
    }
    if (e.getX() > getWhitespaceSeparatorOffset()) {
      return null;
    }
    ActiveGutterRenderer[] gutterRenderer = {null};
    int[] layer = {-1};
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
    closeTextAnnotations(getTextAnnotations());
  }

  @Override
  public void closeTextAnnotations(@NotNull Collection<? extends TextAnnotationGutterProvider> annotations) {
    if (!myCanCloseAnnotations) return;

    Set<TextAnnotationGutterProvider> toClose = new ReferenceOpenHashSet<>(annotations);
    myTextAnnotationGutterProviders.removeIf(info -> {
      TextAnnotationGutterProvider provider = info.provider();
      if (toClose.contains(provider)) {
        provider.gutterClosed();
        myProviderToListener.remove(provider);
        fireTextAnnotationGutterProviderRemoved(provider);
        return true;
      }
      return false;
    });

    updateSize();
  }

  private final class CloseAnnotationsAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.BackendOnly {
    CloseAnnotationsAction() {
      super(EditorBundle.messagePointer("close.editor.annotations.action.name"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      closeAllAnnotations();
    }
  }

  @Override
  public @Nullable Point getCenterPoint(GutterIconRenderer renderer) {
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
  public void setLineNumberConverter(@Nullable LineNumberConverter primaryConverter, @Nullable LineNumberConverter additionalConverter) {
    myLineNumberConverter = primaryConverter;
    myAdditionalLineNumberConverter = additionalConverter;
    myEditorGutterListeners.getMulticaster().lineNumberConvertersChanged();
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
  public void setLeftFreePaintersAreaState(@NotNull EditorGutterFreePainterAreaState value) {
    myLeftFreePaintersAreaState = value;
  }

  @Override
  public void setRightFreePaintersAreaState(@NotNull EditorGutterFreePainterAreaState value) {
    myRightFreePaintersAreaState = value;
  }

  @Override
  public void reserveLeftFreePaintersAreaWidth(@NotNull Disposable disposable, int widthInPixels) {
    if (widthInPixels < 0 || widthInPixels > Short.MAX_VALUE) throw new IllegalArgumentException();
    myLeftFreePaintersAreaReserveWidth += widthInPixels;
    updateSize();
    Disposer.register(disposable, () -> {
      myLeftFreePaintersAreaReserveWidth -= widthInPixels;
      updateSize(false, true);
    });
  }

  @Override
  public void reserveRightFreePaintersAreaWidth(@NotNull Disposable disposable, int widthInPixels) {
    if (widthInPixels < 0 || widthInPixels > Short.MAX_VALUE) throw new IllegalArgumentException();
    myRightFreePaintersAreaReserveWidth += widthInPixels;
    updateSize();
    Disposer.register(disposable, () -> {
      myRightFreePaintersAreaReserveWidth -= widthInPixels;
      updateSize(false, true);
    });
  }

  @Override
  public void setInitialIconAreaWidth(int width) {
    myStartIconAreaWidth = width;
  }

  private void invokePopup(MouseEvent e) {
    int logicalLineAtCursor = EditorUtil.yPositionToLogicalLine(myEditor, e);
    Point point = e.getPoint();
    PointInfo info = e instanceof StickyLineComponent.MyMouseEvent ? null : getPointInfo(point);
    if (info != null) {
      logGutterIconClick(info.renderer);
    }
    ClickInfo clickInfo = new ClickInfo(logicalLineAtCursor, info == null ? point : info.iconCenterPosition);
    EditorMouseEventArea editorArea = myEditor.getMouseEventArea(e);
    myLastActionableClick = clickInfo;

    AnAction rightButtonAction = info == null ? null : info.renderer.getRightButtonClickAction();
    if (rightButtonAction != null) {
      e.consume();
      performAction(rightButtonAction, e, ActionPlaces.EDITOR_GUTTER_POPUP, myEditor.getDataContext(), info);
      return;
    }
    EditorMouseEvent editorMouseEvent = new EditorMouseEvent(
      myEditor, e, editorArea, 0, new LogicalPosition(logicalLineAtCursor, 0),
      new VisualPosition(0, 0), true, null, null, null);
    ActionGroup group;
    if (info != null) {
      group = getPopupMenuActions(info.renderer);
    }
    else {
      group = getPopupActionGroup(editorMouseEvent);
    }
    if (group == null) {
      // nothing
    }
    else if (!checkDumbAware(group)) {
      notifyNotDumbAware();
    }
    else {
      if (info != null) addLoadingIconForGutterMark(info);
      String place = editorArea == EditorMouseEventArea.ANNOTATIONS_AREA ?
                     ActionPlaces.EDITOR_ANNOTATIONS_AREA_POPUP : ActionPlaces.EDITOR_GUTTER_POPUP;
      showGutterContextMenu(group, e, place);
    }
  }

  private static @Nullable ActionGroup getPopupMenuActions(@NotNull GutterIconRenderer renderer) {
    return renderer.getPopupMenuActions();
  }

  @Nullable ActionGroup getPopupActionGroup(@NotNull EditorMouseEvent event) {
    List<AnAction> actions;
    if (event.getArea() == EditorMouseEventArea.ANNOTATIONS_AREA) {
      actions = getTextAnnotationPopupActions(event.getLogicalPosition().line);
    }
    else {
      actions = new ArrayList<>();
      if (ExperimentalUI.isNewUI() &&
          event.getArea() == EditorMouseEventArea.LINE_NUMBERS_AREA &&
          getClientProperty("line.number.hover.icon.context.menu") instanceof ActionGroup g) {
        actions.add(g);
      }
      if (myCustomGutterPopupGroup != null) {
        actions.add(Separator.getInstance());
        actions.add(myCustomGutterPopupGroup);
      }
      else if (myShowDefaultGutterPopup) {
        ActionGroup g = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_GUTTER);
        if (g != null) {
          actions.add(Separator.getInstance());
          actions.add(g);
        }
      }
    }
    if (actions.isEmpty()) return null;
    return new EditorMousePopupActionGroup(actions, event);
  }

  private static final String EDITOR_GUTTER_CONTEXT_MENU_KEY = "editor.gutter.context.menu";

  private void showGutterContextMenu(@NotNull ActionGroup group, MouseEvent e, String place) {
    e.consume();
    ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(place, group);
    putClientProperty(EDITOR_GUTTER_CONTEXT_MENU_KEY, popupMenu);
    popupMenu.getComponent().addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        EditorGutterComponentImpl.this.putClientProperty(EDITOR_GUTTER_CONTEXT_MENU_KEY, null);
      }
    });
    popupMenu.getComponent().show(this, e.getX(), e.getY());
  }

  private boolean isGutterContextMenuShown() {
    return getClientProperty(EDITOR_GUTTER_CONTEXT_MENU_KEY) != null;
  }

  @Override
  public @NotNull List<AnAction> getTextAnnotationPopupActions(int logicalLine) {
    List<AnAction> addActions = new ArrayList<>();
    if (myCanCloseAnnotations) addActions.add(new CloseAnnotationsAction());
    //if (line >= myEditor.getDocument().getLineCount()) return;

    for (TextAnnotationGutterProviderInfo info : myTextAnnotationGutterProviders) {
      List<AnAction> list = info.provider().getPopupActions(logicalLine, myEditor);
      if (list != null) {
        for (AnAction action : list) {
          if (!addActions.contains(action)) {
            addActions.add(action);
          }
        }
      }
    }
    return addActions;
  }

  private void addLoadingIconForGutterMark(@NotNull PointInfo info) {
    ClickInfo clickInfo = myLastActionableClick;
    if (clickInfo == null) return;
    boolean[] removed = {false};
    EdtScheduler.getInstance().schedule(Registry.intValue("actionSystem.popup.progress.icon.delay", 500), () -> {
      if (myLastActionableClick != clickInfo || removed[0]) return;
      clickInfo.myProgressVisualLine = info.visualLine;
      clickInfo.myProgressGutterMark = info.renderer;
      repaint();
    });
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
    updateFreePainters(e);
    updateHover(null);
  }

  private int convertPointToLineNumber(Point p) {
    DocumentEx document = myEditor.getDocument();
    int line = EditorUtil.yPositionToLogicalLine(myEditor, p);
    if (!DocumentUtil.isValidLine(line, document)) return -1;

    int startOffset = document.getLineStartOffset(line);
    FoldRegion region = myEditor.getFoldingModel().getCollapsedRegionAtOffset(startOffset);
    if (region != null) {
      return document.getLineNumber(region.getEndOffset());
    }
    return line;
  }

  @Override
  public @Nullable GutterIconRenderer getGutterRenderer(Point p) {
    PointInfo info = getPointInfo(p);
    return info == null ? null : info.renderer;
  }

  private @Nullable PointInfo getPointInfo(@NotNull Point p) {
    int cX = convertX((int)p.getX());
    int line = myEditor.yToVisualLine(p.y);
    int[] yRange = myEditor.visualLineToYRange(line);
    if (p.y >= yRange[0] && p.y < yRange[0] + myEditor.getLineHeight()) {
      List<GutterMark> renderers = getGutterRenderers(line);
      PointInfo[] result = {null};
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

  private @Nullable PointInfo getPointInfo(@NotNull Inlay<?> inlay, int inlayY, int x, int y) {
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

  private @Nullable GutterIconRenderer getGutterRenderer(MouseEvent e) {
    return getGutterRenderer(e.getPoint());
  }

  private static @NotNull LineMarkerRendererEx.Position getLineMarkerPosition(@NotNull LineMarkerRenderer renderer) {
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
    for (TextAnnotationGutterProviderInfo info : myTextAnnotationGutterProviders) {
      info.provider().gutterClosed();
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
      accessibleContext = new AccessibleJComponent() {
      };
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

  private void onHover(boolean hovered) {
    if (ExperimentalUI.isNewUI()) {
      myHovered = hovered;
      updateFoldingOutlineVisibility();
    }
  }

  private static int getInitialLineNumberWidth() {
    if (ExperimentalUI.isNewUI()) {
      //have a placeholder for breakpoints
      return 12;
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
      if (component instanceof EditorGutterComponentImpl gutter) {
        gutter.onHover(hovered);
      }
    }
  };

  private static final class GutterIconClickCollectors extends CounterUsagesCollector {
    private static final EventLogGroup GROUP = new EventLogGroup("gutter.icon.click", 5);

    private static final BooleanEventField IS_DUMB_MODE = EventFields.Boolean("dumb");
    private static final StringEventField ICON = EventFields.StringValidatedByCustomRule("icon_id", PluginInfoValidationRule.class);

    private static final VarargEventId CLICKED = GROUP.registerVarargEvent(
      "clicked",
      EventFields.Language,
      ICON,
      IS_DUMB_MODE,
      EventFields.PluginInfo
    );

    @Override
    public EventLogGroup getGroup() {
      return GROUP;
    }

    public static void logClick(@Nullable Project project,
                                @Nullable Language language,
                                @NotNull String icon,
                                boolean isDumb,
                                @Nullable PluginInfo pluginInfo) {
      CLICKED.log(
        project,
        EventFields.Language.with(language),
        ICON.with(icon),
        IS_DUMB_MODE.with(isDumb),
        EventFields.PluginInfo.with(pluginInfo)
      );
    }
  }

  @Override
  public int getHoveredFreeMarkersLine() {
    return myHoveredFreeMarkersLine;
  }

  @Override
  public @NotNull UiInspectorInfo getUiInspectorContext(@NotNull MouseEvent event) {
    List<PropertyBean> result = new ArrayList<>();
    result.add(new PropertyBean("Use 'ide.debug.gutter.area' Registry to debug painting areas", null, true));

    Point point = event.getPoint();
    PointInfo pointInfo = getPointInfo(point);
    if (pointInfo != null) {
      result.add(new PropertyBean("Clicked Renderer", pointInfo.renderer, true));
      result.add(new PropertyBean("Clicked Renderer Class", UiInspectorUtil.getClassPresentation(pointInfo.renderer), true));
      result.add(new PropertyBean("Accessible Name", pointInfo.renderer.getAccessibleName()));
      result.add(new PropertyBean("Icon", pointInfo.renderer.getIcon(), true));
      if (pointInfo.renderer instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> lineMarkerRenderer) {
        LineMarkerInfo<?> markerInfo = lineMarkerRenderer.getLineMarkerInfo();
        result.add(new PropertyBean("Marker Info - Element", markerInfo.getElement(), true));
        if (markerInfo.getNavigationHandler() != null) {
          result.add(new PropertyBean("Marker Info - Navigation Handler", markerInfo.getNavigationHandler(), true));
        }
      }
      return new UiInspectorInfo("GutterIconRenderer", result, null);
    }

    ActiveGutterRenderer gutterRenderer = getActiveRendererByMouseEvent(event);
    if (gutterRenderer != null) {
      result.add(new PropertyBean("Clicked Renderer", gutterRenderer));
      result.add(new PropertyBean("Clicked Renderer Class", UiInspectorUtil.getClassPresentation(gutterRenderer)));
      return new UiInspectorInfo("ActiveGutterRenderer", result, null);
    }

    return new UiInspectorInfo(null, result, null);
  }

  private final class LineNumbersRepainter implements CaretListener {
    @Override
    public void caretPositionChanged(@NotNull CaretEvent event) {
      if (event.getOldPosition().line != event.getNewPosition().line &&
          event.getCaret() == event.getEditor().getCaretModel().getPrimaryCaret() &&
          getPrimaryLineNumberConverter().shouldRepaintOnCaretMovement()) {
        repaint();
      }
    }
  }
  void processTextAnnotationGutterProviders(@NotNull BiConsumer<? super TextAnnotationGutterProvider, ? super Integer> consumer) {
    for (TextAnnotationGutterProviderInfo info : myTextAnnotationGutterProviders) {
      TextAnnotationGutterProvider gutterProvider = info.provider();
      int size = info.size();
      consumer.accept(gutterProvider, size);
    }
  }
}
