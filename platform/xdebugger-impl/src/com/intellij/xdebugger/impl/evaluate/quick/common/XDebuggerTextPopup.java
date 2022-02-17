// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.TextViewer;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

@ApiStatus.Experimental
public class XDebuggerTextPopup<D> extends XDebuggerPopupPanel {

  public static final String ACTION_PLACE = "XDebuggerTextPopup";

  private static final int MAX_POPUP_WIDTH = 600;
  private static final int MAX_POPUP_HEIGHT = 300;
  private static final int MIN_POPUP_WIDTH = 170;
  private static final int MIN_POPUP_HEIGHT = 100;
  private static final int TOOLBAR_MARGIN = 30;

  protected final @Nullable XFullValueEvaluator myEvaluator;
  protected final @NotNull DebuggerTreeCreator<D> myTreeCreator;
  protected final @NotNull D myInitialItem;
  protected final @NotNull Project myProject;
  protected final @NotNull Editor myEditor;
  protected final @NotNull Point myPoint;
  protected final @Nullable Runnable myHideRunnable;
  protected final @NotNull TextViewer myTextViewer;

  protected @Nullable JBPopup myPopup;
  protected boolean myTreePopupIsShown = false;
  protected @Nullable Tree myTree;
  private boolean mySetValueModeEnabled = false;
  private String myCachedText = "";

  @ApiStatus.Experimental
  public XDebuggerTextPopup(@Nullable XFullValueEvaluator evaluator,
                            @NotNull DebuggerTreeCreator<D> creator,
                            @NotNull D initialItem,
                            @NotNull Editor editor,
                            @NotNull Point point,
                            @NotNull Project project,
                            @Nullable Runnable hideRunnable) {
    super(ACTION_PLACE);
    myEvaluator = evaluator;
    myTreeCreator = creator;
    myInitialItem = initialItem;
    myEditor = editor;
    myPoint = point;
    myProject = project;
    myHideRunnable = hideRunnable;
    myTextViewer = DebuggerUIUtil.createTextViewer(XDebuggerUIConstants.getEvaluatingExpressionMessage(), myProject);
  }

  private void setTextViewerSize() {
    JFrame frame = WindowManager.getInstance().getFrame(myProject);
    int preferredHeight = frame != null ? frame.getSize().height / 4 : MIN_POPUP_HEIGHT;
    int preferredWidth = frame != null ? frame.getSize().width / 4 : MIN_POPUP_WIDTH;
    myTextViewer.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
  }

  private JBPopup createPopup(XFullValueEvaluator evaluator, Runnable afterEvaluation, Runnable hideTextPopupRunnable) {
    ComponentPopupBuilder builder =
      DebuggerUIUtil.createTextViewerPopupBuilder(myContent, myTextViewer, evaluator, myProject, afterEvaluation, hideTextPopupRunnable);

    return builder.setCancelKeyEnabled(false)
      .setRequestFocus(true)
      .setKeyEventHandler(event -> {
        if (mySetValueModeEnabled) {
          return false;
        }
        if (AbstractPopup.isCloseRequest(event)) {
          if (myPopup != null) {
            myPopup.cancel();
            return true;
          }
        }
        return false;
      }).createPopup();
  }

