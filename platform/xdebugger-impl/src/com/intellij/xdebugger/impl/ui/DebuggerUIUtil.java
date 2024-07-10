// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.ui.AntiFlickeringPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.Consumer;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
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
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedTextPopup;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.wm.IdeFocusManager.getGlobalInstance;

public final class DebuggerUIUtil {
  @NonNls public static final String FULL_VALUE_POPUP_DIMENSION_KEY = "XDebugger.FullValuePopup";

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
      boolean isRemoteEditor = !ClientId.isLocal(ClientEditorManager.getClientId(editor));
      if (isRemoteEditor || editor.getScrollingModel().getVisibleArea().contains(p)) {
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
          Window window = popup.isDisposed() ? null : ComponentUtil.getWindow(popup.getContent());
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

  public static void showValuePopup(@NotNull XFullValueEvaluator evaluator,
                                    @NotNull MouseEvent event,
                                    @NotNull Project project,
                                    @Nullable Editor editor) {
    VisualizedTextPopup.INSTANCE.evaluateAndShowValuePopup(evaluator, event, project, editor);
  }

  @ApiStatus.Experimental
  public static TextViewer createTextViewer(@NotNull String initialText, @NotNull Project project) {
    return createTextViewer(initialText, project, FileTypes.PLAIN_TEXT);
  }

  @ApiStatus.Experimental
  public static TextViewer createTextViewer(@NotNull String initialText, @NotNull Project project, FileType fileType) {
    TextViewer textArea = new TextViewer(initialText, project, fileType);
    textArea.setBackground(HintUtil.getInformationColor());

    textArea.addSettingsProvider(e -> {
      e.getScrollPane().setBorder(JBUI.Borders.empty());
      e.getScrollPane().setViewportBorder(JBUI.Borders.empty());
    });

    return textArea;
  }

  @ApiStatus.Experimental
  public static ComponentPopupBuilder createTextViewerPopupBuilder(@NotNull JComponent popupContent,
                                                                   @NotNull TextViewer textViewer,
                                                                   @NotNull XFullValueEvaluator evaluator,
                                                                   @NotNull Project project,
                                                                   @Nullable Runnable afterFullValueEvaluation,
                                                                   @Nullable Runnable hideRunnable) {

    AtomicBoolean evaluationObsolete = new AtomicBoolean(false);
    var callback = new XFullValueEvaluator.XFullValueEvaluationCallback() {
      @Override
      public void evaluated(@NotNull final String fullValue, @Nullable final Font font) {
        AppUIUtil.invokeOnEdt(() -> {
          textViewer.setText(fullValue);
          if (font != null) {
            textViewer.setFont(font);
          }
          if (afterFullValueEvaluation != null) {
            afterFullValueEvaluation.run();
          }
        });
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        AppUIUtil.invokeOnEdt(() -> {
          textViewer.setForeground(XDebuggerUIConstants.ERROR_MESSAGE_ATTRIBUTES.getFgColor());
          textViewer.setText(errorMessage);
        });
      }

      @Override
      public boolean isObsolete() {
        return evaluationObsolete.get();
      }
    };

    Runnable cancelCallback = () -> {
      evaluationObsolete.set(true);
      if (hideRunnable != null) {
        hideRunnable.run();
      }
    };

    evaluator.startEvaluation(callback);
    return createCancelablePopupBuilder(project, popupContent, textViewer, cancelCallback, null);
  }

  public static JBPopup createValuePopup(Project project,
                                         JComponent component,
                                         @Nullable Runnable cancelCallback) {
    return createCancelablePopupBuilder(project, component, null, cancelCallback, FULL_VALUE_POPUP_DIMENSION_KEY).createPopup();
  }

  private static ComponentPopupBuilder createCancelablePopupBuilder(Project project,
                                                                    JComponent component,
                                                                    JComponent preferableFocusComponent,
                                                                    @Nullable Runnable cancelCallback,
                                                                    @Nullable String dimensionKey) {
    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, preferableFocusComponent);
    builder.setResizable(true)
      .setMovable(true)
      .setRequestFocus(true);
    if (dimensionKey != null) {
      builder.setDimensionServiceKey(project, dimensionKey, false);
    }
    if (cancelCallback != null) {
      builder.setCancelCallback(() -> {
        cancelCallback.run();
        return true;
      });
    }
    return builder;
  }

  public static void showXBreakpointEditorBalloon(final Project project,
                                                  @Nullable final Point point,
                                                  final JComponent component,
                                                  final boolean showAllOptions,
                                                  @NotNull final XBreakpoint breakpoint) {
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    final XLightBreakpointPropertiesPanel propertiesPanel =
      new XLightBreakpointPropertiesPanel(project, breakpointManager, (XBreakpointBase)breakpoint,
                                          showAllOptions, true);

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
    if (borderColor != null) {
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

  public static void addToWatches(@NotNull XWatchesView watchesView, @NotNull XValueNodeImpl node) {
    node.calculateEvaluationExpression().onSuccess(expression -> {
      if (expression != null) {
        invokeLater(() -> watchesView.addWatchExpression(expression, -1, false));
      }
    });
  }

  @Nullable
  public static XWatchesView getWatchesView(@NotNull AnActionEvent e) {
    XWatchesView view = e.getData(XWatchesView.DATA_KEY);
    Project project = e.getProject();
    if (view == null && project != null) {
      XDebugSession session = getSession(e);
      if (session != null) {
        XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
        if (tab != null) {
          return tab.getWatchesView();
        }
      }
    }
    return view;
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

  public static void setActionEnabled(AnActionEvent e, boolean enable) {
    String place = e.getPlace();
    if (ActionPlaces.isMainMenuOrActionSearch(place) || ActionPlaces.DEBUGGER_TOOLBAR.equals(place)) {
      e.getPresentation().setEnabled(enable);
    }
    else {
      e.getPresentation().setVisible(enable);
    }
  }

  public static boolean shouldUseAntiFlickeringPanel() {
    return !ApplicationManager.getApplication().isUnitTestMode() && Registry.intValue("debugger.anti.flickering.delay", 0) > 0;
  }

  public static boolean freezePaintingToReduceFlickering(@Nullable Component component) {
    if (component instanceof AntiFlickeringPanel antiFlickeringPanel) {
      int delay = Registry.intValue("debugger.anti.flickering.delay", 0);
      if (delay > 0) {
        ApplicationManager.getApplication().invokeAndWait(() -> antiFlickeringPanel.freezePainting(delay), ModalityState.any());
        return true;
      }
    }
    return false;
  }
}