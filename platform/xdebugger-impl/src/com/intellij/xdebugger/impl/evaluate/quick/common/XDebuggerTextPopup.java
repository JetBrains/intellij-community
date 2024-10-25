// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedTextPopupUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Experimental
public class XDebuggerTextPopup<D> extends XDebuggerPopupPanel {

  public static final String ACTION_PLACE = "XDebuggerTextPopup";

  private static final int MAX_POPUP_WIDTH = 650;
  private static final int MAX_POPUP_HEIGHT = 400;
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

  private VisualizedTextPopupUtil.VisualizedTextPanel myTextPanel;
  private @Nullable JBPopup myPopup;
  protected boolean myTreePopupIsShown = false;
  protected @Nullable Tree myTree;
  private boolean mySetValueModeEnabled = false;

  @ApiStatus.Experimental
  public XDebuggerTextPopup(@Nullable XFullValueEvaluator evaluator,
                            @NotNull XValue value,
                            @NotNull DebuggerTreeCreator<D> creator,
                            @NotNull D initialItem,
                            @NotNull Editor editor,
                            @NotNull Point point,
                            @NotNull Project project,
                            @Nullable Runnable hideRunnable) {
    super();
    myTreeCreator = creator;
    myInitialItem = initialItem;
    myEditor = editor;
    myPoint = point;
    myProject = project;
    myHideRunnable = hideRunnable;

    if (evaluator == null && value instanceof XValueTextProvider) {
      evaluator = new XFullValueEvaluator() {
        @Override
        public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
          value.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
            @Override
            public void applyPresentation(@Nullable Icon icon, @NotNull XValuePresentation valuePresenter, boolean hasChildren) {
              callback.evaluated(StringUtil.notNullize(((XValueTextProvider)value).getValueText()));
            }

            @Override
            public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
              // do nothing - should already be set
            }
          }, XValuePlace.TOOLTIP);
        }
      };
    }
    myEvaluator = evaluator;
  }

  private JBPopup createPopup(XFullValueEvaluator evaluator, Runnable afterEvaluation, Runnable hideTextPopupRunnable) {

    AtomicBoolean evaluationObsolete = new AtomicBoolean(false);
    var callback = new XFullValueEvaluator.XFullValueEvaluationCallback() {

      private final AtomicReference<Integer> lastFullValueHashCode = new AtomicReference<>();

      private boolean preventDoubleExecution(@NotNull String fullValue) {
        // This code is not expected to be called multiple times (e.g., statistics are expected to be collected only once),
        // but it is actually called in the case of huge Java string.
        // 1. NodeDescriptorImpl.updateRepresentation() calls ValueDescriptorImpl.calcRepresentation() and it calls labelChanged()
        // 2. NodeDescriptorImpl.updateRepresentation() also directly calls labelChanged()
        // Double visualization spoils statistics and wastes the resources.
        // Try to prevent it by a simple hash code check.
        Integer hashCode = fullValue.hashCode();
        if (hashCode.equals(lastFullValueHashCode.get())) {
          return true;
        }
        lastFullValueHashCode.set(hashCode);
        return false;
      }

      @Override
      public void evaluated(@NotNull final String fullValue, @Nullable final Font font) {
        if (preventDoubleExecution(fullValue)) return;

        AppUIUtil.invokeOnEdt(() -> {
          myTextPanel.showVisualizedText(fullValue);
          if (afterEvaluation != null) {
            afterEvaluation.run();
          }
        });
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        AppUIUtil.invokeOnEdt(() -> {
          myTextPanel.showError(errorMessage);
        });
      }

      @Override
      public boolean isObsolete() {
        return evaluationObsolete.get();
      }
    };

    Runnable cancelCallback = () -> {
      evaluationObsolete.set(true);
      if (hideTextPopupRunnable != null) {
        hideTextPopupRunnable.run();
      }
    };

    evaluator.startEvaluation(callback);
    ComponentPopupBuilder builder =
      DebuggerUIUtil.createCancelablePopupBuilder(myProject, myContent, myTextPanel, cancelCallback, null);

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

  public JBPopup show(@NotNull String initialText) {
    myTextPanel = new VisualizedTextPopupUtil.VisualizedTextPanel(myProject);
    myMainPanel.setBorder(JBUI.Borders.empty());
    myMainPanel.setBackground(myTextPanel.getBackground());
    setContent(myTextPanel, getToolbarActions(), ACTION_PLACE, null);

    XFullValueEvaluator evaluator = myEvaluator != null ? myEvaluator : new ImmediateFullValueEvaluator(initialText);

    Runnable hideTextPopupRunnable = myHideRunnable == null ? null : () -> {
      if (!myTreePopupIsShown) {
        myHideRunnable.run();
      }
    };

    myPopup = createPopup(evaluator, this::resizePopup, hideTextPopupRunnable);
    Disposer.register(myPopup, myTextPanel);

    myTree = myTreeCreator.createTree(myInitialItem);
    registerTreeDisposable(myPopup, myTree);

    setAutoResizeUntilToolbarNotFull(this::resizePopup, myPopup);

    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));
    return myPopup;
  }

  private void resizePopup() {
    if (myPopup == null || !myPopup.isVisible() || myPopup.isDisposed()) {
      return;
    }
    final Window popupWindow = SwingUtilities.windowForComponent(myPopup.getContent());
    if (popupWindow == null) {
      return;
    }

    int textWidth = myTextPanel.getPreferredSize().width;
    int toolbarWidth = myToolbar.getPreferredSize().width;
    int newWidth = Math.max(toolbarWidth, textWidth) + TOOLBAR_MARGIN;

    int textHeight = myTextPanel.getPreferredSize().height;
    int toolbarHeight = myToolbar.getPreferredSize().height;
    int newHeight = textHeight + toolbarHeight;

    Rectangle screenRectangle = ScreenUtil.getScreenRectangle(myToolbar);
    newWidth = Math.min(newWidth, getMaxPopupWidth(screenRectangle));
    newWidth = Math.max(newWidth, getMinPopupWidth(screenRectangle));
    newHeight = Math.min(newHeight, getMaxPopupHeight(screenRectangle));
    newHeight = Math.max(newHeight, getMinPopupHeight(screenRectangle));
    updatePopupBounds(popupWindow, newWidth, newHeight);
  }

  protected void hideTextPopup() {
    if (myPopup != null) {
      myPopup.cancel();
    }
  }

  protected @NotNull DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new ShowAsObject());
    toolbarActions.add(new EnableSetValueMode());
    toolbarActions.add(new SetTextValueAction());
    toolbarActions.add(new CancelSetValue());
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


  private void tryEnableSetValueMode() {
    if (myTextPanel.tryEditText()) {
      mySetValueModeEnabled = true;
      myToolbar.updateActionsImmediately();
    } // it may fail to start editing if huge string value wasn't yet evaluated and shown but user already clicks "Set Value"
  }

  private String disableSetValueMode(boolean saveChanges) {
    String newText = myTextPanel.finishEdit(saveChanges);
    mySetValueModeEnabled = false;
    myToolbar.updateActionsImmediately();
    return newText;
  }

  @Override
  protected boolean shouldBeVisible(AnAction action) {
    boolean isSetValueModeAction = action instanceof XDebuggerTextPopup.SetTextValueAction ||
                                   action instanceof XDebuggerTextPopup.CancelSetValue;
    return isSetValueModeAction && mySetValueModeEnabled || !isSetValueModeAction && !mySetValueModeEnabled;
  }

  private static int getMaxPopupWidth(Rectangle screenRectangle) {
    return Math.min(screenRectangle.width / 2, MAX_POPUP_WIDTH);
  }

  private static int getMaxPopupHeight(Rectangle screenRectangle) {
    return Math.min(screenRectangle.height / 2, MAX_POPUP_HEIGHT);
  }

  private static int getMinPopupWidth(Rectangle screenRectangle) {
    return Math.max(screenRectangle.width / 5, MIN_POPUP_WIDTH);
  }

  private static int getMinPopupHeight(Rectangle screenRectangle) {
    return Math.max(screenRectangle.height / 7, MIN_POPUP_HEIGHT);
  }

  private static boolean canSetTextValue(@Nullable XValueNodeImpl node) {
    return getTextValueModifier(node) != null;
  }

  private static @Nullable XStringValueModifier getTextValueModifier(@Nullable XValueNodeImpl node) {
    if (node == null) return null;

    XValue value = node.getValueContainer();
    return value instanceof XValueTextProvider textProvider &&
           textProvider.shouldShowTextValue() &&
           value.getModifier() instanceof XStringValueModifier modifier
           ? modifier
           : null;
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
      setShortcutSet(CommonShortcuts.getCtrlEnter());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(canSetTextValue(getValueNode()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      @Nullable XValueNodeImpl node = getValueNode();
      var modifier = getTextValueModifier(node);
      if (modifier != null) {
        String newValue = disableSetValueMode(true);
        setTextValue(node, modifier, newValue);
      }
      else {
        // No need to call disableSetValueMode(), popup will be hidden on error.
        onErrorSettingValue("Unexpectedly cannot set text value for this edited value");
      }
    }

    private void setTextValue(@NotNull XValueNodeImpl node, @NotNull XStringValueModifier modifier, @NotNull String text) {
      XExpression expression = modifier.stringToXExpression(text);
      DebuggerUIUtil.setTreeNodeValue(node, expression, this::onErrorSettingValue);
    }

    private void onErrorSettingValue(String errorMessage) {
      hideTextPopup();
      //noinspection HardCodedStringLiteral
      @NlsContexts.DialogMessage String message = errorMessage;
      Messages.showErrorDialog(myEditor.getComponent(), message, XDebuggerBundle.message("xdebugger.set.text.value.error.title"));
    }
  }

  private class EnableSetValueMode extends AnAction {
    private EnableSetValueMode() {
      super(XDebuggerBundle.message("xdebugger.enable.set.action.title"));
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(canSetTextValue(getValueNode()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      tryEnableSetValueMode();
    }
  }

  private class CancelSetValue extends AnAction {

    private CancelSetValue() {
      super(XDebuggerBundle.message("xdebugger.cancel.set.action.title"));
      setShortcutSet(CommonShortcuts.ESCAPE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      disableSetValueMode(false);
    }
  }
}