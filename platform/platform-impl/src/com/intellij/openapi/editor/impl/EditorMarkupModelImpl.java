// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.hint.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.actionSystem.remoting.ActionWithMergeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.impl.inspector.InspectionsGroup;
import com.intellij.openapi.editor.impl.inspector.RedesignedInspectionsManager;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.LabelUI;
import javax.swing.plaf.ScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class EditorMarkupModelImpl extends MarkupModelImpl
  implements EditorMarkupModel, CaretListener, BulkAwareDocumentListener.Simple, VisibleAreaListener {
  private static final TooltipGroup ERROR_STRIPE_TOOLTIP_GROUP = new TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0);

  private static final JBValue SCROLLBAR_WIDTH = new JBValue.UIInteger("Editor.scrollBarWidth", 14);

  private static final ColorKey HOVER_BACKGROUND = ColorKey.createColorKey("ActionButton.hoverBackground",
                                                                           JBUI.CurrentTheme.ActionButton.hoverBackground());

  private static final ColorKey PRESSED_BACKGROUND = ColorKey.createColorKey("ActionButton.pressedBackground",
                                                                             JBUI.CurrentTheme.ActionButton.pressedBackground());

  private static final ColorKey ICON_TEXT_COLOR = ColorKey.createColorKey("ActionButton.iconTextForeground",
                                                                          UIUtil.getContextHelpForeground());

  private static final int QUICK_ANALYSIS_TIMEOUT_MS = 3000;

  private static final Logger LOG = Logger.getInstance(EditorMarkupModelImpl.class);

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

  private static int getStatusIconSize() {
    return JBUIScale.scale(18);
  }

  private final @NotNull EditorImpl myEditor;
  // null renderer means we should not show a traffic light icon
  private @Nullable ErrorStripeRenderer myErrorStripeRenderer;
  private final CheckedDisposable resourcesDisposable = Disposer.newCheckedDisposable();
  private final MergingUpdateQueue myStatusUpdates =
    new MergingUpdateQueue(getClass().getName(), 50, true, MergingUpdateQueue.ANY_COMPONENT, resourcesDisposable);
  // query daemon status in BGT (because it's rather expensive and PSI-related) and then update the icon in EDT later
  private final MergingUpdateQueue myTrafficLightIconUpdates =
    new MergingUpdateQueue(getClass().getName(), 50, true, MergingUpdateQueue.ANY_COMPONENT, resourcesDisposable, null,
                           Alarm.ThreadToUse.POOLED_THREAD);
  private final ErrorStripeMarkersModel myErrorStripeMarkersModel;

  private boolean dimensionsAreValid;
  private int myEditorScrollbarTop = -1;
  private int myEditorTargetHeight = -1;
  private int myEditorSourceHeight = -1;
  private @Nullable ProperTextRange myDirtyYPositions;
  private static final ProperTextRange WHOLE_DOCUMENT = new ProperTextRange(0, 0);

  private @NotNull ErrorStripTooltipRendererProvider myTooltipRendererProvider = new BasicTooltipRendererProvider();

  private int myMinMarkHeight;// height for horizontal, width for vertical stripes
  private final @NotNull EditorFragmentRenderer myEditorFragmentRenderer;
  private final MouseMovementTracker myMouseMovementTracker = new MouseMovementTracker();
  private int myRowAdjuster;
  private int myWheelAccumulator;
  private int myLastVisualLine;
  private Reference<LightweightHint> myCurrentHint;
  private int myCurrentHintAnchorY;
  private boolean myKeepHint;

  private final ActionToolbarImpl statusToolbar;
  private boolean showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget();
  private boolean trafficLightVisible = true;
  private final ComponentListener toolbarComponentListener;
  private Rectangle cachedToolbarBounds = new Rectangle();
  private final JLabel smallIconLabel = new JLabel();
  private volatile @NotNull AnalyzerStatus analyzerStatus = AnalyzerStatus.getEMPTY();
  private boolean hasAnalyzed;
  private boolean isAnalyzing;
  private boolean showNavigation;
  private boolean reportErrorStripeInconsistency = true;
  private final @NotNull TrafficLightPopup myTrafficLightPopup;
  private final Alarm statusTimer = new Alarm(resourcesDisposable);
  private final DefaultActionGroup myExtraActions;
  private final Map<InspectionWidgetActionProvider, AnAction> extensionActions = new HashMap<>();

  EditorMarkupModelImpl(@NotNull EditorImpl editor) {
    super(editor.getDocument());
    myEditor = editor;
    myEditorFragmentRenderer = new EditorFragmentRenderer(editor);
    setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().getErrorStripeMarkMinHeight());

    myTrafficLightPopup = new TrafficLightPopup(editor, new CompactViewAction());

    AnAction nextErrorAction = createAction("GotoNextError", AllIcons.Actions.FindAndShowNextMatchesSmall);
    AnAction prevErrorAction = createAction("GotoPreviousError", AllIcons.Actions.FindAndShowPrevMatchesSmall);

    myExtraActions = new ExtraActionGroup();
    populateInspectionWidgetActionsFromExtensions();

    DefaultActionGroup actions = new StatusToolbarGroup(
      myExtraActions,
      new InspectionsGroup(() -> analyzerStatus, editor),
      new TrafficLightAction(),
      new NavigationGroup(prevErrorAction, nextErrorAction));

    ActionButtonLook editorButtonLook = new EditorToolbarButtonLook();
    statusToolbar = new ActionToolbarImpl(ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR, actions, true) {
      @Override
      public void addNotify() {
        setTargetComponent(editor.getContentComponent());
        super.addNotify();
      }

      @Override
      protected void paintComponent(Graphics g) {
        editorButtonLook.paintBackground(g, this, myEditor.getBackgroundColor());
      }

      @Override
      protected int getSeparatorHeight() {
        return getStatusIconSize();
      }

      @Override
      protected @NotNull ActionButtonWithText createTextButton(@NotNull AnAction action,
                                                               @NotNull String place,
                                                               @NotNull Presentation presentation,
                                                               Supplier<? extends @NotNull Dimension> minimumSize) {
        if (RedesignedInspectionsManager.isAvailable()) return super.createTextButton(action, place, presentation, minimumSize);

        ActionButtonWithText button = super.createTextButton(action, place, presentation, minimumSize);
        JBColor color = JBColor.lazy(() -> {
          return ObjectUtils.notNull(editor.getColorsScheme().getColor(ICON_TEXT_COLOR), ICON_TEXT_COLOR.getDefaultColor());
        });
        button.setForeground(color);
        return button;
      }

      @Override
      protected @NotNull ActionButton createIconButton(@NotNull AnAction action,
                                                       @NotNull String place,
                                                       @NotNull Presentation presentation,
                                                       Supplier<? extends @NotNull Dimension> minimumSize) {
        if (RedesignedInspectionsManager.isAvailable()) return super.createIconButton(action, place, presentation, minimumSize);

        return new ActionButton(action, presentation, place, minimumSize) {
          @Override
          public void updateIcon() {
            super.updateIcon();
            revalidate();
            repaint();
          }

          @Override
          public @NotNull Insets getInsets() {
            return myAction == nextErrorAction ? JBUI.insets(2, 1) :
                   myAction == prevErrorAction ? JBUI.insets(2, 1, 2, 2) :
                   JBUI.insets(2);
          }

          @Override
          public @NotNull Dimension getPreferredSize() {

            Icon icon = getIcon();
            Dimension size = new Dimension(icon.getIconWidth(), icon.getIconHeight());

            int minSize = getStatusIconSize();
            size.width = Math.max(size.width, minSize);
            size.height = Math.max(size.height, minSize);

            JBInsets.addTo(size, getInsets());
            return size;
          }
        };
      }

      @Override
      public void doLayout() {
        LayoutManager layoutManager = getLayout();
        if (layoutManager != null) {
          layoutManager.layoutContainer(this);
        }
        else {
          super.doLayout();
        }
      }

/*      @Override
      protected Dimension updatePreferredSize(Dimension preferredSize) {
        return preferredSize;
      }

      @Override
      protected Dimension updateMinimumSize(Dimension minimumSize) {
        return minimumSize;
      }*/
    };

    statusToolbar.setMiniMode(true);
    statusToolbar.setCustomButtonLook(editorButtonLook);
    toolbarComponentListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        Component toolbar = event.getComponent();
        if (toolbar.getWidth() > 0 && toolbar.getHeight() > 0) {
          updateTrafficLightVisibility();
        }
      }
    };

    JComponent toolbar = statusToolbar.getComponent();
    toolbar.setLayout(new StatusComponentLayout());
    toolbar.addComponentListener(toolbarComponentListener);
    toolbar.setBorder(JBUI.Borders.empty(2));