  public void show(@NotNull String initialText) {
    setTextViewerSize();
    setContent(myTextViewer, null);

    XFullValueEvaluator evaluator = myEvaluator != null ? myEvaluator : new ImmediateFullValueEvaluator(initialText);

    Runnable hideTextPopupRunnable = myHideRunnable == null ? null : () -> {
      if (!myTreePopupIsShown) {
        myHideRunnable.run();
      }
    };

    Runnable afterEvaluation = () -> {
      resizePopup();
    };

    myPopup = createPopup(evaluator, afterEvaluation, hideTextPopupRunnable);

    myTree = myTreeCreator.createTree(myInitialItem);
    registerTreeDisposable(myPopup, myTree);

    setAutoResizeUntilToolbarNotFull(() -> resizePopup(), myPopup);

    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));
  }

  private void resizePopup() {
    if (myPopup == null || !myPopup.isVisible() || myPopup.isDisposed()) return;

    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    if (popupWindow == null) {
      return;
    }

    JFrame frame = WindowManager.getInstance().getFrame(myProject);

    int metrics = myTextViewer.getFontMetrics(myTextViewer.getFont()).stringWidth(myTextViewer.getText());
    Dimension preferredSize = metrics > 250 ?
                              getMediumPopupSize(frame, myToolbar, metrics) : getMiniPopupSize(frame, myToolbar);

    int newWidth = Math.min(preferredSize.width, getMaxPopupWidth(frame));
    int newHeight = Math.min(preferredSize.height, getMaxPopupHeight(frame));
    newWidth = Math.max(newWidth, MIN_POPUP_WIDTH);
    newHeight = Math.max(newHeight, MIN_POPUP_HEIGHT);

    final Point location = popupWindow.getLocation();
    final Rectangle targetBounds = new Rectangle(location.x, location.y, newWidth, newHeight);

    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    if (targetBounds.width != popupWindow.getWidth() || targetBounds.height != popupWindow.getHeight()) {
      popupWindow.setBounds(targetBounds);
      popupWindow.validate();
      popupWindow.repaint();
    }
  }

  protected void hideTextPopup() {
    if (myPopup != null) {
      myPopup.cancel();
    }
  }

  @Override
  protected @NotNull DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = super.getToolbarActions();
    toolbarActions.add(new ShowAsObject());
    toolbarActions.add(new EnableSetValueMode());
    toolbarActions.add(new SetTextValueAction());
    toolbarActions.add(new DisableSetValueMode());
    return toolbarActions;
  }

  @Nullable
  private XValueNodeImpl getValueNode() {
    if (myTree instanceof XDebuggerTree) {
      XDebuggerTreeNode node = ((XDebuggerTree)myTree).getRoot();
      return node instanceof XValueNodeImpl ? (XValueNodeImpl)node : null;
    }
    return null;
  }

  protected void showTreePopup(Runnable hideTreeRunnable) {
    new XDebuggerTreePopup<>(myTreeCreator, myEditor, myPoint, myProject, hideTreeRunnable).show(myInitialItem);
  }

  private void enableSetValueMode() {
    myCachedText = myTextViewer.getText();
    myTextViewer.setViewer(false);
    myTextViewer.selectAll();
    mySetValueModeEnabled = true;
    myToolbar.updateActionsImmediately();
  }

  private void disableSetValueMode() {
    myCachedText = "";
    myTextViewer.setViewer(true);
    myTextViewer.removeSelection();
    mySetValueModeEnabled = false;
    myToolbar.updateActionsImmediately();
  }

  @Override
  protected boolean shouldBeVisible(AnAction action) {
    boolean isSetValueModeAction = action instanceof XDebuggerTextPopup.SetTextValueAction ||
                                   action instanceof XDebuggerTextPopup.DisableSetValueMode;
    return isSetValueModeAction && mySetValueModeEnabled || !isSetValueModeAction && !mySetValueModeEnabled;
  }

  private static int getMaxPopupWidth(JFrame frame) {
    return frame != null ? frame.getSize().width / 3 : MAX_POPUP_WIDTH;
  }

  private static int getMaxPopupHeight(JFrame frame) {
    return frame != null ? frame.getSize().height / 3 : MAX_POPUP_HEIGHT;
  }

  private static Dimension getMiniPopupSize(JFrame frame, ActionToolbarImpl toolbar) {
    int preferredHeight = frame != null ? frame.getSize().height / 7 : MIN_POPUP_HEIGHT;
    int preferredWidth = toolbar.getPreferredSize().width + TOOLBAR_MARGIN;
    return new Dimension(preferredWidth, preferredHeight);
  }

  private static Dimension getMediumPopupSize(JFrame frame, ActionToolbarImpl toolbar, int metrics) {
    int preferredHeight = frame != null ? frame.getSize().height / 4 : MAX_POPUP_HEIGHT;
    int preferredWidth = Math.max(toolbar.getPreferredSize().width + TOOLBAR_MARGIN, metrics);
    return new Dimension(preferredWidth, preferredHeight);
  }

  private static boolean canSetTextValue(@NotNull XValueNodeImpl node) {
    @NotNull XValue value = node.getValueContainer();
    return value instanceof XValueTextProvider &&
           ((XValueTextProvider)value).shouldShowTextValue() &&
           value.getModifier() instanceof XStringValueModifier;
  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }

  private class ShowAsObject extends AnAction {
    private ShowAsObject() {
      super(ActionsBundle.message("action.Debugger.XDebuggerTextPopup.ShowAsObject.text"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Runnable hideTreeRunnable = myHideRunnable == null ? null : () -> {
        myTreePopupIsShown = false;
        myHideRunnable.run();
      };
      showTreePopup(hideTreeRunnable);
      myTreePopupIsShown = true;
      hideTextPopup();
    }
  }

  private class SetTextValueAction extends AnAction {

    private SetTextValueAction() {
      super(XDebuggerBundle.message("xdebugger.set.text.value.action.title"));
      setShortcutSet(CommonShortcuts.CTRL_ENTER);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      @Nullable XValueNodeImpl node = getValueNode();
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(node != null && canSetTextValue(node));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      @Nullable XValueNodeImpl node = getValueNode();

      if (node != null && canSetTextValue(node)) {
        setTextValue(node, myTextViewer.getText());
        disableSetValueMode();
      }
    }

    private void setTextValue(@NotNull XValueNodeImpl node, @NotNull String text) {
      @NotNull XValue value = node.getValueContainer();
      @Nullable XValueModifier modifier = value.getModifier();
      if (modifier instanceof XStringValueModifier) {
        XExpression expression = ((XStringValueModifier)modifier).stringToXExpression(text);
        Consumer<? super String> errorConsumer =
          (@NlsContexts.DialogMessage String errorMessage) -> Messages.showErrorDialog(node.getTree(), errorMessage);
        DebuggerUIUtil.setTreeNodeValue(node, expression, errorConsumer);
      }
    }
  }

  private class EnableSetValueMode extends AnAction {
    private EnableSetValueMode() {
      super(XDebuggerBundle.message("xdebugger.enable.set.action.title"));
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      @Nullable XValueNodeImpl node = getValueNode();
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(node != null && canSetTextValue(node));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      enableSetValueMode();
    }
  }

  private class DisableSetValueMode extends AnAction {

    private DisableSetValueMode() {
      super(XDebuggerBundle.message("xdebugger.cancel.set.action.title"));
      setShortcutSet(CommonShortcuts.ESCAPE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myTextViewer.setText(myCachedText);
      disableSetValueMode();
    }
  }
}