// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.AnActionLink;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

@ApiStatus.Experimental
public class XDebuggerTextPopup<D> {

  public static final String ACTION_PLACE = "XDebuggerTextPopup";

  protected final @Nullable XFullValueEvaluator myEvaluator;
  protected final @NotNull DebuggerTreeCreator<D> myTreeCreator;
  protected final @NotNull D myInitialItem;
  protected final @NotNull Project myProject;
  protected final @NotNull Editor myEditor;
  protected final @NotNull Point myPoint;
  protected final @Nullable Runnable myHideRunnable;
  protected final @NotNull TextViewer myTextViewer;
  protected final @NotNull ActionToolbarImpl myToolbar;

  protected @Nullable JBPopup myPopup;
  protected boolean myTreePopupIsShown = false;
  protected @Nullable Tree myTree;
  private boolean myTextViewerContentChanged = false;

  public XDebuggerTextPopup(@Nullable XFullValueEvaluator evaluator,
                            @NotNull DebuggerTreeCreator<D> creator,
                            @NotNull D initialItem,
                            @NotNull Editor editor,
                            @NotNull Point point,
                            @NotNull Project project,
                            @Nullable Runnable hideRunnable) {
    myEvaluator = evaluator;
    myTreeCreator = creator;
    myInitialItem = initialItem;
    myEditor = editor;
    myPoint = point;
    myProject = project;
    myHideRunnable = hideRunnable;
    myToolbar = createToolbar();
    myTextViewer = DebuggerUIUtil.createTextViewer(XDebuggerUIConstants.getEvaluatingExpressionMessage(), myProject);
  }

  private void makeTextViewerEditable() {
    myTextViewer.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        myTextViewerContentChanged = event.getNewLength() + event.getOldLength() > 0;
        myToolbar.updateActionsImmediately();
      }
    });
    myTextViewer.setViewer(false);
  }

  public void show(@NotNull String initialText) {
    XFullValueEvaluator evaluator = myEvaluator != null ? myEvaluator : new ImmediateFullValueEvaluator(initialText);

    Runnable hideTextPopupRunnable = myHideRunnable == null ? null : () -> {
      if (!myTreePopupIsShown) {
        myHideRunnable.run();
      }
    };

    Runnable afterEvaluation = () -> {
      makeTextViewerEditable();
    };

    myPopup = DebuggerUIUtil.createTextViewerPopup(myTextViewer, evaluator, afterEvaluation, myProject, hideTextPopupRunnable,
                                                   myToolbar);

    myTree = myTreeCreator.createTree(myInitialItem);
    registerTreeDisposable(myPopup, myTree);

    WindowMoveListener moveListener = new WindowMoveListener(myPopup.getContent());
    myToolbar.addMouseListener(moveListener);
    myToolbar.addMouseMotionListener(moveListener);

    myPopup.show(new RelativePoint(myEditor.getContentComponent(), myPoint));
  }

  protected void hideTextPopup() {
    if (myPopup != null) {
      myPopup.cancel();
    }
  }

  protected static void registerTreeDisposable(Disposable disposable, Tree tree) {
    if (tree instanceof Disposable) {
      Disposer.register(disposable, (Disposable)tree);
    }
  }

  protected DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.add(new ShowTreeAction());
    toolbarActions.add(new SetTextValueAction());
    toolbarActions.addSeparator();
    return toolbarActions;
  }

  protected DefaultActionGroup wrapActions(DefaultActionGroup toolbarActions) {
    DefaultActionGroup wrappedActions = new DefaultActionGroup();
    for (AnAction action : toolbarActions.getChildren(null)) {
      ActionWrapper actionLink = new ActionWrapper(action);
      wrappedActions.add(actionLink);
    }

    return wrappedActions;
  }

  private ActionToolbarImpl createToolbar() {
    DefaultActionGroup wrappedActions = wrapActions(getToolbarActions());

    var toolbarImpl = new ActionToolbarImpl(ACTION_PLACE, wrappedActions, true);
    toolbarImpl.setTargetComponent(null);
    toolbarImpl.setBorder(BorderFactory.createEmptyBorder());
    toolbarImpl.setBackground(UIUtil.getToolTipActionBackground());

    return toolbarImpl;
  }

  @Nullable
  protected XValueNodeImpl getNode() {
    if (myTree instanceof XDebuggerTree) {
      XDebuggerTreeNode node = ((XDebuggerTree)myTree).getRoot();
      return node instanceof XValueNodeImpl ? (XValueNodeImpl)node : null;
    }
    return null;
  }

  protected void showTreePopup(Runnable hideTreeRunnable) {
    new XDebuggerTreePopup<>(myTreeCreator, myEditor, myPoint, myProject, hideTreeRunnable).show(myInitialItem);
  }

  private class ShowTreeAction extends AnAction {
    private ShowTreeAction() {
      super(ActionsBundle.message("action.Debugger.XDebuggerTextPopup.ShowTree.text"));
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
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      @Nullable XValueNodeImpl node = getNode();

      Presentation presentation = e.getPresentation();
      presentation.setVisible(node != null && canSetTextValue(node));
      presentation.setEnabled(myTextViewerContentChanged);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      @Nullable XValueNodeImpl node = getNode();

      if (node != null && canSetTextValue(node)) {
        setTextValue(node, myTextViewer.getText());
        myTextViewerContentChanged = false;
        myToolbar.updateActionsImmediately();
      }
    }

    private boolean canSetTextValue(@NotNull XValueNodeImpl node) {
      @NotNull XValue value = node.getValueContainer();
      return value instanceof XValueTextProvider &&
             ((XValueTextProvider)value).shouldShowTextValue() &&
             value.getModifier() instanceof XStringValueModifier;
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

  private static class ActionWrapper extends AnAction implements CustomComponentAction {

    private final @NotNull AnAction myDelegate;

    private ActionWrapper(@NotNull AnAction delegateAction) {
      super(delegateAction.getTemplateText());
      copyFrom(delegateAction);
      myDelegate = delegateAction;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myDelegate.update(e);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myDelegate.actionPerformed(e);
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      if (myDelegate instanceof Separator) {
        return DebuggerUIUtil.getSecretComponentForToolbar(); // this is necessary because the toolbar hide if all action panels are not visible
      }

      ActionLinkButton button = new ActionLinkButton(this, presentation, null);
      JPanel actionPanel = DebuggerUIUtil.createCustomToolbarComponent(this, button);

      presentation.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName() == Presentation.PROP_TEXT) {
            button.setText((String)evt.getNewValue());
            button.repaint();
          }
          if (evt.getPropertyName() == Presentation.PROP_ENABLED) {
            button.setEnabled((Boolean)evt.getNewValue());
            button.repaint();
          }
          if (evt.getPropertyName() == Presentation.PROP_VISIBLE) {
            actionPanel.setVisible((Boolean)evt.getNewValue());
            actionPanel.repaint();
          }
        }
      });

      return actionPanel;
    }
  }

  private static class ActionLinkButton extends AnActionLink {
    ActionLinkButton(@NotNull AnAction action,
                     @NotNull Presentation presentation,
                     @Nullable DataProvider contextComponent) {
      //noinspection ConstantConditions
      super(presentation.getText(), action);
      setAutoHideOnDisable(false);
      setVisible(presentation.isVisible());
      setEnabled(presentation.isEnabled());
      setDataProvider(contextComponent);
      setFont(UIUtil.getToolTipFont());
    }
  }
}