/*    if(RedesignedInspectionsManager.isAvailable()) {
      GotItTooltip tooltip = new GotItTooltip("redesigned.inspections.tooltip",
                                              "The perfect companion for on the go, training and sports education. Through an integrated straw, the bottle sends thirst quickly without beating. Thanks to the screw cap, the bottle is quickly filled and it stays in place", resourcesDisposable);
      tooltip.withShowCount(1);
      tooltip.withHeader("Paw Patrol");
      tooltip.withIcon(AllIcons.General.BalloonInformation);
      tooltip.show(toolbar, GotItTooltip.BOTTOM_MIDDLE);
    }*/

    smallIconLabel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        myTrafficLightPopup.hidePopup();
        analyzerStatus.getController().toggleProblemsView();
      }

      @Override
      public void mouseEntered(MouseEvent event) {
        myTrafficLightPopup.scheduleShow(event, analyzerStatus);
      }

      @Override
      public void mouseExited(MouseEvent event) {
        myTrafficLightPopup.scheduleHide();
      }
    });
    smallIconLabel.setOpaque(false);
    smallIconLabel.setBackground(JBColor.lazy(() -> myEditor.getColorsScheme().getDefaultBackground()));
    smallIconLabel.setVisible(false);

    JPanel statusPanel = new NonOpaquePanel();
    statusPanel.setVisible(!myEditor.isOneLineMode());
    statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
    statusPanel.add(toolbar);
    statusPanel.add(smallIconLabel);

    ((JBScrollPane)myEditor.getScrollPane()).setStatusComponent(statusPanel);

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(resourcesDisposable);
    connection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
      @Override
      public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        if (action instanceof HintManagerImpl.ActionToIgnore) {
          return;
        }
        myTrafficLightPopup.hidePopup();
      }
    });

    connection.subscribe(LafManagerListener.TOPIC, __ -> myTrafficLightPopup.updateUI());
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        showToolbar =
          EditorSettingsExternalizable.getInstance().isShowInspectionWidget() && analyzerStatus.getController().isToolbarEnabled();

        updateTrafficLightVisibility();
      }
    });

    myErrorStripeMarkersModel = new ErrorStripeMarkersModel(myEditor, resourcesDisposable);
  }

  @Override
  public String toString() {
    return "EditorMarkupModel for " + myEditor;
  }

  @Override
  public void caretPositionChanged(@NotNull CaretEvent event) {
    updateTrafficLightVisibility();
  }

  @Override
  public void afterDocumentChange(@NotNull Document document) {
    myTrafficLightPopup.hidePopup();
    updateTrafficLightVisibility();
  }

  @Override
  public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
    updateTrafficLightVisibility();
  }

  private void updateTrafficLightVisibility() {
    myStatusUpdates.queue(Update.create("visibility", () -> WriteIntentReadAction.run((Runnable)() -> doUpdateTrafficLightVisibility())));
  }

  private void doUpdateTrafficLightVisibility() {
    if (trafficLightVisible) {
      if (RedesignedInspectionsManager.isAvailable()) {
        statusToolbar.updateActionsAsync();
      }

      if (showToolbar && myEditor.myView != null) {
        statusToolbar.setTargetComponent(myEditor.getContentComponent());
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

  private void populateInspectionWidgetActionsFromExtensions() {
    InspectionWidgetActionProvider.EP_NAME.getExtensionList().
      forEach(extension -> {
        AnAction action = extension.createAction(myEditor);
        if (action != null) {
          extensionActions.put(extension, action);
          addInspectionWidgetAction(action, null);
        }
      });

    InspectionWidgetActionProvider.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull InspectionWidgetActionProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        ApplicationManager.getApplication().invokeLater(() -> {
          AnAction action = extension.createAction(myEditor);
          if (action != null) {
            extensionActions.put(extension, action);
            addInspectionWidgetAction(action, null);
          }
        });
      }

      @Override
      public void extensionRemoved(@NotNull InspectionWidgetActionProvider extension, @NotNull PluginDescriptor pluginDescriptor) {
        ApplicationManager.getApplication().invokeLater(() -> {
          AnAction action = extensionActions.remove(extension);
          if (action != null) {
            removeInspectionWidgetAction(action);
          }
        });
      }
    }, resourcesDisposable);
  }

  @Override
  public void addInspectionWidgetAction(@NotNull AnAction action, @Nullable Constraints constraints) {
    if (constraints != null) {
      myExtraActions.add(action, constraints);
    }
    else {
      myExtraActions.add(action);
    }
  }

  @Override
  public void removeInspectionWidgetAction(@NotNull AnAction action) {
    myExtraActions.remove(action);
  }

  private @NotNull AnAction createAction(@NotNull String id, @NotNull Icon icon) {
    AnAction delegate = ActionManager.getInstance().getAction(id);
    AnAction result = new MarkupModelDelegateAction(delegate) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        if (RedesignedInspectionsManager.isAvailable()) {
          e.getPresentation().setEnabledAndVisible(false);
          return;
        }
        e.getPresentation().setEnabledAndVisible(true);
        super.update(e);
      }
    };
    result.getTemplatePresentation().setIcon(icon);
    return result;
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
    assert myEditorScrollbarTop >= 0;
    int editorScrollbarBottom = scrollBar.getIncScrollButtonHeight();
    myEditorTargetHeight = scrollBarHeight - myEditorScrollbarTop - editorScrollbarBottom;
    myEditorSourceHeight = myEditor.getPreferredHeight();

    dimensionsAreValid = scrollBarHeight != 0;
  }

  @Override
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

    myTrafficLightIconUpdates.queue(Update.create("traffic light icon", () -> {
      ErrorStripeRenderer errorStripeRenderer = myErrorStripeRenderer;
      if (errorStripeRenderer != null) {
        AnalyzerStatus newStatus = ReadAction.compute(() -> errorStripeRenderer.getStatus());
        if (!newStatus.equalsTo(analyzerStatus)) {
          ApplicationManager.getApplication().invokeLater(() -> changeStatus(newStatus));
        }
      }
    }));
  }

  private void changeStatus(@NotNull AnalyzerStatus newStatus) {
    ThreadingAssertions.assertEventDispatchThread();
    if (!isErrorStripeVisible() || resourcesDisposable.isDisposed()) {
      return;
    }
    statusTimer.cancelAllRequests();

    AnalyzingType analyzingType = newStatus.getAnalyzingType();
    boolean resetAnalyzingStatus = analyzerStatus.isTextStatus() && analyzerStatus.getAnalyzingType() == AnalyzingType.COMPLETE;
    analyzerStatus = newStatus;
    smallIconLabel.setIcon(analyzerStatus.getIcon());

    if (showToolbar != analyzerStatus.getController().isToolbarEnabled()) {
      showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget() &&
                    analyzerStatus.getController().isToolbarEnabled();
      updateTrafficLightVisibility();
    }

    boolean analyzing = analyzingType != AnalyzingType.COMPLETE;
    hasAnalyzed = !resetAnalyzingStatus && (hasAnalyzed || (isAnalyzing && !analyzing));
    isAnalyzing = analyzing;

    if (analyzingType != AnalyzingType.EMPTY) {
      showNavigation = analyzerStatus.getShowNavigation();
    }
    else {
      statusTimer.addRequest(() -> {
        hasAnalyzed = false;
        ActivityTracker.getInstance().inc();
      }, QUICK_ANALYSIS_TIMEOUT_MS);
    }

    myTrafficLightPopup.updateVisiblePopup(analyzerStatus);
    ActivityTracker.getInstance().inc();
  }

  // Used in Rider please do not drop it
  public void forcingUpdateStatusToolbar() {
    myStatusUpdates.queue(Update.create("forcingUpdate", () -> {
      statusToolbar.updateActionsImmediately();
    }));
  }

  private static final class PositionedStripe {
    private @NotNull Color color;
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

  private static @NotNull Rectangle getBoundsOnScreen(@NotNull LightweightHint hint) {
    JComponent component = hint.getComponent();
    Point location = hint.getLocationOn(component);
    SwingUtilities.convertPointToScreen(location, component);
    return new Rectangle(location, hint.getSize());
  }

  // true if tooltip shown
  private boolean showToolTipByMouseMove(@NotNull MouseEvent e) {
    ThreadingAssertions.assertEventDispatchThread();
    LightweightHint currentHint = getCurrentHint();
    if (currentHint != null && (myKeepHint || myMouseMovementTracker.isMovingTowards(e, getBoundsOnScreen(currentHint)))) {
      return true;
    }
    int visualLine = getVisualLineByEvent(e);
    myLastVisualLine = visualLine;
    Rectangle area = myEditor.getScrollingModel().getVisibleArea();
    int visualY = myEditor.visualLineToY(visualLine);
    boolean isVisible = myWheelAccumulator == 0 && area.contains(area.x, visualY);

    if (!isVisible &&
        UISettings.getInstance().getShowEditorToolTip() &&
        !Boolean.TRUE.equals(myEditor.getUserData(EditorMarkupModelImpl.DISABLE_CODE_LENS)) &&
        !UIUtil.uiParents(myEditor.getComponent(), false).filter(EditorWindowHolder.class).isEmpty()) {
      float rowRatio = (float)visualLine / (myEditor.getVisibleLineCount() - 1);
      int y = myRowAdjuster != 0 ? (int)(rowRatio * myEditor.getVerticalScrollBar().getHeight()) : e.getY() + 1;
      List<RangeHighlighterEx> highlighters = new ArrayList<>();
      collectRangeHighlighters(this, visualLine, highlighters);
      collectRangeHighlighters(myEditor.getFilteredDocumentMarkupModel(), visualLine, highlighters);
      myEditorFragmentRenderer.show(visualLine, highlighters, e.isAltDown(), createHint(e.getComponent(), new Point(0, y)));
      return true;
    }

    Set<RangeHighlighter> highlighters = getNearestHighlighters(e.getY() + 1);
    if (highlighters.isEmpty()) {
      return false;
    }

    int y;
    RangeHighlighter nearest = getNearestRangeHighlighter(e);
    if (nearest == null) {
      y = e.getY();
    }
    else {
      ProperTextRange range = offsetsToYPositions(nearest.getStartOffset(), nearest.getEndOffset());
      int eachStartY = range.getStartOffset();
      int eachEndY = range.getEndOffset();
      y = eachStartY + (eachEndY - eachStartY) / 2;
    }
    if (currentHint != null && y == myCurrentHintAnchorY) {
      return true;
    }
    ReadAction.nonBlocking(() -> myTooltipRendererProvider.calcTooltipRenderer(highlighters))
      .expireWhen(() -> myEditor.isDisposed())
      .finishOnUiThread(ModalityState.nonModal(), bigRenderer -> {
        if (bigRenderer != null) {
          LightweightHint hint = showTooltip(bigRenderer, createHint(e.getComponent(), new Point(0, y + 1)).setForcePopup(true));
          myCurrentHint = new WeakReference<>(hint);
          myCurrentHintAnchorY = y;
          myKeepHint = false;
          myMouseMovementTracker.reset();
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    return true;
  }

  private static @NotNull HintHint createHint(Component component, Point point) {
    return new HintHint(component, point)
      .setAwtTooltip(true)
      .setPreferredPosition(Balloon.Position.atLeft)
      .setBorderInsets(JBUI.insets(EditorFragmentRenderer.EDITOR_FRAGMENT_POPUP_BORDER))
      .setShowImmediately(true)
      .setAnimationEnabled(false)
      .setStatus(HintHint.Status.Info);
  }

  private int getVisualLineByEvent(@NotNull MouseEvent e) {
    int y = e.getY();
    if (e.getSource() == myEditor.getVerticalScrollBar() && y == myEditor.getVerticalScrollBar().getHeight() - 1) {
      y++;
    }
    return fitLineToEditor(myEditor, myEditor.offsetToVisualLine(yPositionToOffset(y + myWheelAccumulator, true)));
  }

  static int fitLineToEditor(@NotNull EditorImpl editor, int visualLine) {
    int lineCount = editor.getVisibleLineCount();
    int shift = 0;
    if (visualLine >= lineCount - 1) {
      CharSequence sequence = editor.getDocument().getCharsSequence();
      shift = sequence.isEmpty() ? 0 : sequence.charAt(sequence.length() - 1) == '\n' ? 1 : 0;
    }
    return Math.max(0, Math.min(lineCount - shift, visualLine));
  }

  private int getOffset(int visualLine, boolean startLine) {
    return myEditor.visualPositionToOffset(new VisualPosition(visualLine, startLine ? 0 : Integer.MAX_VALUE));
  }

  private void collectRangeHighlighters(@NotNull MarkupModelEx markupModel,
                                        int visualLine,
                                        @NotNull Collection<? super RangeHighlighterEx> highlighters) {
    int startOffset = getOffset(fitLineToEditor(myEditor, visualLine - EditorFragmentRenderer.PREVIEW_LINES), true);
    int endOffset = getOffset(fitLineToEditor(myEditor, visualLine + EditorFragmentRenderer.PREVIEW_LINES), false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      Object tooltip = highlighter.getErrorStripeTooltip();
      if (tooltip != null &&
          !(tooltip instanceof HighlightInfo && ((HighlightInfo)tooltip).type == HighlightInfoType.TODO) &&
          highlighter.getStartOffset() < endOffset &&
          highlighter.getEndOffset() > startOffset &&
          highlighter.getErrorStripeMarkColor(myEditor.getColorsScheme()) != null) {
        highlighters.add(highlighter);
      }
      return true;
    });
  }

  private @Nullable RangeHighlighter getNearestRangeHighlighter(@NotNull MouseEvent e) {
    Set<RangeHighlighter> highlighters = getNearestHighlighters(e.getY());
    RangeHighlighter nearestMarker = null;
    int yPos = 0;
    for (RangeHighlighter highlighter : highlighters) {
      int newYPos = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset()).getStartOffset();

      if (nearestMarker == null || Math.abs(yPos - e.getY()) > Math.abs(newYPos - e.getY())) {
        nearestMarker = highlighter;
        yPos = newYPos;
      }
    }
    return nearestMarker;
  }

  private @NotNull Set<RangeHighlighter> getNearestHighlighters(int y) {
    Set<RangeHighlighter> highlighters = new HashSet<>();
    addNearestHighlighters(this, y, highlighters);
    addNearestHighlighters(myEditor.getFilteredDocumentMarkupModel(), y, highlighters);
    return highlighters;
  }

  private void addNearestHighlighters(@NotNull MarkupModelEx markupModel,
                                      int scrollBarY,
                                      @NotNull Collection<? super RangeHighlighter> result) {
    int startOffset = yPositionToOffset(scrollBarY - getMinMarkHeight(), true);
    int endOffset = yPositionToOffset(scrollBarY + getMinMarkHeight(), false);
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, highlighter -> {
      if (highlighter.getErrorStripeMarkColor(myEditor.getColorsScheme()) != null) {
        ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
        if (scrollBarY >= range.getStartOffset() - getMinMarkHeight() * 2 &&
            scrollBarY <= range.getEndOffset() + getMinMarkHeight() * 2) {
          result.add(highlighter);
        }
      }
      return true;
    });
  }

  private void doClick(@NotNull MouseEvent e) {
    RangeHighlighter marker = getNearestRangeHighlighter(e);
    int offset;
    LogicalPosition logicalPositionToScroll = null;
    LightweightHint editorPreviewHint = myEditorFragmentRenderer.getEditorPreviewHint();
    if (marker == null) {
      if (editorPreviewHint != null) {
        logicalPositionToScroll = myEditor.visualToLogicalPosition(new VisualPosition(myEditorFragmentRenderer.getStartVisualLine(), 0));
        offset = myEditor.getDocument().getLineStartOffset(logicalPositionToScroll.line);
      }
      else {
        return;
      }
    }
    else {
      offset = marker.getStartOffset();
    }

    Document doc = myEditor.getDocument();
    if (doc.getLineCount() > 0 && editorPreviewHint == null) {
      // Necessary to expand folded block even if navigating just before one
      // Very useful when navigating to the first unused import statement.
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
      int relativePopupOffset = myEditorFragmentRenderer.getRelativeY();
      scrollingModel.scrollVertically(lineY - relativePopupOffset);
    }
    else {
      scrollingModel.scrollToCaret(ScrollType.CENTER);
    }
    scrollingModel.enableAnimation();
    if (marker != null) {
      myErrorStripeMarkersModel.fireErrorMarkerClicked(marker, e);
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
    myErrorStripeMarkersModel.setActive(val);
  }

  private @Nullable MyErrorPanel getErrorPanel() {
    ScrollBarUI ui = myEditor.getVerticalScrollBar().getUI();
    return ui instanceof MyErrorPanel ? (MyErrorPanel)ui : null;
  }

  @NotNull
  ErrorStripeMarkersModel getErrorStripeMarkersModel() {
    return myErrorStripeMarkersModel;
  }

  @Override
  public void setErrorPanelPopupHandler(@NotNull PopupHandler handler) {
    ThreadingAssertions.assertEventDispatchThread();
    MyErrorPanel errorPanel = getErrorPanel();
    if (errorPanel != null) {
      errorPanel.setPopupHandler(handler);
    }
  }

  @Override
  public void setErrorStripTooltipRendererProvider(@NotNull ErrorStripTooltipRendererProvider provider) {
    myTooltipRendererProvider = provider;
  }

  @Override
  public @NotNull ErrorStripTooltipRendererProvider getErrorStripTooltipRendererProvider() {
    return myTooltipRendererProvider;
  }

  @Override
  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public @NotNull ActionToolbar getStatusToolbar() {
    return statusToolbar;
  }

  @Override
  public void setErrorStripeRenderer(@Nullable ErrorStripeRenderer renderer) {
    ThreadingAssertions.assertEventDispatchThread();
    if (myErrorStripeRenderer instanceof Disposable) {
      Disposer.dispose((Disposable)myErrorStripeRenderer);
    }
    myErrorStripeRenderer = renderer;
    if (renderer instanceof Disposable) {
      Disposer.register(resourcesDisposable, (Disposable)renderer);
    }
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();
  }

  @Override
  public @Nullable ErrorStripeRenderer getErrorStripeRenderer() {
    return myErrorStripeRenderer;
  }

  @Override
  public void dispose() {
    Disposer.dispose(resourcesDisposable);

    disposeErrorPanel();

    statusToolbar.getComponent().removeComponentListener(toolbarComponentListener);
    ((JBScrollPane)myEditor.getScrollPane()).setStatusComponent(null);

    myErrorStripeRenderer = null;
    myTooltipRendererProvider = new BasicTooltipRendererProvider();
    myEditorFragmentRenderer.clearHint();

    myTrafficLightPopup.hidePopup();
    extensionActions.clear();

    super.dispose();
  }

  private void disposeErrorPanel() {
    MyErrorPanel panel = getErrorPanel();
    if (panel != null) {
      panel.uninstallListeners();
    }
  }

  public void rebuild() {
    myErrorStripeMarkersModel.rebuild();
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
  private final class MyErrorPanel extends ButtonlessScrollBarUI
    implements MouseMotionListener, MouseListener, MouseWheelListener, UISettingsListener {
    private PopupHandler myHandler;
    private @Nullable BufferedImage myCachedTrack;
    private int myCachedHeight = -1;

    void dropCache() {
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

      myTrafficLightPopup.updateVisiblePopup(analyzerStatus);
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
      int b2 = bounds.width / 2;
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
        ReadAction.run(() -> doPaintTrack(g, c, trackBounds));
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
        Graphics2D imageGraphics = myCachedTrack.createGraphics();

        myDirtyYPositions = myDirtyYPositions.intersection(docRange);
        if (myDirtyYPositions == null) myDirtyYPositions = docRange;
        repaint(imageGraphics, componentBounds.width, myDirtyYPositions);
        myDirtyYPositions = null;
      }

      StartupUiUtil.drawImage(g, myCachedTrack);
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

    @Override
    protected @NotNull Color adjustColor(Color c) {
      return isMacOverlayScrollbar() ? super.adjustColor(c) : EditorImpl.adjustThumbColor(super.adjustColor(c), isDark());
    }

    private void repaint(@NotNull Graphics g, int gutterWidth, @NotNull ProperTextRange yRange) {
      Rectangle clip = new Rectangle(0, yRange.getStartOffset(), gutterWidth, yRange.getLength() + getMinMarkHeight());
      paintTrackBasement(g, clip);

      int startOffset = yPositionToOffset(clip.y - getMinMarkHeight(), true);
      int endOffset = yPositionToOffset(clip.y + clip.height, false);

      Shape oldClip = g.getClip();
      g.clipRect(clip.x, clip.y, clip.width, clip.height);

      drawErrorStripeMarkers(g, startOffset, endOffset);

      g.setClip(oldClip);
    }

    private void drawErrorStripeMarkers(@NotNull Graphics g, int startOffset, int endOffset) {
      Queue<PositionedStripe> thinEnds = new PriorityQueue<>(5, Comparator.comparingInt(o -> o.yEnd));
      Queue<PositionedStripe> wideEnds = new PriorityQueue<>(5, Comparator.comparingInt(o -> o.yEnd));
      // sorted by layer
      List<PositionedStripe> thinStripes = new ArrayList<>(); // layer desc
      List<PositionedStripe> wideStripes = new ArrayList<>(); // layer desc
      int[] thinYStart = new int[1];  // in range 0...yStart all spots are drawn
      int[] wideYStart = new int[1];  // in range 0...yStart all spots are drawn

      MarkupIterator<RangeHighlighterEx> iterator = myErrorStripeMarkersModel.highlighterIterator(startOffset, endOffset);
      try {
        ContainerUtil.process(iterator, highlighter -> {
          boolean isThin = highlighter.isThinErrorStripeMark();
          int[] yStart = isThin ? thinYStart : wideYStart;
          List<PositionedStripe> stripes = isThin ? thinStripes : wideStripes;
          Queue<PositionedStripe> ends = isThin ? thinEnds : wideEnds;

          ProperTextRange range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset());
          int ys = range.getStartOffset();
          int ye = range.getEndOffset();
          if (ye - ys < getMinMarkHeight()) ye = ys + getMinMarkHeight();

          yStart[0] = drawStripesEndingBefore(ys, ends, stripes, g, yStart[0]);

          int layer = highlighter.getLayer();

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
          EditorColorsScheme colorsScheme = myEditor.getColorsScheme();
          Color color = highlighter.getErrorStripeMarkColor(colorsScheme);
          if (color == null) {
            if (reportErrorStripeInconsistency) {
              reportErrorStripeInconsistency = false;
              LOG.error("Error stripe marker has no color. highlighter: " + highlighter +
                        ", color scheme: " + colorsScheme + " (" + colorsScheme.getClass() + ")");
            }
            return true;
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

        // check whether endingStripe got obscured in the range yStart...endingStripe.yEnd
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
    public void mouseClicked(@NotNull MouseEvent e) {
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
      IdeFocusManager.getGlobalInstance()
        .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEditor.getContentComponent(), true));
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
      int lineCount = getDocument().getLineCount() + myEditor.getSettings().getAdditionalLinesCount();
      if (lineCount == 0) {
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
      if (myEditorFragmentRenderer.getEditorPreviewHint() == null) {
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

    private void cancelMyToolTips(@NotNull MouseEvent e, boolean checkIfShouldSurvive) {
      hideMyEditorPreviewHint();
      TooltipController tooltipController = TooltipController.getInstance();
      if (!checkIfShouldSurvive || !tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP, e, true);
      }
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent e) {
    }

    @Override
    public void mouseExited(@NotNull MouseEvent e) {
      hideMyEditorPreviewHint();
      LightweightHint currentHint = getCurrentHint();
      if (currentHint != null && !myKeepHint) {
        closeHintOnMovingMouseAway(currentHint);
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
    myEditorFragmentRenderer.hideHint();
    myRowAdjuster = 0;
    myWheelAccumulator = 0;
    myLastVisualLine = 0;
  }

  private LightweightHint showTooltip(@NotNull TooltipRenderer tooltipObject, @NotNull HintHint hintHint) {
    hideMyEditorPreviewHint();
    return TooltipController.getInstance().showTooltipByMouseMove(myEditor, hintHint.getTargetPoint(), tooltipObject,
                                                                  myEditor.getVerticalScrollbarOrientation() ==
                                                                  EditorEx.VERTICAL_SCROLLBAR_RIGHT, ERROR_STRIPE_TOOLTIP_GROUP, hintHint);
  }

  @Override
  public void addErrorMarkerListener(@NotNull ErrorStripeListener listener, @NotNull Disposable parent) {
    myErrorStripeMarkersModel.addErrorMarkerListener(listener, parent);
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

  private static final class BasicTooltipRendererProvider implements ErrorStripTooltipRendererProvider {
    @Override
    public TooltipRenderer calcTooltipRenderer(@NotNull Collection<? extends RangeHighlighter> highlighters) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      LineTooltipRenderer bigRenderer = null;
      //do not show the same tooltip twice
      Set<String> tooltips = null;

      for (RangeHighlighter highlighter : highlighters) {
        Object tooltipObject = highlighter.getErrorStripeTooltip();
        if (tooltipObject == null) continue;

        //noinspection HardCodedStringLiteral
        String text = tooltipObject instanceof HighlightInfo ? ((HighlightInfo)tooltipObject).getToolTip() : tooltipObject.toString();
        if (text == null) continue;

        if (tooltips == null) {
          tooltips = new HashSet<>();
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

    @Override
    public @NotNull TooltipRenderer calcTooltipRenderer(@NotNull String text) {
      return new LineTooltipRenderer(text, new Object[]{text});
    }

    @Override
    public @NotNull TooltipRenderer calcTooltipRenderer(@NotNull String text, int width) {
      return new LineTooltipRenderer(text, width, new Object[]{text});
    }
  }

  private @NotNull ProperTextRange offsetsToYPositions(int start, int end) {
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
      endY = startY; // both offsets are on the same line, no need to re-calc Y position
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
    int safeY = Math.max(0, y - myEditorScrollbarTop);
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

    FoldingModelEx foldingModel = myEditor.getFoldingModel();
    if (beginLine) {
      int offset = document.getLineStartOffset(line);
      FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.min(offset, startCollapsed.getStartOffset()) : offset;
    }
    else {
      int offset = document.getLineEndOffset(line);
      FoldRegion startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset);
      return startCollapsed != null ? Math.max(offset, startCollapsed.getEndOffset()) : offset;
    }
  }

  public static final Key<Boolean> DISABLE_CODE_LENS = new Key<>("DISABLE_CODE_LENS");
  private static final Key<List<StatusItem>> EXPANDED_STATUS = new Key<>("EXPANDED_STATUS");
  private static final Key<Boolean> TRANSLUCENT_STATE = new Key<>("TRANSLUCENT_STATE");

  private final class TrafficLightAction extends DumbAwareAction
    implements CustomComponentAction, ActionRemoteBehaviorSpecification.Frontend {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return new TrafficLightButton(this, presentation, new EditorToolbarButtonLook(), place, myEditor.getColorsScheme());
    }

    @Override
    public void updateCustomComponent(@NotNull JComponent component, @NotNull Presentation presentation) {
      ((TrafficLightButton)component).updateFromPresentation(presentation);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTrafficLightPopup.hidePopup();
      analyzerStatus.getController().toggleProblemsView();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();

      if (RedesignedInspectionsManager.isAvailable()) {
        presentation.setEnabledAndVisible(false);
        return;
      }

      List<StatusItem> newStatus = analyzerStatus.getExpandedStatus();
      Icon newIcon = analyzerStatus.getIcon();

      presentation.setVisible(!analyzerStatus.isEmpty());

      if (!hasAnalyzed || analyzerStatus.getAnalyzingType() != AnalyzingType.EMPTY) {
        List<StatusItem> adjusted = newStatus.isEmpty() ? Collections.singletonList(new StatusItem("", newIcon)) : newStatus;
        presentation.putClientProperty(EXPANDED_STATUS, adjusted);

        presentation.putClientProperty(TRANSLUCENT_STATE, analyzerStatus.getAnalyzingType() != AnalyzingType.COMPLETE);
      }
      else {
        presentation.putClientProperty(TRANSLUCENT_STATE, true);
      }
    }
  }

  private final class TrafficLightButton extends JPanel {
    private static final int LEFT_RIGHT_INDENT = 5;
    private static final int INTER_GROUP_OFFSET = 6;

    private boolean mousePressed;
    private boolean mouseHover;
    private final ActionButtonLook buttonLook;
    private final MouseListener mouseListener;
    private final EditorColorsScheme colorsScheme;
    private List<StatusItem> items;
    private boolean translucent;

    TrafficLightButton(@NotNull AnAction action,
                       @NotNull Presentation presentation,
                       @NotNull ActionButtonLook buttonLook,
                       @NotNull String place,
                       @NotNull EditorColorsScheme colorsScheme) {
      setLayout(new GridBagLayout());
      setOpaque(false);

      this.buttonLook = buttonLook;
      this.colorsScheme = colorsScheme;

      mouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent me) {
          if (SwingUtilities.isLeftMouseButton(me)) {
            showInspectionHint(me);
          }
        }

        private void showInspectionHint(MouseEvent me) {
          DataContext context = ActionToolbar.getDataContextFor(TrafficLightButton.this);
          AnActionEvent event = AnActionEvent.createFromInputEvent(me, place, presentation, context, false, true);
          if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
            ActionUtil.performActionDumbAwareWithCallbacks(action, event);
            ActionsCollector.getInstance().record(event.getProject(), action, event, null);

            ActionToolbar toolbar = ActionToolbar.findToolbarBy(TrafficLightButton.this);
            if (toolbar != null) {
              toolbar.updateActionsImmediately();
            }
          }
        }

        private static void showContextMenu(MouseEvent me) {
          DefaultActionGroup group = new DefaultActionGroup();
          /*
          TODO: show context menu by right click
          group.addAll(analyzerStatus.getController().getActions());
          group.add(new CompactViewAction());
          */
          if (0 < group.getChildrenCount()) {
            JBPopupMenu.showByEvent(me, ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR, group);
          }
        }

        @Override
        public void mousePressed(MouseEvent me) {
          if (me.isPopupTrigger()) showContextMenu(me);
          mousePressed = true;
          repaint();
        }

        @Override
        public void mouseReleased(MouseEvent me) {
          if (me.isPopupTrigger()) showContextMenu(me);
          mousePressed = false;
          repaint();
        }

        @Override
        public void mouseEntered(MouseEvent me) {
          mouseHover = true;
          myTrafficLightPopup.scheduleShow(me, analyzerStatus);
          repaint();
        }

        @Override
        public void mouseExited(MouseEvent me) {
          mouseHover = false;
          myTrafficLightPopup.scheduleHide();
          repaint();
        }
      };

      setBorder(new Border() {
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) { }

        @Override
        public boolean isBorderOpaque() {
          return false;
        }

        @Override
        public Insets getBorderInsets(Component c) {
          return showNavigation ? JBUI.insets(2, 2, 2, 0) : JBUI.insets(2);
        }
      });
    }

    private void updateFromPresentation(@NotNull Presentation presentation) {
      boolean newTranslucent = Boolean.TRUE.equals(presentation.getClientProperty(TRANSLUCENT_STATE));
      List<StatusItem> newItems = presentation.getClientProperty(EXPANDED_STATUS);
      if (translucent == newTranslucent && Objects.equals(items, newItems)) {
        return;
      }
      translucent = newTranslucent;
      items = newItems;
      updateContents(ContainerUtil.notNullize(newItems));
      revalidate();
      repaint();
    }


    @Override
    public void addNotify() {
      super.addNotify();
      addMouseListener(mouseListener);
    }

    @Override
    public void removeNotify() {
      removeMouseListener(mouseListener);
    }

    private void updateContents(@NotNull List<StatusItem> status) {
      removeAll();

      setEnabled(!status.isEmpty());
      setVisible(!status.isEmpty());

      GridBag gc = new GridBag().nextLine();
      if (status.size() == 1 && StringUtil.isEmpty(status.get(0).getText())) {
        add(createStyledLabel(null, status.get(0).getIcon(), SwingConstants.CENTER),
            gc.next().weightx(1).weighty(1).fillCell());
      }
      else if (!status.isEmpty()) {
        int leftRightOffset = JBUIScale.scale(LEFT_RIGHT_INDENT);
        add(Box.createHorizontalStrut(leftRightOffset), gc.next());

        int counter = 0;
        for (StatusItem item : status) {
          add(createStyledLabel(item.getText(), item.getIcon(), SwingConstants.LEFT),
              gc.next().insetLeft(counter++ > 0 ? INTER_GROUP_OFFSET : 0).fillCell().weighty(1));
        }

        add(Box.createHorizontalStrut(leftRightOffset), gc.next());
      }
    }

    private @NotNull JLabel createStyledLabel(@Nullable @Nls String text, @Nullable Icon icon, int alignment) {
      JLabel label = new JLabel(text, icon, alignment) {
        @Override
        protected void paintComponent(@NotNull Graphics graphics) {
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

        @Override
        public void setUI(LabelUI ui) {
          super.setUI(ui);

          if (!SystemInfo.isWindows) {
            Font font = getFont();
            font =
              new FontUIResource(font.deriveFont(font.getStyle(), font.getSize() - JBUIScale.scale(2))); // Allow resetting the font by UI
            setFont(font);
          }
        }
      };

      label.setForeground(
        JBColor.lazy(() -> ObjectUtils.notNull(colorsScheme.getColor(ICON_TEXT_COLOR), ICON_TEXT_COLOR.getDefaultColor())));
      label.setIconTextGap(JBUIScale.scale(1));

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
    public @NotNull Dimension getPreferredSize() {
      if (getComponentCount() == 0) {
        return JBUI.emptySize();
      }

      Dimension size = super.getPreferredSize();
      Insets i = getInsets();
      size.height = Math.max(getStatusIconSize() + i.top + i.bottom, size.height);
      size.width = Math.max(getStatusIconSize() + i.left + i.right, size.width);
      return size;
    }
  }

  private static final class StatusComponentLayout implements LayoutManager {
    private final List<Pair<Component, String>> actionButtons = new ArrayList<>();

    @Override
    public void addLayoutComponent(String s, Component component) {
      actionButtons.add(Pair.pair(component, s));
    }

    @Override
    public void removeLayoutComponent(Component component) {
      for (int i = 0; i < actionButtons.size(); i++) {
        if (Comparing.equal(component, actionButtons.get(i).first)) {
          actionButtons.remove(i);
          break;
        }
      }
    }

    @Override
    public @NotNull Dimension preferredLayoutSize(Container container) {
      Dimension size = JBUI.emptySize();

      for (Pair<Component, String> c : actionButtons) {
        if (c.first.isVisible()) {
          Dimension prefSize = c.first.getPreferredSize();
          size.height = Math.max(size.height, prefSize.height);
        }
      }

      for (Pair<Component, String> c : actionButtons) {
        if (c.first.isVisible()) {
          Dimension prefSize = c.first.getPreferredSize();
          Insets i = ((JComponent)c.first).getInsets();
          JBInsets.removeFrom(prefSize, i);

          if (ActionToolbar.SEPARATOR_CONSTRAINT.equals(c.second)) {
            size.width += prefSize.width + i.left + i.right;
          }
          else {
            int maxBareHeight = size.height - i.top - i.bottom;
            size.width += Math.max(prefSize.width, maxBareHeight) + i.left + i.right;
          }
        }
      }

      if (size.width > 0 && size.height > 0) {
        JBInsets.addTo(size, container.getInsets());
      }
      return size;
    }

    @Override
    public @NotNull Dimension minimumLayoutSize(Container container) {
      return preferredLayoutSize(container);
    }

    @Override
    public void layoutContainer(Container container) {
      Dimension prefSize = preferredLayoutSize(container);

      if (prefSize.width > 0 && prefSize.height > 0) {
        Insets i = container.getInsets();
        JBInsets.removeFrom(prefSize, i);
        int offset = i.left;

        for (Pair<Component, String> c : actionButtons) {
          if (c.first.isVisible()) {
            Dimension cPrefSize = c.first.getPreferredSize();

            if (c.first instanceof TrafficLightButton) {
              c.first.setBounds(offset, i.top, cPrefSize.width, prefSize.height);
              offset += cPrefSize.width;
            }
            else {
              Insets jcInsets = ((JComponent)c.first).getInsets();
              JBInsets.removeFrom(cPrefSize, jcInsets);

              if (ActionToolbar.SEPARATOR_CONSTRAINT.equals(c.second)) {
                c.first.setBounds(offset, i.top, cPrefSize.width, prefSize.height);
                offset += cPrefSize.width;
              }
              else {
                int maxBareHeight = prefSize.height - jcInsets.top - jcInsets.bottom;
                int width = Math.max(cPrefSize.width, maxBareHeight) + jcInsets.left + jcInsets.right;

                c.first.setBounds(offset, i.top, width, prefSize.height);
                offset += width;
              }
            }
          }
        }
      }
    }
  }

  private final class EditorToolbarButtonLook extends ActionButtonLook {
    @Override
    public void paintBorder(Graphics g, JComponent component, int state) { }

    @Override
    public void paintBorder(Graphics g, JComponent component, Color color) { }

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
    public void paintBackground(Graphics g, JComponent component, Color color) {
      ActionButtonLook.SYSTEM_LOOK.paintBackground(g, component, color);
    }

    @Override
    public void paintIcon(Graphics g, ActionButtonComponent actionButton, Icon icon, int x, int y) {
      if (icon != null) {
        boolean isDark = ColorUtil.isDark(myEditor.getColorsScheme().getDefaultBackground());
        super.paintIcon(g, actionButton, IconLoader.getDarkIcon(icon, isDark), x, y);
      }
    }
  }

  public final class CompactViewAction extends ToggleAction {
    CompactViewAction() {
      super(EditorBundle.message("iw.compact.view"));
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return !showToolbar;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      showToolbar = !state;
      EditorSettingsExternalizable.getInstance().setShowInspectionWidget(showToolbar);
      updateTrafficLightVisibility();
      ActionsCollector.getInstance().record(e.getProject(), this, e, null);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(analyzerStatus.getController().isToolbarEnabled());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }
  }

  private class MarkupModelDelegateAction extends AnActionWrapper {

    MarkupModelDelegateAction(@NotNull AnAction delegate) {
      super(delegate);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      IdeFocusManager focusManager = IdeFocusManager.getInstance(myEditor.getProject());

      AnActionEvent delegateEvent = AnActionEvent.createFromAnAction(getDelegate(),
                                                                     e.getInputEvent(),
                                                                     ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR,
                                                                     myEditor.getDataContext());

      if (focusManager.getFocusOwner() != myEditor.getContentComponent()) {
        focusManager.requestFocus(myEditor.getContentComponent(), true).
          doWhenDone(() -> getDelegate().actionPerformed(delegateEvent));
      }
      else {
        getDelegate().actionPerformed(delegateEvent);
      }
    }
  }

  private class NavigationGroup extends DefaultActionGroup implements ActionRemoteBehaviorSpecification.Frontend {

    NavigationGroup(AnAction @NotNull ... actions) {
      super(actions);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(showNavigation);
    }
  }

  private static class ExtraActionGroup extends DefaultActionGroup implements ActionWithMergeId {
  }

  @ApiStatus.Internal
  public static class StatusToolbarGroup extends DefaultActionGroup {

    StatusToolbarGroup(AnAction @NotNull ... actions) {
      super(actions);
    }
  }
}
