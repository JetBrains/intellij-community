/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.hint.HintManager;
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
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
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

/**
 * @author nik
 */
public class XValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance(XValueHint.class);

  private final XDebuggerEvaluator myEvaluator;
  private final XDebugSession myDebugSession;
  private final String myExpression;
  private final String myValueName;
  private final @Nullable XSourcePosition myExpressionPosition;
  private final ExpressionInfo myExpressionInfo;
  private Disposable myDisposable;

  private static final Key<XValueHint> HINT_KEY = Key.create("allows only one value hint per editor");

  public XValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, @NotNull ValueHintType type,
                    @NotNull ExpressionInfo expressionInfo, @NotNull XDebuggerEvaluator evaluator,
                    @NotNull XDebugSession session) {
    super(project, editor, point, type, expressionInfo.getTextRange());

    myEvaluator = evaluator;
    myDebugSession = session;
    myExpression = XDebuggerEvaluateActionHandler.getExpressionText(expressionInfo, editor.getDocument());
    myValueName = XDebuggerEvaluateActionHandler.getDisplayText(expressionInfo, editor.getDocument());
    myExpressionInfo = expressionInfo;

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
    if (result && getType() == ValueHintType.MOUSE_OVER_HINT) {
      myDisposable = Disposer.newDisposable();
      ShortcutSet shortcut = ActionManager.getInstance().getAction("ShowErrorDescription").getShortcutSet();
      new DumbAwareAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          hideHint();
          final Point point = new Point(myPoint.x, myPoint.y + getEditor().getLineHeight());
          new XValueHint(getProject(), getEditor(), point, ValueHintType.MOUSE_CLICK_HINT, myExpressionInfo, myEvaluator, myDebugSession).invokeHint();
        }
      }.registerCustomShortcutSet(shortcut, getEditor().getContentComponent(), myDisposable);
    }
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
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  @Override
  public void hideHint() {
    super.hideHint();
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }

  @Override
  protected void evaluateAndShowHint() {
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
            if (isHintHidden()) {
              return;
            }

            SimpleColoredText text = new SimpleColoredText();
            text.append(StringUtil.trimMiddle(myValueName, 200), XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
            XValueNodeImpl.buildText(valuePresenter, text);

            if (!hasChildren) {
              SimpleColoredComponent component = HintUtil.createInformationComponent();
              text.appendToComponent(component);
              if (myFullValueEvaluator != null) {
                component.append(myFullValueEvaluator.getLinkText(), XDebuggerTreeNodeHyperlink.TEXT_ATTRIBUTES,
                                 new Consumer<MouseEvent>() {
                                   @Override
                                   public void consume(MouseEvent event) {
                                     DebuggerUIUtil.showValuePopup(myFullValueEvaluator, event, getProject(), getEditor());
                                   }
                                 });
                LinkMouseListenerBase.installSingleTagOn(component);
              }
              showHint(component);
            }
            else if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
              if (!myShown) {
                showTree(result);
              }
            }
            else {
              if (getType() == ValueHintType.MOUSE_OVER_HINT) {
                text.insert(0, "(" + KeymapUtil.getFirstKeyboardShortcutText("ShowErrorDescription") + ") ",
                            SimpleTextAttributes.GRAYED_ATTRIBUTES);
              }

              JComponent component = createExpandableHintComponent(text, new Runnable() {
                @Override
                public void run() {
                  showTree(result);
                }
              });
              showHint(component);
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
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            int start = 0, end = 0;
            if (getCurrentRange() != null) {
              start = getCurrentRange().getStartOffset();
              end = getCurrentRange().getEndOffset();
            }
            HintManager.getInstance().showErrorHint(getEditor(), errorMessage, start,
                                                    end, HintManager.ABOVE,
                                                    HintManager.HIDE_BY_ESCAPE
                                                    | HintManager.HIDE_BY_TEXT_CHANGE
                                                    | HintManager.HIDE_BY_SCROLLING,
                                                    0);
          }
        });
        LOG.debug("Cannot evaluate '" + myExpression + "':" + errorMessage);
      }
    }, myExpressionPosition);
  }

  private void showTree(@NotNull XValue value) {
    XValueMarkers<?,?> valueMarkers = ((XDebugSessionImpl)myDebugSession).getValueMarkers();
    XDebuggerTreeCreator creator = new XDebuggerTreeCreator(myDebugSession.getProject(), myDebugSession.getDebugProcess().getEditorsProvider(),
                                                            myDebugSession.getCurrentPosition(), valueMarkers);
    showTreePopup(creator, Pair.create(value, myValueName));
  }
}
