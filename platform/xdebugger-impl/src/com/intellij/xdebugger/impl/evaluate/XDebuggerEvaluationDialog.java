/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel;
import com.intellij.xdebugger.impl.ui.tree.nodes.EvaluatingExpressionRootNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author nik
 */
public class XDebuggerEvaluationDialog extends DialogWrapper {
  public static final DataKey<XDebuggerEvaluationDialog> KEY = DataKey.create("DEBUGGER_EVALUATION_DIALOG");

  private final JPanel myMainPanel;
  private final JPanel myResultPanel;
  private final XDebuggerTreePanel myTreePanel;
  private EvaluationInputComponent myInputComponent;
  private final XDebugSession mySession;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private EvaluationMode myMode;
  private XSourcePosition mySourcePosition;
  private final SwitchModeAction mySwitchModeAction;
  private final boolean myIsCodeFragmentEvaluationSupported;

  public XDebuggerEvaluationDialog(@NotNull XDebugSession session,
                                   @NotNull XDebuggerEditorsProvider editorsProvider,
                                   @NotNull XDebuggerEvaluator evaluator,
                                   @NotNull XExpression text,
                                   @Nullable XSourcePosition sourcePosition) {
    super(WindowManager.getInstance().getFrame(session.getProject()), true);
    mySession = session;
    myEditorsProvider = editorsProvider;
    mySourcePosition = sourcePosition;
    setModal(false);
    setOKButtonText(XDebuggerBundle.message("xdebugger.button.evaluate"));
    setCancelButtonText(XDebuggerBundle.message("xdebugger.evaluate.dialog.close"));

    mySession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        ApplicationManager.getApplication().invokeLater(() -> close(CANCEL_EXIT_CODE));
      }

      @Override
      public void stackFrameChanged() {
        updateSourcePosition();
      }

