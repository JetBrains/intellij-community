// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.hint.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.DropDownLink;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.util.PopupState;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.xml.util.XmlStringUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class EditorMarkupModelImpl extends MarkupModelImpl
      implements EditorMarkupModel, CaretListener, BulkAwareDocumentListener.Simple, VisibleAreaListener {
  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);
  private static final int EDITOR_FRAGMENT_POPUP_BORDER = 1;

  private static final JBValue SCROLLBAR_WIDTH = new JBValue.UIInteger("Editor.scrollBarWidth", 14);

  private static final ColorKey HOVER_BACKGROUND = ColorKey.createColorKey("ActionButton.hoverBackground",
                                                                       JBUI.CurrentTheme.ActionButton.hoverBackground());

  private static final ColorKey PRESSED_BACKGROUND = ColorKey.createColorKey("ActionButton.pressedBackground",
                                                                       JBUI.CurrentTheme.ActionButton.pressedBackground());

  private static final ColorKey ICON_TEXT_COLOR = ColorKey.createColorKey("ActionButton.iconTextForeground",
                                                                          UIUtil.getContextHelpForeground());

  private int getMinMarkHeight() {
    return JBUIScale.scale(myMinMarkHeight);
  }

  private static int getThinGap() {
    return JBUIScale.scale(2);
  }

  private static int getMaxStripeSize() {
    return JBUIScale.scale(4);
  }

  private static int getMaxMacThumbWidth() {
    return JBUIScale.scale(10);
  }

  @NotNull private final EditorImpl myEditor;
  // null renderer means we should not show traffic light icon
  @Nullable private ErrorStripeRenderer myErrorStripeRenderer;
  private final MergingUpdateQueue myErrorUpdates;
  private final List<ErrorStripeListener> myErrorMarkerListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private boolean dimensionsAreValid;
  private int myEditorScrollbarTop = -1;
  private int myEditorTargetHeight = -1;
  private int myEditorSourceHeight = -1;
  @Nullable private ProperTextRange myDirtyYPositions;
  private static final ProperTextRange WHOLE_DOCUMENT = new ProperTextRange(0, 0);

  @NotNull private ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private int myMinMarkHeight;// height for horizontal, width for vertical stripes
  private static final int myPreviewLines = Math.max(2, Math.min(25, Integer.getInteger("preview.lines", 5)));// Actually preview has myPreviewLines * 2 + 1 lines (above + below + current one)
  private static final int myCachePreviewLines = 100;// Actually cache image has myCachePreviewLines * 2 + 1 lines (above + below + current one)
  @Nullable private LightweightHint myEditorPreviewHint;
  @NotNull private final EditorFragmentRenderer myEditorFragmentRenderer;
  private final MouseMovementTracker myMouseMovementTracker = new MouseMovementTracker();
  private int myRowAdjuster;
  private int myWheelAccumulator;
  private int myLastVisualLine;
  private WeakReference<LightweightHint> myCurrentHint;
  private int myCurrentHintAnchorY;
  private boolean myKeepHint;

  private final ActionToolbar statusToolbar;
  private boolean showToolbar;
  private boolean trafficLightVisible;
  private final ComponentListener toolbarComponentListener;
  private Rectangle cachedToolbarBounds = new Rectangle();
  private final JLabel smallIconLabel;
  private AnalyzerStatus analyzerStatus;
  private InspectionPopupManager myPopupManager = new InspectionPopupManager();
  private final Disposable resourcesDisposable = Disposer.newDisposable();

  EditorMarkupModelImpl(@NotNull EditorImpl editor) {
    super(editor.getDocument());
    myEditor = editor;
    myEditorFragmentRenderer = new EditorFragmentRenderer();
    setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight());

    showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget();
    trafficLightVisible = true;

    AnAction nextErrorAction = findAction("GotoNextError", AllIcons.Actions.FindAndShowNextMatches);
    AnAction prevErrorAction = findAction("GotoPreviousError", AllIcons.Actions.FindAndShowPrevMatches);
    DefaultActionGroup navigateGroup = new DefaultActionGroup(Separator.create(), nextErrorAction, prevErrorAction) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(analyzerStatus != null && analyzerStatus.getShowNavigation());
      }
    };

    ActionGroup actions = new DefaultActionGroup(new StatusAction(), navigateGroup);
    ActionButtonLook editorButtonLook = new EditorToolbarButtonLook();
    statusToolbar = new ActionToolbarImpl(ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR, actions, true) {
      @Override
      protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D)g.create();
        try {
          Rectangle rect = new Rectangle(getSize());
          int leftGradientWidth = JBUIScale.scale(5);
          rect.x += leftGradientWidth;
          rect.width -= leftGradientWidth;

          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                              MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

          g2.setColor(myEditor.getBackgroundColor());
          g2.fill(rect);

          g2.setPaint(new GradientPaint(0, 0, ColorUtil.withAlpha(myEditor.getBackgroundColor(), 0),
                                        leftGradientWidth, 0, myEditor.getBackgroundColor()));
          g2.fillRect(0, 0, leftGradientWidth, getHeight());
        }
        finally {
          g2.dispose();
        }

        super.paintComponent(g);
      }

      @Override
      @NotNull
      protected Color getSeparatorColor() {
        Color separatorColor = myEditor.getColorsScheme().getColor(EditorColors.SEPARATOR_BELOW_COLOR);
        return separatorColor != null ? separatorColor : super.getSeparatorColor();
      }

      @NotNull
      @Override
      protected ActionButton createToolbarButton(@NotNull AnAction action, ActionButtonLook look,
                                                 @NotNull String place, @NotNull Presentation presentation,
                                                 @NotNull Dimension minimumSize) {

        ActionButton actionButton = new ActionButton(action, presentation, place, minimumSize) {

          @Override
          public void updateIcon() {
            super.updateIcon();
            revalidate();
            repaint();
          }

          @Override
          public Dimension getPreferredSize() {
            Icon icon = getIcon();
            Dimension size = new Dimension(Math.max(icon.getIconWidth(), DEFAULT_MINIMUM_BUTTON_SIZE.width),
                                           Math.max(icon.getIconHeight(), DEFAULT_MINIMUM_BUTTON_SIZE.height));

            JBInsets.addTo(size, getInsets());
            return size;
          }
        };

        actionButton.setLook(editorButtonLook);
        return actionButton;
      }
    };

    statusToolbar.setMiniMode(true);
    toolbarComponentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        Component toolbar = event.getComponent();
        if (toolbar.getWidth() > 0 && toolbar.getHeight() > 0) {
          updateTrafficLightVisibility();
        }
      }
    };
    statusToolbar.getComponent().addComponentListener(toolbarComponentListener);
    statusToolbar.getComponent().setBorder(JBUI.Borders.emptyTop(1));

    smallIconLabel = new JLabel();
    smallIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        myPopupManager.showPopup(event);
      }
    });
    smallIconLabel.setOpaque(false);
    smallIconLabel.setBackground(new JBColor(() -> myEditor.getColorsScheme().getDefaultBackground()));
    smallIconLabel.setVisible(false);

    JPanel statusPanel = new NonOpaquePanel();
    statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
    statusPanel.add(statusToolbar.getComponent());
    statusPanel.add(smallIconLabel);

    ((JBScrollPane)myEditor.getScrollPane()).setStatusComponent(statusPanel);

    MessageBus bus = ApplicationManager.getApplication().getMessageBus();

    bus.connect(resourcesDisposable).subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (action instanceof HintManagerImpl.ActionToIgnore) return;
        myPopupManager.hidePopup();
      }
    });

    bus.connect(resourcesDisposable).subscribe(LafManagerListener.TOPIC, source -> myPopupManager.updateUI());
    bus.connect(resourcesDisposable).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget() &&
                      (analyzerStatus == null || analyzerStatus.getController().enableToolbar());

        updateTrafficLightVisibility();
      }
    });
    myErrorUpdates = new MergingUpdateQueue(getClass().getName(), 50, true, MergingUpdateQueue.ANY_COMPONENT, resourcesDisposable);
  }

  @Override
  public void caretPositionChanged(@NotNull CaretEvent event) {
    updateTrafficLightVisibility();
  }

  @Override
  public void afterDocumentChange(@NotNull Document document) {
    myPopupManager.hidePopup();
    updateTrafficLightVisibility();
  }

  @Override
  public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
    updateTrafficLightVisibility();
  }

  private void updateTrafficLightVisibility() {
    if (trafficLightVisible) {
      if (showToolbar) {
        VisualPosition pos = myEditor.getCaretModel().getPrimaryCaret().getVisualPosition();
        Point point = myEditor.visualPositionToXY(pos);
        point = SwingUtilities.convertPoint(myEditor.getContentComponent(), point, myEditor.getScrollPane());

        JComponent stComponent = statusToolbar.getComponent();
        if (stComponent.isVisible()) {
          Rectangle bounds = SwingUtilities.convertRectangle(stComponent, stComponent.getBounds(), myEditor.getScrollPane());

          if (!bounds.isEmpty() && bounds.contains(point)) {
            cachedToolbarBounds = bounds;
            stComponent.setVisible(false);
            smallIconLabel.setVisible(true);
          }
        }
        else if (!cachedToolbarBounds.contains(point)) {
          stComponent.setVisible(true);
          smallIconLabel.setVisible(false);
        }
      }
      else {
        statusToolbar.getComponent().setVisible(false);
        smallIconLabel.setVisible(true);
      }
    }
    else {
      statusToolbar.getComponent().setVisible(false);
      smallIconLabel.setVisible(false);
    }
  }

  private static AnAction findAction(@NotNull String id, @NotNull Icon icon) {
    ActionManager am = ActionManager.getInstance();
    AnAction action = am.getAction(id);

    action.getTemplatePresentation().setIcon(icon);
    action.getTemplatePresentation().setDisabledIcon(IconLoader.getDisabledIcon(icon));
    return action;
  }

  private int offsetToLine(int offset, @NotNull Document document) {
    if (offset < 0) {
      return 0;
    }
    if (offset > document.getTextLength()) {
      return myEditor.getVisibleLineCount();
    }
    return myEditor.offsetToVisualLine(offset);
  }

  private void repaintVerticalScrollBar() {
    myEditor.getVerticalScrollBar().repaint();
  }

  void recalcEditorDimensions() {
    EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
    int scrollBarHeight = Math.max(0, scrollBar.getSize().height);

    myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight()/* + 1*/;
    assert myEditorScrollbarTop>=0;
    int editorScrollbarBottom = scrollBar.getIncScrollButtonHeight();
    myEditorTargetHeight = scrollBarHeight - myEditorScrollbarTop - editorScrollbarBottom;
    myEditorSourceHeight = myEditor.getPreferredHeight();

    dimensionsAreValid = scrollBarHeight != 0;
  }

  public void setTrafficLightIconVisible(boolean value) {
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {

      if (value != trafficLightVisible) {
        trafficLightVisible = value;
        updateTrafficLightVisibility();
      }
      repaint();
    }
  }

  public void repaintTrafficLightIcon() {
    if (myErrorStripeRenderer == null) return;
    
    myErrorUpdates.queue(Update.create(this, () -> {
      if (myErrorStripeRenderer != null) {
        AnalyzerStatus newStatus = myErrorStripeRenderer.getStatus(myEditor);
        if (!AnalyzerStatus.equals(newStatus, analyzerStatus)) {
          changeStatus(newStatus);
        }
      }
    }));
  }

  private void changeStatus(AnalyzerStatus newStatus) {
    analyzerStatus = newStatus;
    smallIconLabel.setIcon(analyzerStatus.getIcon());

    if (showToolbar != analyzerStatus.getController().enableToolbar()) {
      showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget() &&
                    analyzerStatus.getController().enableToolbar();
      updateTrafficLightVisibility();
    }

    myPopupManager.updateVisiblePopup();
    ActivityTracker.getInstance().inc();
  }

  private static class PositionedStripe {
    @NotNull private Color color;
    private int yEnd;
    private final boolean thin;
    private final int layer;

    private PositionedStripe(@NotNull Color color, int yEnd, boolean thin, int layer) {
      this.color = color;
      this.yEnd = yEnd;
      this.thin = thin;
      this.layer = layer;
    }
  }

  private LightweightHint getCurrentHint() {
    if (myCurrentHint == null) return null;
    LightweightHint hint = myCurrentHint.get();
    if (hint == null || !hint.isVisible()) {
      myCurrentHint = null;
      hint = null;
    }
    return hint;
  }

  @NotNull
  private static Rectangle getBoundsOnScreen(@NotNull LightweightHint hint) {
    JComponent component = hint.getComponent();
    Point location = hint.getLocationOn(component);
    SwingUtilities.convertPointToScreen(location, component);
    return new Rectangle(location, hint.getSize());
  }

  private boolean showToolTipByMouseMove(@NotNull final MouseEvent e) {
    MouseEvent me = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), 0, e.getY() + 1, e.getClickCount(),
                                              e.isPopupTrigger());

    boolean newLook = Registry.is("editor.new.mouse.hover.popups");
    LightweightHint currentHint = getCurrentHint();
    if (newLook && currentHint != null) {
      if (myKeepHint || myMouseMovementTracker.isMovingTowards(e, getBoundsOnScreen(currentHint))) {
        return true;
      }
    }

    final int visualLine = getVisualLineByEvent(e);
    myLastVisualLine = visualLine;
    Rectangle area = myEditor.getScrollingModel().getVisibleArea();
    int visualY = myEditor.visualLineToY(visualLine);
    boolean isVisible = myWheelAccumulator == 0 && area.contains(area.x, visualY);

    if (UIUtil.uiParents(myEditor.getComponent(), false).filter(EditorWindowHolder.class).isEmpty() || isVisible || !UISettings.getInstance().getShowEditorToolTip()) {
      final Set<RangeHighlighter> highlighters = new THashSet<>();
      getNearestHighlighters(this, me.getY(), highlighters);
      getNearestHighlighters(((EditorEx)getEditor()).getFilteredDocumentMarkupModel(), me.getY(), highlighters);
      if (highlighters.isEmpty()) return false;

      int y = e.getY();
      RangeHighlighter nearest = getNearestRangeHighlighter(e);
      if (nearest != null) {
        ProperTextRange range = offsetsToYPositions(nearest.getStartOffset(), nearest.getEndOffset());
        int eachStartY = range.getStartOffset();
        int eachEndY = range.getEndOffset();
        y = eachStartY + (eachEndY - eachStartY) / 2;
      }
      if (newLook && currentHint != null && y == myCurrentHintAnchorY) return true;
      me = new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), me.getX(), y + 1, e.getClickCount(),
                          e.isPopupTrigger());
      TooltipRenderer bigRenderer = myTooltipRendererProvider.calcTooltipRenderer(highlighters);
      if (bigRenderer != null) {
        LightweightHint hint = showTooltip(bigRenderer, createHint(me).setForcePopup(newLook));
        myCurrentHint = new WeakReference<>(hint);
        myCurrentHintAnchorY = y;
        myKeepHint = false;
        myMouseMovementTracker.reset();
        return true;
      }
      return false;
    } else {
      float rowRatio = (float)visualLine /(myEditor.getVisibleLineCount() - 1);
      int y = myRowAdjuster != 0 ? (int)(rowRatio * myEditor.getVerticalScrollBar().getHeight()) : me.getY();
      me = new MouseEvent(me.getComponent(), me.getID(), me.getWhen(), me.getModifiers(), me.getX(), y, me.getClickCount(), me.isPopupTrigger());
      final List<RangeHighlighterEx> highlighters = new ArrayList<>();
      collectRangeHighlighters(this, visualLine, highlighters);
      collectRangeHighlighters(myEditor.getFilteredDocumentMarkupModel(), visualLine, highlighters);
      myEditorFragmentRenderer.update(visualLine, highlighters, me.isAltDown());
      myEditorFragmentRenderer.show(myEditor, me.getPoint(), true, ERROR_STRIPE_TOOLTIP_GROUP, createHint(me));
      return true;
    }
  }

  private static HintHint createHint(MouseEvent me) {
    return new HintHint(me)
      .setAwtTooltip(true)
      .setPreferredPosition(Balloon.Position.atLeft)
      .setBorderInsets(JBUI.insets(EDITOR_FRAGMENT_POPUP_BORDER))
      .setShowImmediately(true)
      .setAnimationEnabled(false);
  }

  private int getVisualLineByEvent(@NotNull MouseEvent e) {
    int y = e.getY();
    if (e.getSource() == myEditor.getVerticalScrollBar() && y == myEditor.getVerticalScrollBar().getHeight() - 1) {
      y++;
    }
    return fitLineToEditor(myEditor.offsetToVisualLine(yPositionToOffset(y + myWheelAccumulator, true)));
  }

  private int fitLineToEditor(int visualLine) {
    int lineCount = myEditor.getVisibleLineCount();
    int shift = 0;
    if (visualLine >= lineCount - 1) {
      CharSequence sequence = myEditor.getDocument().getCharsSequence();
      shift = sequence.length() < 1 ? 0 : sequence.charAt(sequence.length() - 1) == '\n' ? 1 : 0;
    }
    return Math.max(0, Math.min(lineCount - shift, visualLine));
  }

  private int getOffset(int visualLine, boolean startLine) {
    LogicalPosition pos = myEditor.visualToLogicalPosition(new VisualPosition(visualLine, startLine ? 0 : Integer.MAX_VALUE));
    return myEditor.logicalPositionToOffset(pos);
  }

  private void collectRangeHighlighters(@NotNull MarkupModelEx markupModel, final int visualLine, @NotNull final Collection<? super RangeHighlighterEx> highlighters) {
    final int startOffset = getOffset(fitLineToEditor(visualLine - myPreviewLines), true);
    final int endOffset = getOffset(fitLineToEditor(visualLine + myPreviewLines), false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      Object tooltip = highlighter.getErrorStripeTooltip();
      if (tooltip != null &&
          !(tooltip instanceof HighlightInfo && ((HighlightInfo)tooltip).type == HighlightInfoType.TODO) &&
          highlighter.getErrorStripeMarkColor() != null &&
          highlighter.getStartOffset() < endOffset &&
          highlighter.getEndOffset() > startOffset) {
        highlighters.add(highlighter);
      }
      return true;
    });
  }

  @Nullable
  private RangeHighlighter getNearestRangeHighlighter(@NotNull final MouseEvent e) {
    List<RangeHighlighter> highlighters = new ArrayList<>();
    getNearestHighlighters(this, e.getY(), highlighters);
    getNearestHighlighters(myEditor.getFilteredDocumentMarkupModel(), e.getY(), highlighters);
    RangeHighlighter nearestMarker = null;
    int yPos = 0;
    for (RangeHighlighter highlighter : highlighters) {
      final int newYPos = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset()).getStartOffset();

      if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
        nearestMarker = highlighter;
        yPos = newYPos;
      }
    }
    return nearestMarker;
  }

  private void getNearestHighlighters(@NotNull MarkupModelEx markupModel,
                                      final int scrollBarY,
                                      @NotNull final Collection<? super RangeHighlighter> nearest) {
    int startOffset = yPositionToOffset(scrollBarY - getMinMarkHeight(), true);
    int endOffset = yPositionToOffset(scrollBarY + getMinMarkHeight(), false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      if (highlighter.getErrorStripeMarkColor() != null) {
        ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
        if (scrollBarY >= range.getStartOffset() - getMinMarkHeight() * 2 &&
            scrollBarY <= range.getEndOffset() + getMinMarkHeight() * 2) {
          nearest.add(highlighter);
        }
      }
      return true;
    });
  }

  private void doClick(@NotNull final MouseEvent e) {
    RangeHighlighter marker = getNearestRangeHighlighter(e);
    int offset;
    LogicalPosition logicalPositionToScroll = null;
    if (marker == null) {
      if (myEditorPreviewHint != null) {
        logicalPositionToScroll = myEditor.visualToLogicalPosition(new VisualPosition(myEditorFragmentRenderer.myStartVisualLine, 0));
        offset = myEditor.getDocument().getLineStartOffset(logicalPositionToScroll.line);
      } else {
        return;
      }
    } else {
      offset = marker.getStartOffset();
    }

    final Document doc = myEditor.getDocument();
    if (doc.getLineCount() > 0 && myEditorPreviewHint == null) {
      // Necessary to expand folded block even if navigating just before one
      // Very useful when navigating to first unused import statement.
      int lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset));
      myEditor.getCaretModel().moveToOffset(lineEnd);
    }
    myEditor.getCaretModel().removeSecondaryCarets();
    myEditor.getCaretModel().moveToOffset(offset);
    myEditor.getSelectionModel().removeSelection();
    ScrollingModel scrollingModel = myEditor.getScrollingModel();
    scrollingModel.disableAnimation();
    if (logicalPositionToScroll != null) {
      int lineY = myEditor.logicalPositionToXY(logicalPositionToScroll).y;
      int relativePopupOffset = myEditorFragmentRenderer.myRelativeY;
      scrollingModel.scrollVertically(lineY - relativePopupOffset);
    }
    else {
      scrollingModel.scrollToCaret(ScrollType.CENTER);
    }
    scrollingModel.enableAnimation();
    if (marker != null) {
      fireErrorMarkerClicked(marker, e);
    }
  }

  @Override
  public void setErrorStripeVisible(boolean val) {
    if (val) {
      disposeErrorPanel();
      MyErrorPanel panel = new MyErrorPanel();
      myEditor.getVerticalScrollBar().setPersistentUI(panel);
    }
    else {
      myEditor.getVerticalScrollBar().setPersistentUI(JBScrollBar.createUI(null));
    }
  }

  @Nullable
  private MyErrorPanel getErrorPanel() {
    ScrollBarUI ui = myEditor.getVerticalScrollBar().getUI();
    return ui instanceof MyErrorPanel ? (MyErrorPanel)ui : null;
  }

  @Override
  public void setErrorPanelPopupHandler(@NotNull PopupHandler handler) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.setPopupHandler(handler);
    }
  }

  @Override
  public void setErrorStripTooltipRendererProvider(@NotNull final ErrorStripTooltipRendererProvider provider) {
    myTooltipRendererProvider = provider;
  }

  @Override
  @NotNull
  public ErrorStripTooltipRendererProvider getErrorStripTooltipRendererProvider() {
    return myTooltipRendererProvider;
  }

  @Override
  @NotNull
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public void setErrorStripeRenderer(@Nullable ErrorStripeRenderer renderer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }
    myErrorStripeRenderer = renderer;
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();
  }

  @Nullable
  @Override
  public ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  @Override
  public void dispose() {
    disposeErrorPanel();

    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }

    statusToolbar.getComponent().removeComponentListener(toolbarComponentListener);
    ((JBScrollPane)myEditor.getScrollPane()).setStatusComponent(null);

    myErrorStripeRenderer = null;
    myTooltipRendererProvider = new BasicTooltipRendererProvider();
    myEditorPreviewHint = null;

    myPopupManager.hidePopup();
    myPopupManager = null;

    Disposer.dispose(resourcesDisposable);

    super.dispose();
  }

  private void disposeErrorPanel() {
    MyErrorPanel panel = getErrorPanel();
    if (panel != null) {
      panel.uninstallListeners();
    }
  }

  void repaint() {
    repaint(-1, -1);
  }

  // startOffset == -1 || endOffset == -1 means whole document
  void repaint(int startOffset, int endOffset) {
    ProperTextRange range = offsetsToYPositions(startOffset, endOffset);
    markDirtied(range);
    if (startOffset == -1 || endOffset == -1) {
      myDirtyYPositions = WHOLE_DOCUMENT;
    }

    JScrollBar bar = myEditor.getVerticalScrollBar();
    bar.repaint(0, range.getStartOffset(), bar.getWidth(), range.getLength() + getMinMarkHeight());
  }

  private boolean isMirrored() {
    return myEditor.isMirrored();
  }

  private boolean transparent() {
    return !myEditor.shouldScrollBarBeOpaque();
  }

  @DirtyUI
  private class MyErrorPanel extends ButtonlessScrollBarUI implements MouseMotionListener, MouseListener, MouseWheelListener, UISettingsListener {
    private PopupHandler myHandler;
    @Nullable private BufferedImage myCachedTrack;
    private int myCachedHeight = -1;

    public void dropCache() {
      myCachedTrack = null;
      myCachedHeight = -1;
    }

    @Override
    public boolean alwaysShowTrack() {
      if (scrollbar.getOrientation() == Adjustable.VERTICAL) return !transparent();
      return super.alwaysShowTrack();
    }

    @Override
    public void installUI(JComponent c) {
      super.installUI(c);
      dropCache();
    }

    @Override
    public void uninstallUI(@NotNull JComponent c) {
      super.uninstallUI(c);
      dropCache();
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      scrollbar.addMouseMotionListener(this);
      scrollbar.addMouseListener(this);
      scrollbar.addMouseWheelListener(this);
    }

    @Override
    protected void uninstallListeners() {
      scrollbar.removeMouseMotionListener(this);
      scrollbar.removeMouseListener(this);
      super.uninstallListeners();
    }

    @Override
    public void uiSettingsChanged(@NotNull UISettings uiSettings) {
      if (!uiSettings.getShowEditorToolTip()) {
        hideMyEditorPreviewHint();
      }
      setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight());
      repaintTrafficLightIcon();
      repaintVerticalScrollBar();

      myPopupManager.updateVisiblePopup();
    }

    @Override
    protected void paintThumb(@NotNull Graphics g, @NotNull JComponent c, Rectangle thumbBounds) {
      if (isMacOverlayScrollbar()) {
        if (!isMirrored()) {
          super.paintThumb(g, c, thumbBounds);
        }
        else {
          Graphics2D g2d = (Graphics2D)g;
          AffineTransform old = g2d.getTransform();
          AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
          tx.translate(-c.getWidth(), 0);
          g2d.transform(tx);
          super.paintThumb(g, c, thumbBounds);
          g2d.setTransform(old);
        }
      }
      else {
        super.paintThumb(g, c, thumbBounds);
      }
    }

    @Override
    protected boolean isThumbTranslucent() {
      return true;
    }

    @Override
    protected int getThumbOffset(int value) {
      if (SystemInfo.isMac || Registry.is("editor.full.width.scrollbar")) return getMinMarkHeight() + JBUIScale.scale(2);
      return super.getThumbOffset(value);
    }

    @Override
    protected boolean isDark() {
      return myEditor.isDarkEnough();
    }

    @Override
    protected boolean alwaysPaintThumb() {
      return true;
    }

    @Override
    protected Rectangle getMacScrollBarBounds(Rectangle baseBounds, boolean thumb) {
      Rectangle bounds = super.getMacScrollBarBounds(baseBounds, thumb);
      bounds.width = Math.min(bounds.width, getMaxMacThumbWidth());
      int b2 =  bounds.width / 2;
      bounds.x = getThinGap() + getMinMarkHeight() + SCROLLBAR_WIDTH.get() / 2 - b2;

      return bounds;
    }

    @Override
    protected int getThickness() {
      return SCROLLBAR_WIDTH.get() + getThinGap() + getMinMarkHeight();
    }

    @Override
    protected void paintTrack(@NotNull Graphics g, @NotNull JComponent c, @NotNull Rectangle trackBounds) {
      if (myEditor.isDisposed()) return;
      if (transparent()) {
        doPaintTrack(g, c, trackBounds);
      }
      else {
        super.paintTrack(g, c, trackBounds);
      }
    }

    @Override
    protected void doPaintTrack(@NotNull Graphics g, @NotNull JComponent c, @NotNull Rectangle bounds) {
      Rectangle clip = g.getClipBounds().intersection(bounds);
      if (clip.height == 0) return;

      Rectangle componentBounds = c.getBounds();
      ProperTextRange docRange = ProperTextRange.create(0, componentBounds.height);
      if (myCachedTrack == null || myCachedHeight != componentBounds.height) {
        myCachedTrack = UIUtil.createImage(c, componentBounds.width, componentBounds.height, BufferedImage.TYPE_INT_ARGB);
        myCachedHeight = componentBounds.height;
        myDirtyYPositions = docRange;
        dimensionsAreValid = false;
        paintTrackBasement(myCachedTrack.getGraphics(), new Rectangle(0, 0, componentBounds.width, componentBounds.height));
      }
      if (myDirtyYPositions == WHOLE_DOCUMENT) {
        myDirtyYPositions = docRange;
      }
      if (myDirtyYPositions != null) {
        final Graphics2D imageGraphics = myCachedTrack.createGraphics();

        myDirtyYPositions = myDirtyYPositions.intersection(docRange);
        if (myDirtyYPositions == null) myDirtyYPositions = docRange;
        repaint(imageGraphics, componentBounds.width, myDirtyYPositions);
        myDirtyYPositions = null;
      }

      UIUtil.drawImage(g, myCachedTrack, null, 0, 0);
    }

    private void paintTrackBasement(@NotNull Graphics g, @NotNull Rectangle bounds) {
      if (transparent()) {
        Graphics2D g2 = (Graphics2D)g;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
      }
      else {
        g.setColor(myEditor.getBackgroundColor());
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    @NotNull
    @Override
    protected Color adjustColor(Color c) {
      return isMacOverlayScrollbar() ? super.adjustColor(c) : EditorImpl.adjustThumbColor(super.adjustColor(c), isDark());
    }

    private void repaint(@NotNull final Graphics g, int gutterWidth, @NotNull ProperTextRange yrange) {
      final Rectangle clip = new Rectangle(0, yrange.getStartOffset(), gutterWidth, yrange.getLength() + getMinMarkHeight());
      paintTrackBasement(g, clip);

      int startOffset = yPositionToOffset(clip.y - getMinMarkHeight(), true);
      int endOffset = yPositionToOffset(clip.y + clip.height, false);

      Shape oldClip = g.getClip();
      g.clipRect(clip.x, clip.y, clip.width, clip.height);

      drawMarkup(g, startOffset, endOffset,
                 myEditor.getFilteredDocumentMarkupModel(), EditorMarkupModelImpl.this);

      g.setClip(oldClip);
    }

    private void drawMarkup(@NotNull final Graphics g, int startOffset, int endOffset, @NotNull MarkupModelEx markup1, @NotNull MarkupModelEx markup2) {
      final Queue<PositionedStripe> thinEnds = new PriorityQueue<>(5, Comparator.comparingInt(o -> o.yEnd));
      final Queue<PositionedStripe> wideEnds = new PriorityQueue<>(5, Comparator.comparingInt(o -> o.yEnd));
      // sorted by layer
      final List<PositionedStripe> thinStripes = new ArrayList<>(); // layer desc
      final List<PositionedStripe> wideStripes = new ArrayList<>(); // layer desc
      final int[] thinYStart = new int[1];  // in range 0..yStart all spots are drawn
      final int[] wideYStart = new int[1];  // in range 0..yStart all spots are drawn

      MarkupIterator<RangeHighlighterEx> iterator1 = markup1.overlappingIterator(startOffset, endOffset, false, true);
      MarkupIterator<RangeHighlighterEx> iterator2 = markup2.overlappingIterator(startOffset, endOffset, false, true);
      MarkupIterator<RangeHighlighterEx> iterator =
        MarkupIterator.mergeIterators(iterator1, iterator2, RangeHighlighterEx.BY_AFFECTED_START_OFFSET);
      try {
        ContainerUtil.process(iterator, highlighter -> {
          Color color = highlighter.getErrorStripeMarkColor();
          if (color == null) return true;
          boolean isThin = highlighter.isThinErrorStripeMark();
          int[] yStart = isThin ? thinYStart : wideYStart;
          List<PositionedStripe> stripes = isThin ? thinStripes : wideStripes;
          Queue<PositionedStripe> ends = isThin ? thinEnds : wideEnds;

          ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
          final int ys = range.getStartOffset();
          int ye = range.getEndOffset();
          if (ye - ys < getMinMarkHeight()) ye = ys + getMinMarkHeight();

          yStart[0] = drawStripesEndingBefore(ys, ends, stripes, g, yStart[0]);

          final int layer = highlighter.getLayer();

          PositionedStripe stripe = null;
          int i;
          for (i = 0; i < stripes.size(); i++) {
            PositionedStripe s = stripes.get(i);
            if (s.layer == layer) {
              stripe = s;
              break;
            }
            if (s.layer < layer) {
              break;
            }
          }
          if (stripe == null) {
            // started new stripe, draw previous above
            if (i == 0 && yStart[0] != ys) {
              if (!stripes.isEmpty()) {
                PositionedStripe top = stripes.get(0);
                drawSpot(g, top.thin, yStart[0], ys, top.color);
              }
              yStart[0] = ys;
            }
            stripe = new PositionedStripe(color, ye, isThin, layer);
            stripes.add(i, stripe);
            ends.offer(stripe);
          }
          else {
            if (stripe.yEnd < ye) {
              if (!color.equals(stripe.color)) {
                // paint previous stripe on this layer
                if (i == 0 && yStart[0] != ys) {
                  drawSpot(g, stripe.thin, yStart[0], ys, stripe.color);
                  yStart[0] = ys;
                }
                stripe.color = color;
              }

              // key changed, reinsert into queue
              ends.remove(stripe);
              stripe.yEnd = ye;
              ends.offer(stripe);
            }
          }

          return true;
        });
      }
      finally {
        iterator.dispose();
      }

      drawStripesEndingBefore(Integer.MAX_VALUE, thinEnds, thinStripes, g, thinYStart[0]);
      drawStripesEndingBefore(Integer.MAX_VALUE, wideEnds, wideStripes, g, wideYStart[0]);
    }

    private int drawStripesEndingBefore(int ys,
                                        @NotNull Queue<? extends PositionedStripe> ends,
                                        @NotNull List<PositionedStripe> stripes,
                                        @NotNull Graphics g, int yStart) {
      while (!ends.isEmpty()) {
        PositionedStripe endingStripe = ends.peek();
        if (endingStripe == null || endingStripe.yEnd > ys) break;
        ends.remove();

        // check whether endingStripe got obscured in the range yStart..endingStripe.yEnd
        int i = stripes.indexOf(endingStripe);
        stripes.remove(i);
        if (i == 0) {
          // visible
          drawSpot(g, endingStripe.thin, yStart, endingStripe.yEnd, endingStripe.color);
          yStart = endingStripe.yEnd;
        }
      }
      return yStart;
    }

    private void drawSpot(@NotNull Graphics g, boolean thinErrorStripeMark, int yStart, int yEnd, @NotNull Color color) {
      int paintWidth;
      int x;
      if (thinErrorStripeMark) {
        paintWidth = getMinMarkHeight();
        x = isMirrored() ? getThickness() - paintWidth : 0;
        if (yEnd - yStart < 6) {
          yStart -= 1;
          yEnd += yEnd - yStart - 1;
        }
      }
      else {
        x = isMirrored() ? 0 : getMinMarkHeight() + getThinGap();
        paintWidth = SCROLLBAR_WIDTH.get();
      }
      g.setColor(color);
      g.fillRect(x, yStart, paintWidth, yEnd - yStart);
    }

    // mouse events
    @Override
    public void mouseClicked(@NotNull final MouseEvent e) {
      CommandProcessor.getInstance().executeCommand(myEditor.getProject(), () -> doMouseClicked(e),
                                                    EditorBundle.message("move.caret.command.name"),
                                                    DocCommandGroupId.noneGroupId(getDocument()), UndoConfirmationPolicy.DEFAULT,
                                                    getDocument()
      );
    }

    @Override
    public void mousePressed(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent e) {
    }

    private int getWidth() {
      return scrollbar.getWidth();
    }

    private void doMouseClicked(@NotNull MouseEvent e) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }
      if (e.getX() > 0 && e.getX() <= getWidth()) {
        doClick(e);
      }
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent e) {
      EditorImpl.MyScrollBar scrollBar = myEditor.getVerticalScrollBar();
      int buttonHeight = scrollBar.getDecScrollButtonHeight();
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
        return;
      }

      if (e.getY() < buttonHeight && myErrorStripeRenderer != null) {
        showTrafficLightTooltip(e);
        return;
      }

      if (e.getX() > 0 && e.getX() <= getWidth() && showToolTipByMouseMove(e)) {
        UIUtil.setCursor(scrollbar, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return;
      }

      cancelMyToolTips(e, false);

      if (scrollbar.getCursor().equals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    @Override
    public void mouseWheelMoved(@NotNull MouseWheelEvent e) {
      if (myEditorPreviewHint == null) {
        // process wheel event by the parent scroll pane if no code lens
        MouseEventAdapter.redispatch(e, e.getComponent().getParent());
        return;
      }
      int units = e.getUnitsToScroll();
      if (units == 0) return;
      // Stop accumulating when the last or the first line has been reached as 'adjusted' position to show lens.
      if (myLastVisualLine < myEditor.getVisibleLineCount() - 1 && units > 0 || myLastVisualLine > 0 && units < 0) {
        myWheelAccumulator += units;
      }
      myRowAdjuster = myWheelAccumulator / myEditor.getLineHeight();
      showToolTipByMouseMove(e);
    }

    @Nullable private TrafficTooltipRenderer myTrafficTooltipRenderer;

    private void showTrafficLightTooltip(@NotNull MouseEvent e) {
      if (myTrafficTooltipRenderer == null) {
        myTrafficTooltipRenderer = myTooltipRendererProvider.createTrafficTooltipRenderer(() -> myTrafficTooltipRenderer = null, myEditor);
      }
      showTooltip(myTrafficTooltipRenderer, new HintHint(e).setAwtTooltip(true).setMayCenterPosition(true).setContentActive(false)
        .setPreferredPosition(Balloon.Position.atLeft));
    }

    private void cancelMyToolTips(final MouseEvent e, boolean checkIfShouldSurvive) {
      hideMyEditorPreviewHint();
      final TooltipController tooltipController = TooltipController.getInstance();
      if (!checkIfShouldSurvive || !tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP, e, true);
      }
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      if (Registry.is("editor.new.mouse.hover.popups")) {
        hideMyEditorPreviewHint();
        LightweightHint currentHint = getCurrentHint();
        if (currentHint != null && !myKeepHint) {
          closeHintOnMovingMouseAway(currentHint);
        }
      }
      else {
        cancelMyToolTips(e, true);
      }
    }

    private void closeHintOnMovingMouseAway(LightweightHint hint) {
      Disposable disposable = Disposer.newDisposable();
      IdeEventQueue.getInstance().addDispatcher(e -> {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
          myKeepHint = true;
          Disposer.dispose(disposable);
        }
        else if (e.getID() == MouseEvent.MOUSE_MOVED && !hint.isInsideHint(new RelativePoint((MouseEvent)e))) {
          hint.hide();
          Disposer.dispose(disposable);
        }
        return false;
      }, disposable);
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      cancelMyToolTips(e, true);
    }

    private void setPopupHandler(@NotNull PopupHandler handler) {
      if (myHandler != null) {
        scrollbar.removeMouseListener(myHandler);
      }

      myHandler = handler;
      scrollbar.addMouseListener(handler);
    }
  }

  private void hideMyEditorPreviewHint() {
    if (myEditorPreviewHint != null) {
      myEditorPreviewHint.hide();
      myEditorPreviewHint = null;
      myRowAdjuster = 0;
      myWheelAccumulator = 0;
      myLastVisualLine = 0;
    }
  }

  private LightweightHint showTooltip(final TooltipRenderer tooltipObject, @NotNull HintHint hintHint) {
    if (Registry.is("editor.new.mouse.hover.popups")) {
      hideMyEditorPreviewHint();
    }
    return TooltipController.getInstance().showTooltipByMouseMove(myEditor, hintHint.getTargetPoint(), tooltipObject,
                                                                  myEditor.getVerticalScrollbarOrientation() ==
                                                                  EditorEx.VERTICAL_SCROLLBAR_RIGHT, ERROR_STRIPE_TOOLTIP_GROUP, hintHint);
  }

  private void fireErrorMarkerClicked(RangeHighlighter marker, MouseEvent e) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ErrorStripeEvent event = new ErrorStripeEvent(getEditor(), e, marker);
    for (ErrorStripeListener listener : myErrorMarkerListeners) {
      listener.errorMarkerClicked(event);
    }
  }

  @Override
  public void addErrorMarkerListener(@NotNull final ErrorStripeListener listener, @NotNull Disposable parent) {
    ContainerUtil.add(listener, myErrorMarkerListeners, parent);
  }

  private void markDirtied(@NotNull ProperTextRange yPositions) {
    if (myDirtyYPositions != WHOLE_DOCUMENT) {
      int start = Math.max(0, yPositions.getStartOffset() - myEditor.getLineHeight());
      int end = myEditorScrollbarTop + myEditorTargetHeight == 0 ? yPositions.getEndOffset() + myEditor.getLineHeight()
                                                                 : Math
                  .min(myEditorScrollbarTop + myEditorTargetHeight, yPositions.getEndOffset() + myEditor.getLineHeight());
      ProperTextRange adj = new ProperTextRange(start, Math.max(end, start));

      myDirtyYPositions = myDirtyYPositions == null ? adj : myDirtyYPositions.union(adj);
    }

    myEditorScrollbarTop = 0;
    myEditorSourceHeight = 0;
    myEditorTargetHeight = 0;
    dimensionsAreValid = false;
  }

  @Override
  public void setMinMarkHeight(int minMarkHeight) {
    myMinMarkHeight = Math.min(minMarkHeight, getMaxStripeSize());
  }

  @Override
  public boolean isErrorStripeVisible() {
    return getErrorPanel() != null;
  }

  private static class BasicTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final Collection<? extends RangeHighlighter> highlighters) {
      LineTooltipRenderer bigRenderer = null;
      //do not show same tooltip twice
      Set<String> tooltips = null;

      for (RangeHighlighter highlighter : highlighters) {
        final Object tooltipObject = highlighter.getErrorStripeTooltip();
        if (tooltipObject == null) continue;

        final String text = tooltipObject instanceof HighlightInfo ? ((HighlightInfo)tooltipObject).getToolTip() : tooltipObject.toString();
        if (text == null) continue;

        if (tooltips == null) {
          tooltips = new THashSet<>();
        }
        if (tooltips.add(text)) {
          if (bigRenderer == null) {
            bigRenderer = new LineTooltipRenderer(text, new Object[]{highlighters});
          }
          else {
            bigRenderer.addBelow(text);
          }
        }
      }

      return bigRenderer;
    }

    @NotNull
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final String text) {
      return new LineTooltipRenderer(text, new Object[]{text});
    }

    @NotNull
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull final String text, final int width) {
      return new LineTooltipRenderer(text, width, new Object[]{text});
    }

    @NotNull
    @Override
    public TrafficTooltipRenderer createTrafficTooltipRenderer(@NotNull final Runnable onHide, @NotNull Editor editor) {
      return new TrafficTooltipRenderer() {
        @Override
        public void repaintTooltipWindow() {
        }

        @NotNull
        @Override
        public LightweightHint show(@NotNull Editor editor,
                                    @NotNull Point p,
                                    boolean alignToRight,
                                    @NotNull TooltipGroup group,
                                    @NotNull HintHint hintHint) {
          JLabel label = new JLabel("WTF");
          return new LightweightHint(label) {
            @Override
            public void hide() {
              super.hide();
              onHide.run();
            }
          };
        }
      };
    }
  }

  @NotNull
  private ProperTextRange offsetsToYPositions(int start, int end) {
    if (!dimensionsAreValid) {
      recalcEditorDimensions();
    }
    Document document = myEditor.getDocument();
    int startLineNumber = end == -1 ? 0 : offsetToLine(start, document);
    int editorStartY = myEditor.visualLineToY(startLineNumber);
    int startY;
    int editorTargetHeight = Math.max(0, myEditorTargetHeight);
    if (myEditorSourceHeight < editorTargetHeight) {
      startY = myEditorScrollbarTop + editorStartY;
    }
    else {
      startY = myEditorScrollbarTop + (int)((float)editorStartY / myEditorSourceHeight * editorTargetHeight);
    }

    int endY;
    int endLineNumber = offsetToLine(end, document);
    if (end == -1 || start == -1) {
      endY = Math.min(myEditorSourceHeight, editorTargetHeight);
    }
    else if (startLineNumber == endLineNumber) {
      endY = startY; // both offsets are on the same line, no need to recalc Y position
    }
    else if (myEditorSourceHeight < editorTargetHeight) {
      endY = myEditorScrollbarTop + myEditor.visualLineToY(endLineNumber);
    }
    else {
      int editorEndY = myEditor.visualLineToY(endLineNumber);
      endY = myEditorScrollbarTop + (int)((float)editorEndY / myEditorSourceHeight * editorTargetHeight);
    }
    if (endY < startY) endY = startY;
    return new ProperTextRange(startY, endY);
  }

  private int yPositionToOffset(int y, boolean beginLine) {
    if (!dimensionsAreValid) {
      recalcEditorDimensions();
    }
    final int safeY = Math.max(0, y - myEditorScrollbarTop);
    int editorY;
    if (myEditorSourceHeight < myEditorTargetHeight) {
      editorY = safeY;
    }
    else {
      float fraction = Math.max(0, Math.min(1, safeY / (float)myEditorTargetHeight));
      editorY = (int)(fraction * myEditorSourceHeight);
    }
    VisualPosition visual = myEditor.xyToVisualPosition(new Point(0, editorY));
    int line = myEditor.visualToLogicalPosition(visual).line;
    Document document = myEditor.getDocument();
    if (line < 0) return 0;
    if (line >= document.getLineCount()) return document.getTextLength();

    final FoldingModelEx foldingModel = myEditor.getFoldingModel();
    if (beginLine) {
      final int offset = document.getLineStartOffset(line);
      final FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.min(offset, startCollapsed.getStartOffset()) : offset;
    }
    else {
      final int offset = document.getLineEndOffset(line);
      final FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.max(offset, startCollapsed.getEndOffset()) : offset;
    }
  }
  private class EditorFragmentRenderer implements TooltipRenderer {
    private int myVisualLine;
    private boolean myShowInstantly;
    private final List<RangeHighlighterEx> myHighlighters = new ArrayList<>();
    @Nullable private BufferedImage myCacheLevel1;
    @Nullable private BufferedImage myCacheLevel2;
    private int myCacheStartLine;
    private int myCacheEndLine;
    private int myStartVisualLine;
    private int myEndVisualLine;
    private int myRelativeY;
    private boolean myDelayed;
    private boolean isDirty;
    private final AtomicReference<Point> myPointHolder = new AtomicReference<>();
    private final AtomicReference<HintHint> myHintHolder = new AtomicReference<>();

    private EditorFragmentRenderer() {
      update(-1, Collections.emptyList(), false);
    }

    void update(int visualLine, @NotNull Collection<? extends RangeHighlighterEx> rangeHighlighters, boolean showInstantly) {
      myVisualLine = visualLine;
      myShowInstantly = showInstantly;
      myHighlighters.clear();
      if (myVisualLine ==-1) return;
      int oldStartLine = myStartVisualLine;
      int oldEndLine = myEndVisualLine;
      myStartVisualLine = fitLineToEditor(myVisualLine - myPreviewLines);
      myEndVisualLine = fitLineToEditor(myVisualLine + myPreviewLines);
      isDirty |= oldStartLine != myStartVisualLine || oldEndLine != myEndVisualLine;
      myHighlighters.addAll(rangeHighlighters);
      myHighlighters.sort((ex1, ex2) -> {
        LogicalPosition startPos1 = myEditor.offsetToLogicalPosition(ex1.getAffectedAreaStartOffset());
        LogicalPosition startPos2 = myEditor.offsetToLogicalPosition(ex2.getAffectedAreaStartOffset());
        if (startPos1.line != startPos2.line) return 0;
        return startPos1.column - startPos2.column;
      });
    }

    @Nullable
    @Override
    public LightweightHint show(@NotNull final Editor editor,
                                @NotNull Point p,
                                boolean alignToRight,
                                @NotNull TooltipGroup group,
                                @NotNull final HintHint hintInfo) {
      int contentInsets = JBUIScale.scale(2); // BalloonPopupBuilderImpl.myContentInsets
      final HintManagerImpl hintManager = HintManagerImpl.getInstanceImpl();
      boolean needDelay = false;
      if (myEditorPreviewHint == null) {
        needDelay = true;
        final JPanel editorFragmentPreviewPanel = new JPanel() {
          private static final int R = 6;

          @DirtyUI
          @NotNull
          @Override
          public Dimension getPreferredSize() {
            int width = myEditor.getGutterComponentEx().getWidth() + myEditor.getScrollingModel().getVisibleArea().width
                        - myEditor.getVerticalScrollBar().getWidth();
            width -= JBUIScale.scale(EDITOR_FRAGMENT_POPUP_BORDER) * 2 + contentInsets;
            return new Dimension(width - BalloonImpl.POINTER_LENGTH.get(),
                                 Math.min(2 * myPreviewLines * myEditor.getLineHeight(),
                                          myEditor.visualLineToY(myEndVisualLine) - myEditor.visualLineToY(myStartVisualLine)));
          }

          @DirtyUI
          @Override
          protected void paintComponent(@NotNull Graphics g) {
            if (myVisualLine ==-1 || myEditor.isDisposed()) return;
            Dimension size = getPreferredSize();
            if (size.width <= 0 || size.height <= 0) return;

            EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
            EditorComponentImpl content = myEditor.getContentComponent();

            int gutterWidth = gutter.getWidth();
            if (myCacheLevel2 == null || myCacheStartLine > myStartVisualLine || myCacheEndLine < myEndVisualLine) {
              myCacheStartLine = fitLineToEditor(myVisualLine - myCachePreviewLines);
              myCacheEndLine = fitLineToEditor(myCacheStartLine + 2 * myCachePreviewLines + 1);
              int cacheStartY = myEditor.visualLineToY(myCacheStartLine);
              if (myCacheLevel2 == null) {
                myCacheLevel2 = ImageUtil
                  .createImage(g, size.width, myEditor.visualLineToY(myCacheEndLine) - cacheStartY + myEditor.getLineHeight(),
                               BufferedImage.TYPE_INT_RGB);
              }
              Graphics2D cg = myCacheLevel2.createGraphics();
              final AffineTransform t = cg.getTransform();
              EditorUIUtil.setupAntialiasing(cg);
              int lineShift = - cacheStartY;

              int shift = JBUIScale.scale(EDITOR_FRAGMENT_POPUP_BORDER) + contentInsets;
              AffineTransform gutterAT = AffineTransform.getTranslateInstance(-shift, lineShift);
              AffineTransform contentAT = AffineTransform.getTranslateInstance(gutterWidth - shift, lineShift);
              gutterAT.preConcatenate(t);
              contentAT.preConcatenate(t);

              EditorTextField.SUPPLEMENTARY_KEY.set(myEditor, Boolean.TRUE);
              try {
                cg.setTransform(gutterAT);
                cg.setClip(0, -lineShift, gutterWidth, myCacheLevel2.getHeight());
                gutter.paint(cg);

                cg.setTransform(contentAT);
                cg.setClip(0, -lineShift, content.getWidth(), myCacheLevel2.getHeight());
                content.paint(cg);
              }
              finally {
                EditorTextField.SUPPLEMENTARY_KEY.set(myEditor, null);
              }

            }
            if (myCacheLevel1 == null) {
              myCacheLevel1 =
                ImageUtil.createImage(g, size.width, myEditor.getLineHeight() * (2 * myPreviewLines + 1), BufferedImage.TYPE_INT_RGB);
              isDirty = true;
            }
            if (isDirty) {
              myRelativeY = SwingUtilities.convertPoint(this, 0, 0, myEditor.getScrollPane()).y;
              Graphics2D g2d = myCacheLevel1.createGraphics();
              final AffineTransform transform = g2d.getTransform();
              EditorUIUtil.setupAntialiasing(g2d);
              GraphicsUtil.setupAAPainting(g2d);
              g2d.setColor(myEditor.getBackgroundColor());
              g2d.fillRect(0, 0, getWidth(), getHeight());
              int topDisplayedY = Math.max(myEditor.visualLineToY(myStartVisualLine),
                                           myEditor.visualLineToY(myVisualLine) - myPreviewLines * myEditor.getLineHeight());
              int cacheStartY = myEditor.visualLineToY(myCacheStartLine);
              AffineTransform translateInstance = AffineTransform.getTranslateInstance(gutterWidth, cacheStartY - topDisplayedY);
              translateInstance.preConcatenate(transform);
              g2d.setTransform(translateInstance);
              UIUtil.drawImage(g2d, myCacheLevel2, -gutterWidth, 0, null);
              TIntIntHashMap rightEdges = new TIntIntHashMap();
              int h = myEditor.getLineHeight() - 2;

              EditorColorsScheme colorsScheme = myEditor.getColorsScheme();
              Font font = UIUtil.getFontWithFallback(colorsScheme.getEditorFontName(), Font.PLAIN, colorsScheme.getEditorFontSize());
              g2d.setFont(font.deriveFont(font.getSize() *.8F));

              for (RangeHighlighterEx ex : myHighlighters) {
                if (!ex.isValid()) continue;
                int hEndOffset = ex.getAffectedAreaEndOffset();
                Object tooltip = ex.getErrorStripeTooltip();
                if (tooltip == null) continue;
                String s = tooltip instanceof HighlightInfo ? ((HighlightInfo)tooltip).getDescription() : String.valueOf(tooltip);
                if (StringUtil.isEmpty(s)) continue;
                s = s.replaceAll("&nbsp;", " ").replaceAll("\\s+", " ");
                s = StringUtil.unescapeXmlEntities(s);

                LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(hEndOffset);
                int endOfLineOffset = myEditor.getDocument().getLineEndOffset(logicalPosition.line);
                logicalPosition = myEditor.offsetToLogicalPosition(endOfLineOffset);
                Point placeToShow = myEditor.logicalPositionToXY(logicalPosition);
                logicalPosition = myEditor.xyToLogicalPosition(placeToShow);//wraps&foldings workaround
                placeToShow.x += R * 3 / 2;
                placeToShow.y -= cacheStartY - 1;

                int w = g2d.getFontMetrics().stringWidth(s);

                int rightEdge = rightEdges.get(logicalPosition.line);
                placeToShow.x = Math.max(placeToShow.x, rightEdge);
                rightEdge  = Math.max(rightEdge, placeToShow.x + w + 3 * R);
                rightEdges.put(logicalPosition.line, rightEdge);

                g2d.setColor(MessageType.WARNING.getPopupBackground());
                g2d.fillRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R);
                g2d.setColor(new JBColor(JBColor.GRAY, Gray._200));
                g2d.drawRoundRect(placeToShow.x, placeToShow.y, w + 2 * R, h, R, R);
                g2d.setColor(JBColor.foreground());
                g2d.drawString(s, placeToShow.x + R, placeToShow.y + h - g2d.getFontMetrics(g2d.getFont()).getDescent()/2 - 2);
              }
              isDirty = false;
            }
            Graphics2D g2 = (Graphics2D)g.create();
            try {
              GraphicsUtil.setupAAPainting(g2);
              g2.setClip(new RoundRectangle2D.Double(0, 0, size.width-.5, size.height-.5, 2, 2));
              UIUtil.drawImage(g2, myCacheLevel1, 0, 0, this);
              if (StartupUiUtil.isUnderDarcula()) {
                //Add glass effect
                Shape s = new Rectangle(0, 0, size.width, size.height);
                double cx = size.width / 2.0;
                double rx = size.width / 10.0;
                int ry = myEditor.getLineHeight() * 3 / 2;
                g2.setPaint(new GradientPaint(0, 0, Gray._255.withAlpha(75), 0, ry, Gray._255.withAlpha(10)));
                double pseudoMajorAxis = size.width - rx * 9 / 5;
                double cy = 0;
                Shape topShape1 = new Ellipse2D.Double(cx - rx - pseudoMajorAxis / 2, cy - ry, 2 * rx, 2 * ry);
                Shape topShape2 = new Ellipse2D.Double(cx - rx + pseudoMajorAxis / 2, cy - ry, 2 * rx, 2 * ry);
                Area topArea = new Area(topShape1);
                topArea.add(new Area(topShape2));
                topArea.add(new Area(new Rectangle.Double(cx - pseudoMajorAxis / 2, cy, pseudoMajorAxis, ry)));
                g2.fill(topArea);
                Area bottomArea = new Area(s);
                bottomArea.subtract(topArea);
                g2.setPaint(new GradientPaint(0, size.height - ry, Gray._0.withAlpha(10), 0, size.height, Gray._255.withAlpha(30)));
                g2.fill(bottomArea);
              }
            }
            finally {
              g2.dispose();
            }
          }
        };
        editorFragmentPreviewPanel.putClientProperty(BalloonImpl.FORCED_NO_SHADOW, Boolean.TRUE);
        myEditorPreviewHint = new LightweightHint(editorFragmentPreviewPanel) {

          @Override
          public void hide(boolean ok) {
            super.hide(ok);
            myCacheLevel1 = null;
            if (myCacheLevel2 != null) {
              myCacheLevel2 = null;
              myCacheStartLine = -1;
              myCacheEndLine = -1;
            }

            myDelayed = false;
          }
        };
        myEditorPreviewHint.setForceLightweightPopup(true);
      }
      Point point = new Point(hintInfo.getOriginalPoint());
      hintInfo.setTextBg(myEditor.getBackgroundColor());

      Color borderColor = myEditor.getColorsScheme().getAttributes(EditorColors.CODE_LENS_BORDER_COLOR).getEffectColor();
      hintInfo.setBorderColor(borderColor != null ? borderColor : myEditor.getColorsScheme().getDefaultForeground());
      point = SwingUtilities.convertPoint(((EditorImpl)editor).getVerticalScrollBar(), point, myEditor.getComponent().getRootPane());
      myPointHolder.set(point);
      myHintHolder.set(hintInfo);
      if (needDelay && !myShowInstantly) {
        myDelayed = true;
        Alarm alarm = new Alarm();
        alarm.addRequest(() -> {
          if (myEditorPreviewHint == null || !myDelayed) return;
          showEditorHint(hintManager, myPointHolder.get(), myHintHolder.get());
          myDelayed = false;
        }, /*Registry.intValue("ide.tooltip.initialDelay")*/300);
      }
      else if (!myDelayed) {
        showEditorHint(hintManager, point, hintInfo);
      }
      return myEditorPreviewHint;
    }

    private void showEditorHint(@NotNull HintManagerImpl hintManager, @NotNull Point point, HintHint hintInfo) {
      int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_MOUSEOVER |
                  HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_SCROLLING;
      hintManager.showEditorHint(myEditorPreviewHint, myEditor, point, flags, 0, false, hintInfo);
    }
  }

  private static final Key<List<StatusItem>> EXPANDED_STATUS = new Key<>("EXPANDED_STATUS");
  private static final Key<Boolean> TRANSLUCENT_STATE = new Key<>("TRANSLUCENT_STATE");
  private static final int DELTA_X = 6;
  private static final int DELTA_Y = 6;

  private class StatusAction extends DumbAwareAction implements CustomComponentAction {
    private boolean hasAnalyzed;
    private boolean isAnalyzing;

    @Override
    @NotNull
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return new StatusButton(this, presentation, new EditorToolbarButtonLook(), place, myEditor.getColorsScheme());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myPopupManager.showPopup(e.getInputEvent());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      if (analyzerStatus != null) {
        List<StatusItem> newStatus = analyzerStatus.getExpandedStatus();
        Icon newIcon = analyzerStatus.getIcon();

        boolean analyzing = analyzerStatus.getStandardStatus() == StandardStatus.ANALYZING;
        hasAnalyzed = hasAnalyzed || (isAnalyzing && !analyzing);
        isAnalyzing = analyzing;

        if (!(hasAnalyzed && isAnalyzing)) {
          if (newStatus.isEmpty()) {
            newStatus = Collections.singletonList(new StatusItem("", newIcon));
            presentation.putClientProperty(EXPANDED_STATUS, newStatus);
          }

          if (!Objects.equals(presentation.getClientProperty(EXPANDED_STATUS), newStatus)) {
            presentation.putClientProperty(EXPANDED_STATUS, newStatus);
          }
          else {
            presentation.putClientProperty(TRANSLUCENT_STATE, false);
          }
        }
        else {
          presentation.putClientProperty(TRANSLUCENT_STATE, true);
        }
      }
      else {
        presentation.putClientProperty(EXPANDED_STATUS, Collections.emptyList());
      }
    }
  }

  private static class StatusButton extends JPanel {
    private static final int LEFT_RIGHT_INDENT = 7;
    private static final int INTER_GROUP_OFFSET = 6;

    private boolean mousePressed;
    private boolean mouseHover;
    private final ActionButtonLook buttonLook;
    private final MouseListener mouseListener;
    private final PropertyChangeListener presentationPropertyListener;
    private final Presentation presentation;
    private final EditorColorsScheme colorsScheme;
    private boolean translucent;

    private StatusButton(@NotNull AnAction action, @NotNull Presentation presentation,
                         @NotNull ActionButtonLook buttonLook, @NotNull String place,
                         @NotNull EditorColorsScheme colorsScheme) {
      setLayout(new GridBagLayout());
      setOpaque(false);

      this.buttonLook = buttonLook;
      this.presentation = presentation;
      this.colorsScheme = colorsScheme;

      presentationPropertyListener = l -> {
        String propName = l.getPropertyName();
        if (propName.equals(EXPANDED_STATUS.toString()) && l.getNewValue() != null) {
          //noinspection unchecked
          List<StatusItem> newStatus = (List<StatusItem>)l.getNewValue();
          updateContents(newStatus);
          translucent = false;
          revalidate();
          repaint();
        }
        else if (propName.equals(TRANSLUCENT_STATE.toString())) {
          translucent = l.getNewValue() == Boolean.TRUE;
          repaint();
        }
      };

      mouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent me) {
          DataContext context = getDataContext();
          AnActionEvent event = AnActionEvent.createFromInputEvent(me, place, presentation, context, false, true);
          if (!ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            return;
          }

          if (presentation.isEnabled()) {
            ActionManagerEx manager = ActionManagerEx.getInstanceEx();
            manager.fireBeforeActionPerformed(action, context, event);

            action.actionPerformed(event);

            manager.queueActionPerformedEvent(action, context, event);
            ActionsCollector.getInstance().record(event.getProject(), action, event, null);

            ActionToolbar toolbar = getActionToolbar();
            if (toolbar != null) {
              toolbar.updateActionsImmediately();
            }
          }
        }

        @Override
        public void mousePressed(MouseEvent me) {
          mousePressed = true;
          repaint();
        }

        @Override
        public void mouseReleased(MouseEvent me) {
          mousePressed = false;
          repaint();
        }

        @Override
        public void mouseEntered(MouseEvent me) {
          mouseHover = true;
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent me) {
          mouseHover = false;
          repaint();
        }
      };

      List<StatusItem> newStatus = presentation.getClientProperty(EXPANDED_STATUS);
      if (newStatus != null) {
        updateContents(newStatus);
      }

      setBorder(JBUI.Borders.empty(1, 2));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      presentation.addPropertyChangeListener(presentationPropertyListener);
      addMouseListener(mouseListener);
    }

    @Override
    public void removeNotify() {
      presentation.removePropertyChangeListener(presentationPropertyListener);
      removeMouseListener(mouseListener);
    }

    private DataContext getDataContext() {
      ActionToolbar actionToolbar = getActionToolbar();
      return actionToolbar != null ? actionToolbar.getToolbarDataContext() : DataManager.getInstance().getDataContext(this);
    }

    private ActionToolbar getActionToolbar() {
      return ComponentUtil.getParentOfType((Class<? extends ActionToolbar>)ActionToolbar.class, this);
    }

    private void updateContents(@NotNull List<StatusItem> status) {
      removeAll();

      setEnabled(!status.isEmpty());
      setVisible(!status.isEmpty());

      GridBag gc = new GridBag().nextLine();
      if (status.size() == 1 && StringUtil.isEmpty(status.get(0).getText())) {
        add(createStyledLabel(null, status.get(0).getIcon(), SwingConstants.CENTER),
            gc.next().weightx(1).fillCellHorizontally());
      }
      else if (status.size() > 0) {
        int leftRightOffset = JBUIScale.scale(LEFT_RIGHT_INDENT);
        add(Box.createHorizontalStrut(leftRightOffset), gc.next());

        int counter = 0;
        for (StatusItem item : status) {
          add(createStyledLabel(item.getText(), item.getIcon(), SwingConstants.LEFT),
              gc.next().insetLeft(counter++ > 0 ? INTER_GROUP_OFFSET : 0));
        }

        add(Box.createHorizontalStrut(leftRightOffset), gc.next());
      }
    }

    private JLabel createStyledLabel(@Nullable String text, @Nullable Icon icon, int alignment) {
      JLabel label = new JLabel(text, icon, alignment) {
        @Override
        protected void paintComponent(Graphics graphics) {
          Graphics2D g2 = (Graphics2D)graphics.create();
          try {
            float alpha = translucent ? 0.5f : 1.0f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paintComponent(g2);
          }
          finally {
            g2.dispose();
          }
        }
      };

      label.setForeground(colorsScheme.getColor(ICON_TEXT_COLOR));

      Font font = label.getFont();
      font = font.deriveFont(font.getStyle(), font.getSize() - JBUIScale.scale(2));
      label.setFont(font);
      return label;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      int state = mousePressed ? ActionButtonComponent.PUSHED :
                  mouseHover ? ActionButtonComponent.POPPED :
                  ActionButtonComponent.NORMAL;

      buttonLook.paintBackground(graphics, this, state);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      size.height = Math.max(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height, size.height);
      size.width = Math.max(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width, size.width);
      JBInsets.addTo(size, getInsets());
      return size;
    }
  }

  private class EditorToolbarButtonLook extends ActionButtonLook {
    @Override
    public void paintBorder(Graphics g, JComponent component, int state) {}

    @Override
    public void paintLookBorder(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {}

    @Override
    public void paintBorder(Graphics g, JComponent component, Color color) {}

    @Override
    public void paintBackground(Graphics g, JComponent component, @ActionButtonComponent.ButtonState int state) {
      if (state == ActionButtonComponent.NORMAL) return;
      Rectangle rect = new Rectangle(component.getSize());
      JBInsets.removeFrom(rect, component.getInsets());

      EditorColorsScheme scheme = myEditor.getColorsScheme();
      Color color = state == ActionButtonComponent.PUSHED ? scheme.getColor(PRESSED_BACKGROUND) : scheme.getColor(HOVER_BACKGROUND);

      if (color != null) {
        ActionButtonLook.SYSTEM_LOOK.paintLookBackground(g, rect, color);
      }
    }

    @Override
    public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon, int x, int y) {
      if (icon != null) {
        boolean isDark = ColorUtil.isDark(myEditor.getColorsScheme().getDefaultBackground());
        super.paintIcon(g, actionButton, IconLoader.getDarkIcon(icon, isDark), x, y);
      }
    }
  }

  private class InspectionPopupManager {
    private final JPanel myContent = new JPanel(new GridBagLayout());
    private final ComponentPopupBuilder myPopupBuilder;
    private final Map<String, JProgressBar> myProgressBarMap = new HashMap<>();
    private final AncestorListener myAncestorListener;
    private final JBPopupListener myPopupListener;
    private final PopupState myPopupState = new PopupState();

    private JBPopup myPopup;

    private InspectionPopupManager() {
      myContent.setOpaque(true);
      myContent.setBackground(UIUtil.getToolTipBackground());

      myPopupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null).
        setCancelOnClickOutside(true).
        setCancelCallback(() -> analyzerStatus == null || analyzerStatus.getController().canClosePopup());

      myAncestorListener = new AncestorListenerAdapter() {
        @Override
        public void ancestorMoved(AncestorEvent event) {
          hidePopup();
        }
      };

      myPopupListener = new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          if (analyzerStatus != null) {
            analyzerStatus.getController().onClosePopup();
          }
          myEditor.getComponent().removeAncestorListener(myAncestorListener);
          myPopup.removeListener(myPopupListener);
        }
      };
    }

    private void updateUI() {
      IJSwingUtilities.updateComponentTreeUI(myContent);
    }

    private void showPopup(@NotNull InputEvent event) {
      hidePopup();
      if (myPopupState.isRecentlyHidden()) return; // do not show new popup

      updateContentPanel(analyzerStatus.getController());

      myPopup = myPopupBuilder.createPopup();
      myPopup.addListener(myPopupListener);
      myPopup.addListener(myPopupState);
      myEditor.getComponent().addAncestorListener(myAncestorListener);

      JComponent owner = (JComponent)event.getComponent();
      Dimension size = myContent.getPreferredSize();
      size.width = Math.max(size.width, JBUIScale.scale(296));

      RelativePoint point = new RelativePoint(owner,
                  new Point(owner.getWidth() - owner.getInsets().right + JBUIScale.scale(DELTA_X) - size.width,
                            owner.getHeight() + JBUIScale.scale(DELTA_Y)));

      myPopup.setSize(size);
      myPopup.show(point);
    }

    private void hidePopup() {
      if (myPopup != null && !myPopup.isDisposed()) {
        myPopup.cancel();
      }
      myPopup = null;
    }

    private void updateContentPanel(@NotNull UIController controller) {
      List<PassWrapper> passes = analyzerStatus.getPasses();
      Set<String> presentableNames = ContainerUtil.map2Set(passes, p -> p.getPresentableName());

      if (!presentableNames.isEmpty() && myProgressBarMap.keySet().equals(presentableNames)) {
        for (PassWrapper pass : passes) {
          myProgressBarMap.get(pass.getPresentableName()).setValue(pass.toPercent());
        }
        return;
      }
      myContent.removeAll();

      GridBag gc = new GridBag();
      myContent.add(new JLabel(XmlStringUtil.wrapInHtml(analyzerStatus.getTitle())),
                       gc.nextLine().next().
                         anchor(GridBagConstraints.LINE_START).
                         weightx(1).
                         fillCellHorizontally().
                         insets(10, 10, 10, 0));

      Presentation presentation = new Presentation();
      presentation.setIcon(AllIcons.Actions.More);
      presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);

      List<AnAction> actions = controller.getActions();
      if (!actions.isEmpty()) {
        ActionButton menuButton = new ActionButton(new MenuAction(actions),
                                                   presentation,
                                                   ActionPlaces.EDITOR_POPUP,
                                                   ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);

        myContent.add(menuButton, gc.next().anchor(GridBagConstraints.LINE_END).weightx(0).insets(10, 6, 10, 6));
      }

      myProgressBarMap.clear();
      JPanel myProgressPanel = new NonOpaquePanel(new GridBagLayout());
      GridBag progressGC = new GridBag();
      for (PassWrapper pass : passes) {
        myProgressPanel.add(new JLabel(pass.getPresentableName() + ": "),
                            progressGC.nextLine().next().anchor(GridBagConstraints.LINE_START).weightx(0).insets(0, 10, 0, 6));

        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(pass.toPercent());
        myProgressPanel.add(pb, progressGC.next().anchor(GridBagConstraints.LINE_START).weightx(1).fillCellHorizontally().insets(0, 0, 0, 6));
        myProgressBarMap.put(pass.getPresentableName(), pb);
      }

      myContent.add(myProgressPanel, gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1));

      if (!analyzerStatus.getDetails().isEmpty()) {
        int topIndent = !myProgressBarMap.isEmpty() ? 10 : 0;
        myContent.add(new JLabel(XmlStringUtil.wrapInHtml(analyzerStatus.getDetails())),
                      gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().
                        coverLine().weightx(1).insets(topIndent, 10, 10, 6));
      }

      myContent.add(new TrackableLinkLabel(EditorBundle.message("iw.open.problems.view"), controller::openProblemsView),
                    gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1).insets(10, 10, 10, 0));
      
      myContent.add(createLowerPanel(controller),
                    gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1));
    }

    private void updateVisiblePopup() {
      if (myPopup != null && myPopup.isVisible()) {
        updateContentPanel(analyzerStatus.getController());

        Dimension size = myContent.getPreferredSize();
        size.width = Math.max(size.width, JBUIScale.scale(296));
        myPopup.setSize(size);
      }
    }

    private @NotNull JPanel createLowerPanel(@NotNull UIController controller) {
      JPanel panel = new JPanel(new GridBagLayout());
      GridBag gc = new GridBag().nextLine();

      if (PowerSaveMode.isEnabled()) {
        panel.add(new TrackableLinkLabel(EditorBundle.message("iw.disable.powersave"), () ->{
                    PowerSaveMode.setEnabled(false);
                    hidePopup();
                  }),
                  gc.next().anchor(GridBagConstraints.LINE_START));
      }
      else {
        List<LanguageHighlightLevel> levels = controller.getHighlightLevels();

        if (levels.size() == 1) {
          JLabel highlightLabel = new JLabel(EditorBundle.message("iw.highlight.label") + " ");
          highlightLabel.setForeground(JBUI.CurrentTheme.Link.linkColor());

          panel.add(highlightLabel, gc.next().anchor(GridBagConstraints.LINE_START));
          panel.add(createDropDownLink(levels.get(0), controller), gc.next());
        }
        else if (levels.size() > 1) {
          for(LanguageHighlightLevel level: levels) {
            JLabel highlightLabel = new JLabel(level.getLanguage().getDisplayName() + ": ");
            highlightLabel.setForeground(JBUI.CurrentTheme.Link.linkColor());

            panel.add(highlightLabel, gc.next().anchor(GridBagConstraints.LINE_START).gridx > 0 ? gc.insetLeft(8) : gc);
            panel.add(createDropDownLink(level, controller), gc.next());
          }
        }
      }
      panel.add(Box.createHorizontalGlue(), gc.next().fillCellHorizontally().weightx(1.0));

      controller.fillHectorPanels(panel, gc);

      panel.setOpaque(true);
      panel.setBackground(UIUtil.getToolTipActionBackground());
      panel.setBorder(JBUI.Borders.empty(4, 10));
      return panel;
    }

    private @NotNull DropDownLink<InspectionsLevel> createDropDownLink(@NotNull LanguageHighlightLevel level, @NotNull UIController controller) {
      return new DropDownLink<>(level.getLevel(),
                                controller.getAvailableLevels(),
                                inspectionsLevel -> {
                                  controller.setHighLightLevel(level.copy(level.getLanguage(), inspectionsLevel));
                                  myContent.revalidate();

                                  Dimension size = myContent.getPreferredSize();
                                  size.width = Math.max(size.width, JBUIScale.scale(296));
                                  myPopup.setSize(size);
                                }, true);
    }
  }

  private class MenuAction extends DefaultActionGroup implements HintManagerImpl.ActionToIgnore {
    private MenuAction(@NotNull List<? extends AnAction> actions) {
      setPopup(true);
      addAll(actions);
      add(new ToggleAction(EditorBundle.message("iw.show.toolbar")) {
        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
          return showToolbar;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
          showToolbar = state;
          EditorSettingsExternalizable.getInstance().setShowInspectionWidget(state);
          updateTrafficLightVisibility();
          ActionsCollector.getInstance().record(e.getProject(), this, e, null);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          super.update(e);
          e.getPresentation().setEnabled(analyzerStatus == null || analyzerStatus.getController().enableToolbar());
        }

        @Override
        public boolean isDumbAware() {
          return true;
        }
      });
    }
  }

  private static class TrackableLinkLabel extends LinkLabel<Object> {
    private InputEvent myEvent;

    private TrackableLinkLabel(@NotNull String text, @NotNull Runnable action) {
      super(text, null);
      setListener((__, ___) -> {
        action.run();
        ActionsCollector.getInstance().record(null, myEvent, getClass());
      }, null);
    }

    @Override
    public void doClick(InputEvent e) {
      myEvent = e;
      super.doClick(e);
    }
  }
}
