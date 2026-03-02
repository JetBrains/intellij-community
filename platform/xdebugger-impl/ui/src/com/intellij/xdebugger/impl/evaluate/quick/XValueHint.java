// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy;
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter;
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
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler;
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.XValueTextProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackWithOrigin;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.awt.Point;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

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
         XDebuggerEntityConverter.asProxy(session).getValueMarkers(), session.getCurrentPosition(), fromKeyboard);
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
                                                    @Nullable XFullValueEvaluator evaluator,
                                                    @Nullable XDebuggerTreeNodeHyperlink link) {
    var panel = installInformationProperties(new BorderLayoutPanel());
    SimpleColoredComponent component = HintUtil.createInformationComponent();
    component.setIcon(icon);
    text.appendToComponent(component);
    panel.add(component);
    if (evaluator != null || link != null) {
      var linkComponent = new SimpleColoredComponent();
      if (link != null) {
        appendAdditionalHyperlink(link, linkComponent);
      } else {
        appendEvaluatorLink(evaluator, linkComponent);
      }
      LinkMouseListenerBase.installSingleTagOn(linkComponent);
      panel.addToRight(linkComponent);
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
      result.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeExImpl() {
        private XFullValueEvaluator myFullValueEvaluator;
        private boolean myShown = false;
        private SimpleColoredComponent mySimpleColoredComponent;
        private @Nullable HintPresentation myHintPresentation;
        private @Nullable XDebuggerTreeNodeHyperlink myLink;

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

          HintPresentation presentation = new HintPresentation(icon, text, hasChildren, valuePresenter, myFullValueEvaluator, myLink);
          myHintPresentation = presentation;

          if (!hasChildren) {
            // show simple popup if there are no children
            mySimpleColoredComponent = null;
            showTooltipPopup(createHintComponent(presentation.icon(), presentation.text(),
                                                 presentation.valuePresentation(), presentation.evaluator(), presentation.link()));
          }
          else if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
            // show full evaluation popup if the hint is explicitly requested
            if (!myShown) {
              Runnable showPopupRunnable = getShowPopupRunnable(result, presentation.evaluator());
              showPopupRunnable.run();
            }
          }
          else {
            // show simple popup, which can be expanded to the full one
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
            if (updateShownExpandableHint(presentation)) {
              return;
            }

            mySimpleColoredComponent = createExpandableHintComponent(presentation.icon(), presentation.text(),
                                                                     getShowPopupRunnable(result, presentation.evaluator()),
                                                                     presentation.evaluator(), presentation.valuePresentation(), presentation.link());
            if (mySimpleColoredComponent instanceof SimpleColoredComponentWithProgress) {
              // TODO: it seems like that we are skipping "Collecting data..." this way, assuming that it will be the first presentation
              //   But this is not a correct way, UI should send "Collecting data..." presentation instead of the backend
              ((SimpleColoredComponentWithProgress)mySimpleColoredComponent).startLoading();
            }
            showTooltipPopup(mySimpleColoredComponent);
          }
          myShown = true;
        }

        @Override
        public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
          myFullValueEvaluator = fullValueEvaluator;
          updateDisplayedTooltip(oldPresentation -> new HintPresentation(
            oldPresentation.icon(), oldPresentation.text(), oldPresentation.hasChildren(),
            oldPresentation.valuePresentation(), fullValueEvaluator, oldPresentation.link()
          ));
        }

        private void updateDisplayedTooltip(Function<HintPresentation, HintPresentation> buildNewPresentation) {
          if (!myShown || isHintHidden()) {
            return;
          }
          if (myHintPresentation == null) {
            return;
          }

          HintPresentation newPresentation = buildNewPresentation.apply(myHintPresentation);
          myHintPresentation = newPresentation;

          if (!newPresentation.hasChildren()) {
            showTooltipPopup(createHintComponent(newPresentation.icon(), newPresentation.text(),
                                                 newPresentation.valuePresentation(), newPresentation.evaluator(), newPresentation.link()));
            return;
          }

          updateShownExpandableHint(newPresentation);
        }

        @Override
        public boolean isObsolete() {
          return isHintHidden();
        }

        /**
         * Updates the currently displayed hint with new content based on the provided {@link presentation}.
         * If the hint component's preferred size changes as a result of the update, the popup is resized accordingly.
         */
        private boolean updateShownExpandableHint(@NotNull HintPresentation presentation) {
          if (mySimpleColoredComponent == null) {
            return false;
          }
          if (mySimpleColoredComponent instanceof SimpleColoredComponentWithProgress) {
            ((SimpleColoredComponentWithProgress)mySimpleColoredComponent).stopLoading();
          }
          Icon previousIcon = mySimpleColoredComponent.getIcon();
          var previousPreferredWidth = mySimpleColoredComponent.getPreferredSize().width;

          mySimpleColoredComponent.clear();
          fillSimpleColoredComponent(mySimpleColoredComponent, previousIcon, presentation.text(), presentation.evaluator(), presentation.link());

          var delta = mySimpleColoredComponent.getPreferredSize().width - previousPreferredWidth;
          if (delta > 0) {
            resizePopup(delta, 0);
          }
          return true;
        }

        @Override
        public void clearAdditionalHyperlinks() {
          myLink = null;
        }

        @Override
        public void addAdditionalHyperlink(@NotNull XDebuggerTreeNodeHyperlink link) {
          if (myLink != null) {
            LOG.error("Replacing additional link with the new one. Only one can be displayed. Previous: `" + myLink.getLinkText() + "`, new: `" + link + "`");
          }
          myLink = link;
          updateDisplayedTooltip(oldPresentation -> new HintPresentation(
            oldPresentation.icon(), oldPresentation.text(), oldPresentation.hasChildren(),
            oldPresentation.valuePresentation(), oldPresentation.evaluator(), link
          ));
        }

        @Override
        public void clearFullValueEvaluator() {
          myFullValueEvaluator = null;
        }

        @Override
        public @NotNull XValue getXValue() {
          return result;
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

  private record HintPresentation(@Nullable Icon icon,
                                  @NotNull SimpleColoredText text,
                                  boolean hasChildren,
                                  @NotNull XValuePresentation valuePresentation,
                                  @Nullable XFullValueEvaluator evaluator,
                                  @Nullable XDebuggerTreeNodeHyperlink link) {
  }
}
