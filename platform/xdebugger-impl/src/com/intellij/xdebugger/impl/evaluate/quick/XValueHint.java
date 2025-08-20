// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorMouseHoverPopupManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredComponentWithProgress;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.components.BorderLayoutPanel;
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
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.hint.HintUtil.installInformationProperties;

public class XValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance(XValueHint.class);

  private final XDebuggerEditorsProvider myEditorsProvider;
  private final int myOffset;
  private final XDebuggerEvaluator myEvaluator;
  private final XSourcePosition myPosition;
  private final boolean myFromKeyboard;
  private final String myExpression;
  private final String myValueName;
  private final XSourcePosition myExpressionPosition;
  private final boolean myIsManualSelection;
  private Disposable myDisposable;
  private final XValueMarkers<?, ?> myValueMarkers;

  @ApiStatus.Internal
  public XValueHint(@NotNull Project project,
                    @NotNull Editor editor,
                    @NotNull Point point,
                    @NotNull ValueHintType type,
                    int offset,
                    @NotNull ExpressionInfo expressionInfo,
                    @NotNull XDebuggerEvaluator evaluator,
                    @NotNull XDebugSession session,
                    boolean fromKeyboard) {
    this(project, session.getDebugProcess().getEditorsProvider(), editor, point, type, offset, expressionInfo, evaluator,
         ((XDebugSessionImpl)session).getValueMarkers(), session.getCurrentPosition(), fromKeyboard);
  }

  @ApiStatus.Internal
  public XValueHint(@NotNull Project project,
                    @NotNull Editor editor,
                    @NotNull Point point,
                    @NotNull ValueHintType type,
                    int offset,
                    @NotNull ExpressionInfo expressionInfo,
                    @NotNull XDebuggerEvaluator evaluator,
                    @NotNull XDebugSessionProxy sessionProxy,
                    boolean fromKeyboard) {
    this(project, sessionProxy.getEditorsProvider(), editor, point, type, offset, expressionInfo, evaluator,
         sessionProxy.getValueMarkers(), sessionProxy.getCurrentPosition(), fromKeyboard);
  }

  @ApiStatus.Internal
  public XValueHint(@NotNull Project project,
                       @NotNull XDebuggerEditorsProvider editorsProvider,
                       @NotNull Editor editor,
                       @NotNull Point point,
                       @NotNull ValueHintType type,
                       int offset,
                       @NotNull ExpressionInfo expressionInfo,
                       @NotNull XDebuggerEvaluator evaluator,
                       boolean fromKeyboard) {
    this(project, editorsProvider, editor, point, type, offset, expressionInfo, evaluator, null, null, fromKeyboard);
  }

  private XValueHint(@NotNull Project project,
                     @NotNull XDebuggerEditorsProvider editorsProvider,
                     @NotNull Editor editor,
                     @NotNull Point point,
                     @NotNull ValueHintType type,
                     int offset,
                     @NotNull ExpressionInfo expressionInfo,
                     @NotNull XDebuggerEvaluator evaluator,
                     @Nullable XValueMarkers<?, ?> valueMarkers,
                     @Nullable XSourcePosition expressionPosition,
                     boolean fromKeyboard) {
    super(project, editor, point, type, expressionInfo.getTextRange());
    myEditorsProvider = editorsProvider;
    myOffset = offset;
    myEvaluator = evaluator;
    myValueMarkers = valueMarkers;
    myPosition = expressionPosition;
    myFromKeyboard = fromKeyboard;
    myIsManualSelection = expressionInfo.isManualSelection();
    myExpression = XDebuggerEvaluateActionHandler.getExpressionText(expressionInfo, editor.getDocument());
    myValueName = XDebuggerEvaluateActionHandler.getDisplayText(expressionInfo, editor.getDocument());

    VirtualFile file;
    ConsoleView consoleView = ConsoleViewImpl.CONSOLE_VIEW_IN_EDITOR_VIEW.get(editor);
    if (consoleView instanceof LanguageConsoleView console) {
      file = console.getHistoryViewer() == editor ? console.getVirtualFile() : null;
    }
    else {
      file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    }

    myExpressionPosition = file != null ? XDebuggerUtil.getInstance().createPositionByOffset(file, expressionInfo.getTextRange().getStartOffset()) : null;
  }

  @Override
  protected void onHintHidden() {
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
      if (!isHintHidden() && !isShowing() && showEvaluating.get()) {
        SimpleColoredComponent component = HintUtil.createInformationComponent();
        component.append(XDebuggerUIConstants.getEvaluatingExpressionMessage());
        showHint(component);
      }
    }, 200, TimeUnit.MILLISECONDS);

    XEvaluationCallbackBase callback = new MyEvaluationCallback(showEvaluating);
    if (!myIsManualSelection && myEvaluator instanceof XDebuggerDocumentOffsetEvaluator xDebuggerPsiEvaluator) {
      xDebuggerPsiEvaluator.evaluate(myEditor.getDocument(), myOffset, myType, callback);
    }
    else {
      myEvaluator.evaluate(myExpression, callback, myExpressionPosition);
    }
  }

  protected @NotNull JComponent createHintComponent(@Nullable Icon icon,
                                                    @NotNull SimpleColoredText text,
                                                    @NotNull XValuePresentation presentation,
                                                    @Nullable XFullValueEvaluator evaluator) {
    var panel = installInformationProperties(new BorderLayoutPanel());
    SimpleColoredComponent component = HintUtil.createInformationComponent();
    component.setIcon(icon);
    text.appendToComponent(component);
    panel.add(component);
    if (evaluator != null) {
      var evaluationLinkComponent = new SimpleColoredComponent();
      appendEvaluatorLink(evaluator, evaluationLinkComponent);
      LinkMouseListenerBase.installSingleTagOn(evaluationLinkComponent);
      panel.addToRight(evaluationLinkComponent);
    }
    return panel;
  }

  private void disposeVisibleHint() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
  }

  private void showTree(@NotNull XValue value) {
    showTreePopup(getTreeCreator(), Pair.create(value, myValueName));
  }

  private XDebuggerTreeCreator getTreeCreator() {
    return new XDebuggerTreeCreator(getProject(), myEditorsProvider, myPosition, myValueMarkers) {
      @Override
      public @NotNull String getTitle(@NotNull Pair<XValue, String> descriptor) {
        return "";
      }
    };
  }

  private Runnable getShowPopupRunnable(@NotNull XValue value, @Nullable XFullValueEvaluator evaluator) {
    if (value instanceof XValueTextProvider textValue && textValue.shouldShowTextValue()) {
      @NotNull String initialText = StringUtil.notNullize(textValue.getValueText());
      return () -> showTextPopup(getTreeCreator(), Pair.create(value, myValueName), initialText, evaluator);
    }
    return () -> showTree(value);
  }

  @Override
  public String toString() {
    return myExpression;
  }

  private class MyEvaluationCallback extends XEvaluationCallbackBase implements XEvaluationCallbackWithOrigin {
    private final AtomicBoolean myShowEvaluating;

    MyEvaluationCallback(AtomicBoolean showEvaluating) { myShowEvaluating = showEvaluating; }

    @Override
    public XEvaluationOrigin getOrigin() {
      return XEvaluationOrigin.EDITOR;
    }

    @Override
    public void evaluated(final @NotNull XValue result) {
      result.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
        private XFullValueEvaluator myFullValueEvaluator;
        private boolean myShown = false;
        private SimpleColoredComponent mySimpleColoredComponent;

        @Override
        public void applyPresentation(@Nullable Icon icon,
                                      @NotNull XValuePresentation valuePresenter,
                                      boolean hasChildren) {
          myShowEvaluating.set(false);
          if (isHintHidden()) {
            return;
          }

          SimpleColoredText text = new SimpleColoredText();
          XValueNodeImpl.buildText(valuePresenter, text, false);

          if (!hasChildren) {
            showTooltipPopup(createHintComponent(icon, text, valuePresenter, myFullValueEvaluator));
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
              ShortcutSet shortcut = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION);
              DumbAwareAction.create(e -> showTree(result))
                             .registerCustomShortcutSet(shortcut, getEditor().getContentComponent(), myDisposable);
            }

            // On presentation change we update our shown popup and resize if needed
            if (mySimpleColoredComponent != null) {
              if (mySimpleColoredComponent instanceof SimpleColoredComponentWithProgress) {
                ((SimpleColoredComponentWithProgress)mySimpleColoredComponent).stopLoading();
              }
              Icon previousIcon = mySimpleColoredComponent.getIcon();
              var previousPreferredWidth = mySimpleColoredComponent.getPreferredSize().width;

              mySimpleColoredComponent.clear();
              fillSimpleColoredComponent(mySimpleColoredComponent, previousIcon, text, myFullValueEvaluator);

              var delta = mySimpleColoredComponent.getPreferredSize().width - previousPreferredWidth;
              if (delta < 0) return;

              resizePopup(delta, 0);
              return;
            }

            mySimpleColoredComponent = createExpandableHintComponent(icon, text, getShowPopupRunnable(result, myFullValueEvaluator), myFullValueEvaluator, valuePresenter);
            if (mySimpleColoredComponent instanceof SimpleColoredComponentWithProgress) {
              ((SimpleColoredComponentWithProgress)mySimpleColoredComponent).startLoading();
            }
            showTooltipPopup(mySimpleColoredComponent);
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
    public void errorOccurred(final @NotNull String errorMessage) {
      myShowEvaluating.set(false);
      ApplicationManager.getApplication().invokeLater(() -> {
        switch (getType()) {
          case MOUSE_CLICK_HINT -> {
            showHint(HintUtil.createErrorLabel(errorMessage));
          }
          case MOUSE_OVER_HINT -> {
            hideCurrentHint();
            EditorMouseEvent event = getEditorMouseEvent();
            if (event != null) {
              EditorMouseHoverPopupManager.getInstance().showInfoTooltip(event);
            }
          }
          default -> {
            hideCurrentHint();
          }
        }
      });
      LOG.debug("Cannot evaluate '" + myExpression + "':" + errorMessage);
    }
  }
}
