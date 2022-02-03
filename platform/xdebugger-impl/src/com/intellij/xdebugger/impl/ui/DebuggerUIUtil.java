// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.AnActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory;
import com.intellij.xdebugger.impl.breakpoints.ui.XLightBreakpointPropertiesPanel;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public final class DebuggerUIUtil {
  @NonNls public static final String FULL_VALUE_POPUP_DIMENSION_KEY = "XDebugger.FullValuePopup";
  @NonNls public static final String TEXT_VIEWER_POPUP_DIMENSION_KEY = "XDebugger.TextViewerPopup";

  private DebuggerUIUtil() {
  }

  public static void enableEditorOnCheck(final JCheckBox checkbox, final JComponent textfield) {
    checkbox.addActionListener(e -> {
      boolean selected = checkbox.isSelected();
      textfield.setEnabled(selected);
    });
    textfield.setEnabled(checkbox.isSelected());
  }

  public static void focusEditorOnCheck(final JCheckBox checkbox, final JComponent component) {
    final Runnable runnable = () -> getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(component, true));
    checkbox.addActionListener(e -> {
      if (checkbox.isSelected()) {
        SwingUtilities.invokeLater(runnable);
      }
    });
  }

  public static void invokeLater(Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  @Nullable
  public static RelativePoint getPositionForPopup(@NotNull Editor editor, int line) {
    if (line > -1) {
      Point p = editor.logicalPositionToXY(new LogicalPosition(line + 1, 0));
      if (editor.getScrollingModel().getVisibleArea().contains(p)) {
        return new RelativePoint(editor.getContentComponent(), p);
      }
    }
    return null;
  }

  public static void showPopupForEditorLine(@NotNull JBPopup popup, @NotNull Editor editor, int line) {
    RelativePoint point = getPositionForPopup(editor, line);
    if (point != null) {
      popup.addListener(new JBPopupListener() {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          Window window = popup.isDisposed()  ? null : ComponentUtil.getWindow(popup.getContent());
          if (window != null) {
            Point expected = point.getScreenPoint();
            Rectangle screen = ScreenUtil.getScreenRectangle(expected);
            int y = expected.y - window.getHeight() - editor.getLineHeight();
            if (screen.y < y) window.setLocation(window.getX(), y);
          }
        }
      });
      popup.show(point);
    }
    else {
      Project project = editor.getProject();
      if (project != null) {
        popup.showCenteredInCurrentWindow(project);
      }
      else {
        popup.showInFocusCenter();
      }
    }
  }

  public static void showValuePopup(@NotNull XFullValueEvaluator evaluator, @NotNull MouseEvent event, @NotNull Project project, @Nullable Editor editor) {
    EditorTextField textArea = createTextViewer(XDebuggerUIConstants.getEvaluatingExpressionMessage(), project);

    final FullValueEvaluationCallbackImpl callback = new FullValueEvaluationCallbackImpl(textArea);
    evaluator.startEvaluation(callback);

    Dimension size = DimensionService.getInstance().getSize(FULL_VALUE_POPUP_DIMENSION_KEY, project);
    if (size == null) {
      Dimension frameSize = WindowManager.getInstance().getFrame(project).getSize();
      size = new Dimension(frameSize.width / 2, frameSize.height / 2);
    }

    textArea.setPreferredSize(size);

    JBPopup popup = createValuePopup(project, textArea, callback);
    if (editor == null) {
      Rectangle bounds = new Rectangle(event.getLocationOnScreen(), size);
      ScreenUtil.fitToScreenVertical(bounds, 5, 5, true);
      if (size.width != bounds.width || size.height != bounds.height) {
        size = bounds.getSize();
        textArea.setPreferredSize(size);
      }
      popup.showInScreenCoordinates(event.getComponent(), bounds.getLocation());
    }
    else {
      popup.showInBestPositionFor(editor);
    }
  }

  @ApiStatus.Experimental
  public static TextViewer createTextViewer(@NotNull String initialText, @NotNull Project project) {
    TextViewer textArea = new TextViewer(initialText, project);
    textArea.setBackground(HintUtil.getInformationColor());

    textArea.addSettingsProvider(e -> {
      e.getScrollPane().setBorder(JBUI.Borders.empty());
      e.getScrollPane().setViewportBorder(JBUI.Borders.empty());
    });

    return textArea;
  }

  @NotNull
  private static FullValueEvaluationCallbackImpl startEvaluation(@NotNull TextViewer textViewer,
                                                                 @NotNull XFullValueEvaluator evaluator,
                                                                 @Nullable Runnable afterFullValueEvaluation) {
    FullValueEvaluationCallbackImpl callback = new FullValueEvaluationCallbackImpl(textViewer) {
      @Override
      public void evaluated(@NotNull String fullValue) {
        super.evaluated(fullValue);
        AppUIUtil.invokeOnEdt(() -> {
          if (afterFullValueEvaluation != null) {
            afterFullValueEvaluation.run();
          }
        });
      }
    };
    evaluator.startEvaluation(callback);
    return callback;
  }

  @ApiStatus.Experimental
  public static JBPopup createTextViewerPopup(@NotNull TextViewer textViewer,
                                              @NotNull XFullValueEvaluator evaluator,
                                              @Nullable Runnable afterFullValueEvaluation,
                                              @NotNull Project project,
                                              @Nullable Runnable hideRunnable,
                                              @Nullable JComponent bottomToolBar) {
    Dimension  preferredSize  = DimensionService.getInstance().getSize(TEXT_VIEWER_POPUP_DIMENSION_KEY, project);
    if (preferredSize == null) {
      Dimension frameSize = WindowManager.getInstance().getFrame(project).getSize();
      preferredSize = new Dimension(frameSize.width / 4, frameSize.height / 4);
    }
    textViewer.setPreferredSize(preferredSize);

    final @NotNull FullValueEvaluationCallbackImpl callback = startEvaluation(textViewer, evaluator, afterFullValueEvaluation);

    Runnable cancelCallback = () -> {
      callback.setObsolete();
      if (hideRunnable != null) {
        hideRunnable.run();
      }
    };

    BorderLayoutPanel componentToShow = JBUI.Panels.simplePanel();
    componentToShow.addToCenter(textViewer);
    if (bottomToolBar != null) {
      componentToShow.addToBottom(bottomToolBar);
    }

    return createCancelablePopup(project, componentToShow, cancelCallback, TEXT_VIEWER_POPUP_DIMENSION_KEY);
  }

  public static JBPopup createValuePopup(Project project,
                                          JComponent component,
                                          @Nullable final FullValueEvaluationCallbackImpl callback) {
    Runnable cancelCallback = callback == null ? null : () -> callback.setObsolete();
    return createCancelablePopup(project, component, cancelCallback, FULL_VALUE_POPUP_DIMENSION_KEY);
  }

  private static JBPopup createCancelablePopup(Project project,
                                               JComponent component,
                                               @Nullable Runnable cancelCallback,
                                               @NotNull String dimensionKey) {
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null);
    builder.setResizable(true)
      .setMovable(true)
      .setDimensionServiceKey(project, dimensionKey, false)
      .setRequestFocus(true);
    if (cancelCallback != null) {
      builder.setCancelCallback(() -> {
        cancelCallback.run();
        return true;
      });
    }
    return builder.createPopup();
  }

  public static void showXBreakpointEditorBalloon(final Project project,
                                                  @Nullable final Point point,
                                                  final JComponent component,
                                                  final boolean showAllOptions,
                                                  final XBreakpoint breakpoint) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    final XLightBreakpointPropertiesPanel propertiesPanel =
      new XLightBreakpointPropertiesPanel(project, breakpointManager, (XBreakpointBase)breakpoint,
                                          showAllOptions);

    final Ref<Balloon> balloonRef = Ref.create(null);
    final Ref<Boolean> isLoading = Ref.create(Boolean.FALSE);
    final Ref<Boolean> moreOptionsRequested = Ref.create(Boolean.FALSE);

    propertiesPanel.setDelegate(() -> {
      if (!isLoading.get()) {
        propertiesPanel.saveProperties();
      }
      if (!balloonRef.isNull()) {
        balloonRef.get().hide();
      }
      propertiesPanel.dispose();
      showXBreakpointEditorBalloon(project, point, component, true, breakpoint);
      moreOptionsRequested.set(true);
    });

    isLoading.set(Boolean.TRUE);
    propertiesPanel.loadProperties();
    isLoading.set(Boolean.FALSE);

    if (moreOptionsRequested.get()) {
      return;
    }

    Runnable showMoreOptions = () -> {
      propertiesPanel.saveProperties();
      propertiesPanel.dispose();
      BreakpointsDialogFactory.getInstance(project).showDialog(breakpoint);
    };

    final JComponent mainPanel = propertiesPanel.getMainPanel();
    final Balloon balloon = showBreakpointEditor(project, mainPanel, point, component, showMoreOptions, breakpoint);
    balloonRef.set(balloon);

    Disposable disposable = Disposer.newDisposable();

    balloon.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        Disposer.dispose(disposable);
        propertiesPanel.saveProperties();
        propertiesPanel.dispose();
      }
    });

    project.getMessageBus().connect(disposable).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
      @Override
      public void breakpointRemoved(@NotNull XBreakpoint<?> removedBreakpoint) {
        if (removedBreakpoint.equals(breakpoint)) {
          balloon.hide();
        }
      }
    });
    ApplicationManager.getApplication().invokeLater(() -> IdeFocusManager.findInstance().requestFocus(mainPanel, true));
  }

  public static Balloon showBreakpointEditor(Project project, final JComponent mainPanel,
                                             final Point whereToShow,
                                             final JComponent component,
                                             @Nullable final Runnable showMoreOptions, Object breakpoint) {
    final BreakpointEditor editor = new BreakpointEditor();
    editor.setPropertiesPanel(mainPanel);
    editor.setShowMoreOptionsLink(true);

    final JPanel panel = editor.getMainPanel();

    BalloonBuilder builder = JBPopupFactory.getInstance()
      .createDialogBalloonBuilder(panel, null)
      .setHideOnClickOutside(true)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(0)
      .setBlockClicksThroughBalloon(true);

    Color borderColor = UIManager.getColor("DebuggerPopup.borderColor");
    if (borderColor != null ) {
      builder.setBorderColor(borderColor);
    }

    Balloon balloon = builder.createBalloon();

    editor.setDelegate(new BreakpointEditor.Delegate() {
      @Override
      public void done() {
        balloon.hide();
      }

      @Override
      public void more() {
        assert showMoreOptions != null;
        balloon.hide();
        showMoreOptions.run();
      }
    });

    ComponentAdapter moveListener = new ComponentAdapter() {
      @Override
      public void componentMoved(ComponentEvent e) {
        balloon.hide();
      }
    };
    component.addComponentListener(moveListener);
    Disposer.register(balloon, () -> component.removeComponentListener(moveListener));

    HierarchyBoundsListener hierarchyBoundsListener = new HierarchyBoundsAdapter() {
      @Override
      public void ancestorMoved(HierarchyEvent e) {
        balloon.hide();
      }
    };
    component.addHierarchyBoundsListener(hierarchyBoundsListener);
    Disposer.register(balloon, () -> component.removeHierarchyBoundsListener(hierarchyBoundsListener));

    if (whereToShow == null) {
      balloon.showInCenterOf(component);
    }
    else {
      //todo[kb] modify and move to BalloonImpl?
      final Window window = SwingUtilities.windowForComponent(component);
      final RelativePoint p = new RelativePoint(component, whereToShow);
      if (window != null) {
        final RelativePoint point = new RelativePoint(window, new Point(0, 0));
        if (p.getScreenPoint().getX() - point.getScreenPoint().getX() < 40) { // triangle + offsets is ~40px
          p.getPoint().x += 40;
        }
      }
      balloon.show(p, Balloon.Position.below);
    }

    BreakpointsDialogFactory.getInstance(project).setBalloonToHide(balloon, breakpoint);

    return balloon;
  }

  @NotNull
  public static EditorColorsScheme getColorScheme() {
    return EditorColorsUtil.getGlobalOrDefaultColorScheme();
  }

  @NotNull
  public static EditorColorsScheme getColorScheme(@Nullable JComponent component) {
    return EditorColorsUtil.getColorSchemeForComponent(component);
  }

  private static class FullValueEvaluationCallbackImpl implements XFullValueEvaluator.XFullValueEvaluationCallback {
    private final AtomicBoolean myObsolete = new AtomicBoolean(false);
    private final EditorTextField myTextArea;

    FullValueEvaluationCallbackImpl(final EditorTextField textArea) {
      myTextArea = textArea;
    }

    @Override
    public void evaluated(@NotNull final String fullValue) {
      evaluated(fullValue, null);
    }

    @Override
    public void evaluated(@NotNull final String fullValue, @Nullable final Font font) {
      AppUIUtil.invokeOnEdt(() -> {
        myTextArea.setText(fullValue);
        if (font != null) {
          myTextArea.setFont(font);
        }
      });
    }

    @Override
    public void errorOccurred(@NotNull final String errorMessage) {
      AppUIUtil.invokeOnEdt(() -> {
        myTextArea.setForeground(XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES.getFgColor());
        myTextArea.setText(errorMessage);
      });
    }

    private void setObsolete() {
      myObsolete.set(true);
    }

    @Override
    public boolean isObsolete() {
      return myObsolete.get();
    }
  }

  @Nullable
  public static String getNodeRawValue(@NotNull XValueNodeImpl valueNode) {
    String res = null;
    if (valueNode.getValueContainer() instanceof XValueTextProvider) {
      res = ((XValueTextProvider)valueNode.getValueContainer()).getValueText();
    }
    if (res == null) {
      res = valueNode.getRawValue();
    }
    return res;
  }

  /**
   * @deprecated avoid, {@link XValue#calculateEvaluationExpression()} may produce side effects
   */
  @Deprecated
  public static boolean hasEvaluationExpression(@NotNull XValue value) {
    Promise<XExpression> promise = value.calculateEvaluationExpression();
    try {
      return promise.getState() == Promise.State.PENDING || promise.blockingGet(0) != null;
    }
    catch (ExecutionException | TimeoutException e) {
      return true;
    }
  }

  public static void addToWatches(@NotNull XWatchesView watchesView, @NotNull XValueNodeImpl node) {
    node.calculateEvaluationExpression().onSuccess(expression -> {
      if (expression != null) {
        invokeLater(() -> watchesView.addWatchExpression(expression, -1, false));
      }
    });
  }

  public static void registerActionOnComponent(String name, JComponent component, Disposable parentDisposable) {
    AnAction action = ActionManager.getInstance().getAction(name);
    action.registerCustomShortcutSet(action.getShortcutSet(), component, parentDisposable);
  }

  public static void registerExtraHandleShortcuts(ListPopupImpl popup, Ref<Boolean> showAd, String... actionNames) {
    for (String name : actionNames) {
      KeyStroke stroke = KeymapUtil.getKeyStroke(ActionManager.getInstance().getAction(name).getShortcutSet());
      if (stroke != null) {
        popup.registerAction("handleSelection " + stroke, stroke, new AbstractAction() {
          @Override
          public void actionPerformed(ActionEvent e) {
            showAd.set(false);
            popup.handleSelect(true);
          }
        });
      }
    }
    if (showAd.get()) {
      popup.setAdText(getSelectionShortcutsAdText(actionNames));
    }
  }

  @NotNull
  public static @NlsContexts.PopupAdvertisement String getSelectionShortcutsAdText(String... actionNames) {
    String text = StreamEx.of(actionNames).map(DebuggerUIUtil::getActionShortcutText).nonNull().collect(NlsMessages.joiningOr());
    return StringUtil.isEmpty(text) ? "" : XDebuggerBundle.message("ad.extra.selection.shortcut", text);
  }

  @Nullable
  public static String getActionShortcutText(String actionName) {
    KeyStroke stroke = KeymapUtil.getKeyStroke(ActionManager.getInstance().getAction(actionName).getShortcutSet());
    if (stroke != null) {
      return KeymapUtil.getKeystrokeText(stroke);
    }
    return null;
  }

  public static boolean isObsolete(Object object) {
    return object instanceof Obsolescent && ((Obsolescent)object).isObsolete();
  }

  public static void setTreeNodeValue(XValueNodeImpl valueNode, XExpression text, Consumer<? super String> errorConsumer) {
    XDebuggerTree tree = valueNode.getTree();
    Project project = tree.getProject();
    XValueModifier modifier = valueNode.getValueContainer().getModifier();
    if (modifier == null) return;
    XDebuggerTreeState treeState = XDebuggerTreeState.saveState(tree);
    valueNode.setValueModificationStarted();
    modifier.setValue(text, new XValueModifier.XModificationCallback() {
      @Override
      public void valueModified() {
        AppUIUtil.invokeOnEdt(() -> {
          if (tree.isDetached()) {
            tree.rebuildAndRestore(treeState);
          }
        });
        XDebuggerUtilImpl.rebuildAllSessionsViews(project);
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        AppUIUtil.invokeOnEdt(() -> {
          tree.rebuildAndRestore(treeState);
          errorConsumer.consume(errorMessage);
        });
        XDebuggerUtilImpl.rebuildAllSessionsViews(project);
      }
    });
  }

  public static boolean isInDetachedTree(AnActionEvent event) {
    return event.getData(XDebugSessionTab.TAB_KEY) == null;
  }

  @Nullable
  public static XDebugSessionData getSessionData(AnActionEvent e) {
    XDebugSessionData data = e.getData(XDebugSessionData.DATA_KEY);
    if (data == null) {
      XDebugSession session = getSession(e);
      if (session != null) {
        data = ((XDebugSessionImpl)session).getSessionData();
      }
    }
    return data;
  }

  @Nullable
  public static XDebugSession getSession(@NotNull AnActionEvent e) {
    XDebugSession session = e.getData(XDebugSession.DATA_KEY);
    if (session == null) {
      Project project = e.getProject();
      if (project != null) {
        session = XDebuggerManager.getInstance(project).getCurrentSession();
      }
    }
    return session;
  }


  public static void repaintCurrentEditor(Project project) {
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor != null) {
      editor.getContentComponent().revalidate();
      editor.getContentComponent().repaint();
    }
  }


  @ApiStatus.Experimental
  public static @NotNull JPanel createCustomToolbarComponent(@NotNull AnAction action, @NotNull AnActionLink actionLink) {
    JPanel actionPanel = new JPanel(new GridBagLayout());

    GridBag gridBag = new GridBag().fillCellHorizontally().anchor(GridBagConstraints.WEST);
    int topInset = 5;
    int bottomInset = 4;

    actionPanel.add(actionLink, gridBag.next().insets(topInset, 10, bottomInset, 4));

    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    String shortcutsText = KeymapUtil.getShortcutsText(shortcuts);
    if (!shortcutsText.isEmpty()) {
      JComponent keymapHint = createKeymapHint(shortcutsText);
      actionPanel.add(keymapHint, gridBag.next().insets(topInset, 4, bottomInset, 12));
    }

    actionPanel.setBackground(UIUtil.getToolTipActionBackground());
    return actionPanel;
  }

  private static JComponent createKeymapHint(@NlsContexts.Label String shortcutRunAction) {
    JBLabel shortcutHint = new JBLabel(shortcutRunAction) {
      @Override
      public Color getForeground() {
        return getKeymapColor();
      }
    };
    shortcutHint.setBorder(JBUI.Borders.empty());
    shortcutHint.setFont(UIUtil.getToolTipFont());
    return shortcutHint;
  }

  private static Color getKeymapColor() {
    return JBColor.namedColor("ToolTip.Actions.infoForeground", new JBColor(0x99a4ad, 0x919191));
  }

  @ApiStatus.Experimental
  public static JPanel getSecretComponentForToolbar() {
    JPanel secretPanel = new JPanel();
    secretPanel.setPreferredSize(new Dimension(0, 27));
    return secretPanel;
  }
}