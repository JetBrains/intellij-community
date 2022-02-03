// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.psi.PsiElement;
import com.intellij.ui.*;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class XValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance(XValueHint.class);

  private final XDebuggerEditorsProvider myEditorsProvider;
  private final XDebuggerEvaluator myEvaluator;
  private final XDebugSession myDebugSession;
  private final boolean myFromKeyboard;
  private final String myExpression;
  private final String myValueName;
  private final PsiElement myElement;
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
    myElement = expressionInfo.getElement();

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
        component.append(XDebuggerUIConstants.getEvaluatingExpressionMessage());
        showHint(component);
      }
    }, 200, TimeUnit.MILLISECONDS);

    XEvaluationCallbackBase callback = new XEvaluationCallbackBase() {
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
              showHint(createHintComponent(icon, text, valuePresenter, myFullValueEvaluator));
            }
            else if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
              if (!myShown) {
                Runnable showPopupRunnable = getShowPopupRunnable(result, myFullValueEvaluator);
                showPopupRunnable.run();
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
                DumbAwareAction.create(e -> showTree(result))
                               .registerCustomShortcutSet(shortcut, getEditor().getContentComponent(), myDisposable);
              }

              showHint(createExpandableHintComponent(icon, text, getShowPopupRunnable(result, myFullValueEvaluator), myFullValueEvaluator));
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
        ApplicationManager.getApplication().invokeLater(() -> {
          if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
            showHint(HintUtil.createErrorLabel(errorMessage));
          }
          else if (myCurrentHint != null) {
            myCurrentHint.hide();
          }
        });
        LOG.debug("Cannot evaluate '" + myExpression + "':" + errorMessage);
      }
    };
    if (myElement != null && myEvaluator instanceof XDebuggerPsiEvaluator) {
      ((XDebuggerPsiEvaluator)myEvaluator).evaluate(myElement, callback);
    }
    else {
      myEvaluator.evaluate(myExpression, callback, myExpressionPosition);
    }
  }

  @NotNull
  protected JComponent createHintComponent(@Nullable Icon icon,
                                           @NotNull SimpleColoredText text,
                                           @NotNull XValuePresentation presentation,
                                           @Nullable XFullValueEvaluator evaluator) {
    SimpleColoredComponent component = HintUtil.createInformationComponent();
    component.setIcon(icon);
    text.appendToComponent(component);
    appendEvaluatorLink(evaluator, component);
    if (evaluator != null) {
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
    showTreePopup(getTreeCreator(), Pair.create(value, myValueName));
  }

  private void showText(@NotNull String initialText, @NotNull XValue value, @Nullable XFullValueEvaluator evaluator) {
    if (myCurrentHint != null) {
      myCurrentHint.hide();
    }
    showTextPopup(getTreeCreator(), Pair.create(value, myValueName), initialText, evaluator);
  }

  private XDebuggerTreeCreator getTreeCreator() {
    XValueMarkers<?,?> valueMarkers = myDebugSession == null ? null : ((XDebugSessionImpl)myDebugSession).getValueMarkers();
    XSourcePosition position = myDebugSession == null ? null : myDebugSession.getCurrentPosition();
    return new XDebuggerTreeCreator(getProject(), myEditorsProvider, position, valueMarkers) {
      @Override
      public @NotNull String getTitle(@NotNull Pair<XValue, String> descriptor) {
        return "";
      }
    };
  }

  private Runnable getShowPopupRunnable(@NotNull XValue value, @Nullable XFullValueEvaluator evaluator) {
    if (value instanceof XValueTextProvider && ((XValueTextProvider)value).shouldShowTextValue()) {
      @NotNull String initialText = StringUtil.notNullize(((XValueTextProvider)value).getValueText());
      return () -> showText(initialText, value, evaluator);
    }
    return () -> showTree(value);
  }

  @Override
  public String toString() {
    return myExpression;
  }
}
