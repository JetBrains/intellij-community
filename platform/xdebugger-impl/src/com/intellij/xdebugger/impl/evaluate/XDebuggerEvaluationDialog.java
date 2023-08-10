// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

public class XDebuggerEvaluationDialog extends DialogWrapper {
  public static final DataKey<XDebuggerEvaluationDialog> KEY = DataKey.create("DEBUGGER_EVALUATION_DIALOG");

  //can not use new SHIFT_DOWN_MASK etc because in this case ActionEvent modifiers do not match
  private static final int ADD_WATCH_MODIFIERS = (SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK) | InputEvent.SHIFT_MASK;
  public static final KeyStroke ADD_WATCH_KEYSTROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, ADD_WATCH_MODIFIERS);

  private final JPanel myMainPanel;
  private final JPanel myResultPanel;
  private final XDebuggerTreePanel myTreePanel;
  private EvaluationInputComponent myInputComponent;
  private final XDebugSession mySession;
  private final Supplier<? extends XDebuggerEvaluator> myEvaluatorSupplier;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private EvaluationMode myMode;
  private XSourcePosition mySourcePosition;
  private final SwitchModeAction mySwitchModeAction;

  public XDebuggerEvaluationDialog(@NotNull XDebugSession session,
                                   @NotNull XDebuggerEditorsProvider editorsProvider,
                                   @NotNull XExpression text,
                                   @Nullable XSourcePosition sourcePosition,
                                   boolean isCodeFragmentEvaluationSupported) {
    this(session, null, session.getProject(), editorsProvider, text, sourcePosition, isCodeFragmentEvaluationSupported);
  }

  public XDebuggerEvaluationDialog(@NotNull XDebuggerEvaluator evaluator,
                                   @NotNull Project project,
                                   @NotNull XDebuggerEditorsProvider editorsProvider,
                                   @NotNull XExpression text,
                                   @Nullable XSourcePosition sourcePosition,
                                   boolean isCodeFragmentEvaluationSupported) {
    this(null, () -> evaluator, project, editorsProvider, text, sourcePosition, isCodeFragmentEvaluationSupported);
  }

  private XDebuggerEvaluationDialog(@Nullable XDebugSession session,
                                    @Nullable Supplier<? extends XDebuggerEvaluator> evaluatorSupplier,
                                    @NotNull Project project,
                                    @NotNull XDebuggerEditorsProvider editorsProvider,
                                    @NotNull XExpression text,
                                    @Nullable XSourcePosition sourcePosition,
                                    boolean isCodeFragmentEvaluationSupported) {
    super(project, true);
    mySession = session;
    myEvaluatorSupplier = evaluatorSupplier;
    myProject = project;
    myEditorsProvider = editorsProvider;
    mySourcePosition = sourcePosition;
    setModal(false);
    setOKButtonText(XDebuggerBundle.message("xdebugger.button.evaluate"));
    setCancelButtonText(XDebuggerBundle.message("xdebugger.evaluate.dialog.close"));

    myTreePanel = new XDebuggerTreePanel(project, editorsProvider, myDisposable, sourcePosition, XDebuggerActions.EVALUATE_DIALOG_TREE_POPUP_GROUP,
                                         session == null ? null : ((XDebugSessionImpl)session).getValueMarkers());
    myResultPanel = JBUI.Panels.simplePanel()
      .addToTop(new JLabel(XDebuggerBundle.message("xdebugger.evaluate.label.result")))
      .addToCenter(myTreePanel.getMainPanel());
    myMainPanel = new EvaluationMainPanel();

    mySwitchModeAction = new SwitchModeAction();

    // preserve old mode switch shortcut
    DumbAwareAction.create(e -> mySwitchModeAction.actionPerformed(null))
      .registerCustomShortcutSet(
        new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK)),
        getRootPane(), myDisposable);

    new AnAction(){
      @Override
      public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setEnabled(session != null && project != null && LookupManager.getInstance(project).getActiveLookup() == null);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        //doOKAction(); // do not evaluate on add to watches
        addToWatches();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(ADD_WATCH_KEYSTROKE), getRootPane(), myDisposable);

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        IdeFocusManager.getInstance(project).requestFocus(myTreePanel.getTree(), true);
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK)), getRootPane(),
                                myDisposable);

    myTreePanel.getTree().expandNodesOnLoad(XDebuggerEvaluationDialog::isFirstChild);

    EvaluationMode mode = XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().getEvaluationDialogMode();
    if (mode == EvaluationMode.CODE_FRAGMENT && !isCodeFragmentEvaluationSupported) {
      mode = EvaluationMode.EXPRESSION;
    }
    if (mode == EvaluationMode.EXPRESSION && text.getMode() == EvaluationMode.CODE_FRAGMENT && isCodeFragmentEvaluationSupported) {
      mode = EvaluationMode.CODE_FRAGMENT;
    }
    setTitle(XDebuggerBundle.message("xdebugger.evaluate.dialog.title"));
    switchToMode(mode, text);
    DebuggerEvaluationStatisticsCollector.DIALOG_OPEN.log(project, mode);
    if (mode == EvaluationMode.EXPRESSION) {
      myInputComponent.getInputEditor().selectAll();
    }
    init();

    if (mySession != null) mySession.addSessionListener(new XDebugSessionListener() {
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
  }

  @Override
  protected void dispose() {
    super.dispose();
    myMainPanel.removeAll();
  }

  private void updateSourcePosition() {
    if (mySession == null) return;
    ApplicationManager.getApplication().invokeLater(() -> {
      mySourcePosition = mySession.getCurrentPosition();
      getInputEditor().setSourcePosition(mySourcePosition);
    });
  }

  @Override
  protected void doOKAction() {
    DebuggerEvaluationStatisticsCollector.EVALUATE.log(myProject, myMode);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("debugger.evaluate.expression");
    evaluate();
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction = new OkAction(){
      @Override
      public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        if (mySession != null && (e.getModifiers() & ADD_WATCH_MODIFIERS) == ADD_WATCH_MODIFIERS) {
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

  private static @Nls String getSwitchButtonText(EvaluationMode mode) {
    return mode != EvaluationMode.EXPRESSION
           ? XDebuggerBundle.message("button.text.expression.mode")
           : XDebuggerBundle.message("button.text.code.fragment.mode");
  }

  private void switchToMode(EvaluationMode mode, XExpression text) {
    if (myMode == mode) return;

    myMode = mode;

    Editor oldEditor = (myInputComponent != null) ? myInputComponent.getInputEditor().getEditor() : null;

    myInputComponent = createInputComponent(mode, text);
    myMainPanel.removeAll();
    myInputComponent.addComponent(myMainPanel, myResultPanel);

    XDebuggerEditorBase.copyCaretPosition(oldEditor, myInputComponent.getInputEditor().getEditor());

    mySwitchModeAction.putValue(Action.NAME, getSwitchButtonText(mode));
    getInputEditor().requestFocusInEditor();
  }

  private XDebuggerEditorBase getInputEditor() {
    return myInputComponent.getInputEditor();
  }

  private EvaluationInputComponent createInputComponent(EvaluationMode mode, XExpression text) {
    text = XExpressionImpl.changeMode(text, mode);
    if (mode == EvaluationMode.EXPRESSION) {
      ExpressionInputComponent component =
        new ExpressionInputComponent(myProject, myEditorsProvider, "evaluateExpression", mySourcePosition, text, myDisposable,
                                     mySession != null);
      component.getInputEditor().setExpandHandler(() -> mySwitchModeAction.actionPerformed(null));
      return component;
    }
    else {
      CodeFragmentInputComponent component = new CodeFragmentInputComponent(myProject, myEditorsProvider, mySourcePosition, text,
                                                                            getDimensionServiceKey() + ".splitter", myDisposable);
      component.getInputEditor().addCollapseButton(() -> mySwitchModeAction.actionPerformed(null));
      return component;
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
    tree.selectNodeOnLoad(XDebuggerEvaluationDialog::isFirstChild, Conditions.alwaysFalse());

    myResultPanel.invalidate();

    //editor is already changed
    editor = inputEditor.getEditor();
    inputEditor.requestFocusInEditor();

    //try to restore caret position and clear selection
    if (offset >= 0 && editor != null) {
      offset = Math.min(editor.getDocument().getTextLength(), offset);
      editor.getCaretModel().moveToOffset(offset);
      editor.getSelectionModel().setSelection(offset, offset);
    }
  }

  private static boolean isFirstChild(TreeNode node) {
    return node.getParent() instanceof EvaluatingExpressionRootNode;
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

    XDebuggerEvaluator evaluator = mySession == null ? myEvaluatorSupplier.get() : mySession.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      evaluationCallback.errorOccurred(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"));
    }
    else {
      evaluator.evaluate(expression, evaluationCallback, null);
    }
  }

  public void evaluationDone() {
    if (mySession != null) mySession.rebuildViews();
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
      DebuggerEvaluationStatisticsCollector.MODE_SWITCH.log(myProject, newMode);
      switchToMode(newMode, text);
    }
  }

  private class EvaluationMainPanel extends BorderLayoutPanel implements DataProvider {
    @Nullable
    @Override
    public Object getData(@NotNull @NonNls String dataId) {
      if (KEY.is(dataId)) {
        return XDebuggerEvaluationDialog.this;
      }
      return null;
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension d = super.getMinimumSize();
      d.width = Math.max(d.width, JBUI.scale(450));
      return d;
    }
  }
}