      @Override
      public void sessionPaused() {
        updateSourcePosition();
      }
    }, myDisposable);

    myTreePanel = new XDebuggerTreePanel(session.getProject(), editorsProvider, myDisposable, sourcePosition, XDebuggerActions.EVALUATE_DIALOG_TREE_POPUP_GROUP,
                                         ((XDebugSessionImpl)session).getValueMarkers());
    myResultPanel = JBUI.Panels.simplePanel()
      .addToTop(new JLabel(XDebuggerBundle.message("xdebugger.evaluate.label.result")))
      .addToCenter(myTreePanel.getMainPanel());
    myMainPanel = new EvaluationMainPanel();

    mySwitchModeAction = new SwitchModeAction();

    new AnAction(){
      @Override
      public void update(AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null && LookupManager.getInstance(project).getActiveLookup() == null);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        doOKAction();
        addToWatches();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)), getRootPane(), myDisposable);

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        IdeFocusManager.getInstance(mySession.getProject()).requestFocus(myTreePanel.getTree(), true);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK)), getRootPane(),
                                myDisposable);

    Condition<TreeNode> rootFilter = node -> node.getParent() instanceof EvaluatingExpressionRootNode;
    myTreePanel.getTree().expandNodesOnLoad(rootFilter);
    myTreePanel.getTree().selectNodeOnLoad(rootFilter);

    EvaluationMode mode = XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().getEvaluationDialogMode();
    myIsCodeFragmentEvaluationSupported = evaluator.isCodeFragmentEvaluationSupported();
    if (mode == EvaluationMode.CODE_FRAGMENT && !myIsCodeFragmentEvaluationSupported) {
      mode = EvaluationMode.EXPRESSION;
    }
    if (mode == EvaluationMode.EXPRESSION && text.getMode() == EvaluationMode.CODE_FRAGMENT && myIsCodeFragmentEvaluationSupported) {
      mode = EvaluationMode.CODE_FRAGMENT;
    }
    switchToMode(mode, text);
    init();
  }

  @Override
  protected void dispose() {
    super.dispose();
    myMainPanel.removeAll();
  }

  private void updateSourcePosition() {
    ApplicationManager.getApplication().invokeLater(() -> {
      mySourcePosition = mySession.getCurrentPosition();
      getInputEditor().setSourcePosition(mySourcePosition);
    });
  }

  @Override
  protected void doOKAction() {
    evaluate();
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction = new OkAction(){
      @Override
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if ((e.getModifiers() & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK)) == (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK)) {
          addToWatches();
        }
      }
    };
  }

  private void addToWatches() {
    if (myMode == EvaluationMode.EXPRESSION) {
      XExpression expression = getInputEditor().getExpression();
      if (!XDebuggerUtilImpl.isEmptyExpression(expression)) {
        XDebugSessionTab tab = ((XDebugSessionImpl)mySession).getSessionTab();
        if (tab != null) {
          tab.getWatchesView().addWatchExpression(expression, -1, true);
          getInputEditor().requestFocusInEditor();
        }
      }
    }
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    if (myIsCodeFragmentEvaluationSupported) {
      return new Action[]{getOKAction(), mySwitchModeAction, getCancelAction()};
    }
    return super.createActions();
  }

  @Override
  protected String getHelpId() {
    return "debugging.debugMenu.evaluate";
  }

  @Override
  protected JButton createJButtonForAction(Action action) {
    final JButton button = super.createJButtonForAction(action);
    if (action == mySwitchModeAction) {
      int width1 = new JButton(getSwitchButtonText(EvaluationMode.EXPRESSION)).getPreferredSize().width;
      int width2 = new JButton(getSwitchButtonText(EvaluationMode.CODE_FRAGMENT)).getPreferredSize().width;
      final Dimension size = new Dimension(Math.max(width1, width2), button.getPreferredSize().height);
      button.setMinimumSize(size);
      button.setPreferredSize(size);
    }
    return button;
  }

  public XExpression getExpression() {
    return getInputEditor().getExpression();
  }

  private static String getSwitchButtonText(EvaluationMode mode) {
    return mode != EvaluationMode.EXPRESSION
           ? XDebuggerBundle.message("button.text.expression.mode")
           : XDebuggerBundle.message("button.text.code.fragment.mode");
  }

  private void switchToMode(EvaluationMode mode, XExpression text) {
    if (myMode == mode) return;

    myMode = mode;

    if (mode == EvaluationMode.EXPRESSION) {
      text = new XExpressionImpl(StringUtil.convertLineSeparators(text.getExpression(), " "), text.getLanguage(), text.getCustomInfo());
    }

    myInputComponent = createInputComponent(mode, text);
    myMainPanel.removeAll();
    myInputComponent.addComponent(myMainPanel, myResultPanel);

    setTitle(myInputComponent.getTitle());
    mySwitchModeAction.putValue(Action.NAME, getSwitchButtonText(mode));
    getInputEditor().requestFocusInEditor();
  }

  private XDebuggerEditorBase getInputEditor() {
    return myInputComponent.getInputEditor();
  }

  private EvaluationInputComponent createInputComponent(EvaluationMode mode, XExpression text) {
    final Project project = mySession.getProject();
    text = XExpressionImpl.changeMode(text, mode);
    if (mode == EvaluationMode.EXPRESSION) {
      return new ExpressionInputComponent(project, myEditorsProvider, mySourcePosition, text, myDisposable);
    }
    else {
      return new CodeFragmentInputComponent(project, myEditorsProvider, mySourcePosition, text,
                                            getDimensionServiceKey() + ".splitter", myDisposable);
    }
  }

  private void evaluate() {
    final XDebuggerEditorBase inputEditor = getInputEditor();
    int offset = -1;

    //try to save caret position
    Editor editor = inputEditor.getEditor();
    if (editor != null) {
      offset = editor.getCaretModel().getOffset();
    }

    final XDebuggerTree tree = myTreePanel.getTree();
    tree.markNodesObsolete();
    tree.setRoot(new EvaluatingExpressionRootNode(this, tree), false);

    myResultPanel.invalidate();

    //editor is already changed
    editor = inputEditor.getEditor();
    //selectAll puts focus back
    inputEditor.selectAll();

    //try to restore caret position and clear selection
    if (offset >= 0 && editor != null) {
      offset = Math.min(editor.getDocument().getTextLength(), offset);
      editor.getCaretModel().moveToOffset(offset);
      editor.getSelectionModel().setSelection(offset, offset);
    }
  }

  @Override
  public void doCancelAction() {
    getInputEditor().saveTextInHistory();
    super.doCancelAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#xdebugger.evaluate";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public void startEvaluation(@NotNull XDebuggerEvaluator.XEvaluationCallback evaluationCallback) {
    final XDebuggerEditorBase inputEditor = getInputEditor();
    inputEditor.saveTextInHistory();
    XExpression expression = inputEditor.getExpression();

    XDebuggerEvaluator evaluator = mySession.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      evaluationCallback.errorOccurred(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"));
    }
    else {
      evaluator.evaluate(expression, evaluationCallback, null);
    }
  }

  public void evaluationDone() {
    mySession.rebuildViews();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return getInputEditor().getPreferredFocusedComponent();
  }

  private class SwitchModeAction extends AbstractAction {
    @Override
    public void actionPerformed(ActionEvent e) {
      XExpression text = getInputEditor().getExpression();
      EvaluationMode newMode = (myMode == EvaluationMode.EXPRESSION) ? EvaluationMode.CODE_FRAGMENT : EvaluationMode.EXPRESSION;
      // remember only on user selection
      XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().setEvaluationDialogMode(newMode);
      switchToMode(newMode, text);
    }
  }

  private class EvaluationMainPanel extends BorderLayoutPanel implements DataProvider {
    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (KEY.is(dataId)) {
        return XDebuggerEvaluationDialog.this;
      }
      return null;
    }
  }
}
