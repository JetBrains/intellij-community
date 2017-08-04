/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class XValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance(XValueHint.class);

  private final XDebuggerEditorsProvider myEditorsProvider;
  private final XDebuggerEvaluator myEvaluator;
  private final XDebugSession myDebugSession;
  private final boolean myFromKeyboard;
  private final String myExpression;
  private final String myValueName;
  private final XSourcePosition myExpressionPosition;
  private Disposable myDisposable;

  private static final Key<XValueHint> HINT_KEY = Key.create("allows only one value hint per editor");

  public XValueHint(@NotNull Project project,
                    @NotNull Editor editor,
                    @NotNull Point point,
                    @NotNull ValueHintType type,
                    @NotNull ExpressionInfo expressionInfo,
                    @NotNull XDebuggerEvaluator evaluator,
                    @NotNull XDebugSession session,
                    boolean fromKeyboard) {
    this(project, session.getDebugProcess().getEditorsProvider(), editor, point, type, expressionInfo, evaluator, session, fromKeyboard);
  }

  protected XValueHint(@NotNull Project project,
                       @NotNull XDebuggerEditorsProvider editorsProvider,
                       @NotNull Editor editor,
                       @NotNull Point point,
                       @NotNull ValueHintType type,
                       @NotNull ExpressionInfo expressionInfo,
                       @NotNull XDebuggerEvaluator evaluator,
                       boolean fromKeyboard) {
    this(project, editorsProvider, editor, point, type, expressionInfo, evaluator, null, fromKeyboard);
  }

  private XValueHint(@NotNull Project project,
                     @NotNull XDebuggerEditorsProvider editorsProvider,
                     @NotNull Editor editor,
                     @NotNull Point point,
                     @NotNull ValueHintType type,
                     @NotNull ExpressionInfo expressionInfo,
                     @NotNull XDebuggerEvaluator evaluator,
                     @Nullable XDebugSession session,
                     boolean fromKeyboard) {
    super(project, editor, point, type, expressionInfo.getTextRange());
    myEditorsProvider = editorsProvider;
    myEvaluator = evaluator;
    myDebugSession = session;
    myFromKeyboard = fromKeyboard;
    myExpression = XDebuggerEvaluateActionHandler.getExpressionText(expressionInfo, editor.getDocument());
    myValueName = XDebuggerEvaluateActionHandler.getDisplayText(expressionInfo, editor.getDocument());

    VirtualFile file;
    ConsoleView consoleView = ConsoleViewImpl.CONSOLE_VIEW_IN_EDITOR_VIEW.get(editor);
    if (consoleView instanceof LanguageConsoleView) {
      LanguageConsoleView console = ((LanguageConsoleView)consoleView);
      file = console.getHistoryViewer() == editor ? console.getVirtualFile() : null;
    }
    else {
      file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    }

    myExpressionPosition = file != null ? XDebuggerUtil.getInstance().createPositionByOffset(file, expressionInfo.getTextRange().getStartOffset()) : null;
  }

  @Override
  protected boolean canShowHint() {
    return true;
  }

  @Override
  protected boolean showHint(final JComponent component) {
    boolean result = super.showHint(component);
    if (result) {
      XValueHint prev = getEditor().getUserData(HINT_KEY);
      if (prev != null) {
        prev.hideHint();
      }
      getEditor().putUserData(HINT_KEY, this);
    }
    return result;
  }

  @Override
  protected void onHintHidden() {
    super.onHintHidden();
    XValueHint prev = getEditor().getUserData(HINT_KEY);
    if (prev == this) {
      getEditor().putUserData(HINT_KEY, null);
    }
    disposeVisibleHint();
  }

  @Override
  public void hideHint() {
    super.hideHint();
    disposeVisibleHint();
  }

  @Override
  protected void evaluateAndShowHint() {
    AtomicBoolean showEvaluating = new AtomicBoolean(true);
    EdtExecutorService.getScheduledExecutorInstance().schedule(() -> {
      if (myCurrentHint == null && showEvaluating.get()) {
        SimpleColoredComponent component = HintUtil.createInformationComponent();
        component.append(XDebuggerUIConstants.EVALUATING_EXPRESSION_MESSAGE);
        showHint(component);
      }
    }, 200, TimeUnit.MILLISECONDS);

    myEvaluator.evaluate(myExpression, new XEvaluationCallbackBase() {
      @Override
      public void evaluated(@NotNull final XValue result) {
        result.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
          private XFullValueEvaluator myFullValueEvaluator;
          private boolean myShown = false;

          @Override
          public void applyPresentation(@Nullable Icon icon,
                                        @NotNull XValuePresentation valuePresenter,
                                        boolean hasChildren) {
            showEvaluating.set(false);
            if (isHintHidden()) {
              return;
            }

            SimpleColoredText text = new SimpleColoredText();
            XValueNodeImpl.buildText(valuePresenter, text, false);

            if (!hasChildren) {
              JComponent component = createHintComponent(text, valuePresenter, myFullValueEvaluator);
              showHint(component);
            }
            else if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
              if (!myShown) {
                showTree(result);
              }
            }
            else {
              if (getType() == ValueHintType.MOUSE_OVER_HINT) {
                if (myFromKeyboard) {
                  text.insert(0, "(" + KeymapUtil.getFirstKeyboardShortcutText("ShowErrorDescription") + ") ",
                              SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }

                // first remove a shortcut created for any previous presentation (like "Collecting data...")
                disposeVisibleHint();
                myDisposable = Disposer.newDisposable();
                ShortcutSet shortcut = ActionManager.getInstance().getAction("ShowErrorDescription").getShortcutSet();
                new DumbAwareAction() {
                  @Override
                  public void actionPerformed(@NotNull AnActionEvent e) {
                    showTree(result);
                  }
                }.registerCustomShortcutSet(shortcut, getEditor().getContentComponent(), myDisposable);
              }

              showHint(createExpandableHintComponent(text, () -> showTree(result)));
            }
            myShown = true;
          }

          @Override
          public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
            myFullValueEvaluator = fullValueEvaluator;
          }

          @Override
          public boolean isObsolete() {
            return isHintHidden();
          }
        }, XValuePlace.TOOLTIP);
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        showEvaluating.set(false);
        if (myCurrentHint != null) {
          myCurrentHint.hide();
        }
        if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
          ApplicationManager.getApplication().invokeLater(() -> showHint(HintUtil.createErrorLabel(errorMessage)));
        }
        LOG.debug("Cannot evaluate '" + myExpression + "':" + errorMessage);
      }
    }, myExpressionPosition);
  }

  @NotNull
  protected JComponent createHintComponent(@NotNull SimpleColoredText text,
                                           @NotNull XValuePresentation presentation,
                                           @Nullable XFullValueEvaluator evaluator) {
    SimpleColoredComponent component = HintUtil.createInformationComponent();
    text.appendToComponent(component);
    if (evaluator != null) {
      component.append(
        evaluator.getLinkText(),
        XDebuggerTreeNodeHyperlink.TEXT_ATTRIBUTES,
        (Consumer<MouseEvent>)event -> DebuggerUIUtil.showValuePopup(evaluator, event, getProject(), getEditor())
      );
      LinkMouseListenerBase.installSingleTagOn(component);
    }
    return component;
  }

  private void disposeVisibleHint() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  private void showTree(@NotNull XValue value) {
    if (myCurrentHint != null) {
      myCurrentHint.hide();
    }
    XValueMarkers<?,?> valueMarkers = myDebugSession == null ? null : ((XDebugSessionImpl)myDebugSession).getValueMarkers();
    XSourcePosition position = myDebugSession == null ? null : myDebugSession.getCurrentPosition();
    XDebuggerTreeCreator creator = new XDebuggerTreeCreator(getProject(), myEditorsProvider, position, valueMarkers);
    showTreePopup(creator, Pair.create(value, myValueName));
  }

  @Override
  public String toString() {
    return myExpression;
  }
}
