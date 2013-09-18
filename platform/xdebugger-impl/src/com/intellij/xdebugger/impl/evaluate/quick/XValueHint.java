/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleColoredText;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XValueHint extends AbstractValueHint {
  private static final Logger LOG = Logger.getInstance(XValueHint.class);

  private final XDebuggerEvaluator myEvaluator;
  private final XDebugSession myDebugSession;
  private final String myExpression;
  private final @Nullable XSourcePosition myExpressionPosition;

  public XValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, @NotNull ValueHintType type,
                    @NotNull Pair<TextRange, String> expressionData, @NotNull XDebuggerEvaluator evaluator,
                    @NotNull XDebugSession session) {
    super(project, editor, point, type, expressionData.first);

    myEvaluator = evaluator;
    myDebugSession = session;
    myExpression = XDebuggerEvaluateActionHandler.getExpressionText(expressionData, editor.getDocument());
    final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    myExpressionPosition = file != null ? XDebuggerUtil.getInstance().createPositionByOffset(file, expressionData.first.getStartOffset()) : null;
  }

  @Override
  protected boolean canShowHint() {
    return true;
  }

  @Override
  protected void evaluateAndShowHint() {
    myEvaluator.evaluate(myExpression, new XEvaluationCallbackBase() {
      @Override
      public void evaluated(@NotNull final XValue result) {
        result.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl() {
          @Override
          public void applyPresentation(@Nullable Icon icon,
                                        @NotNull XValuePresentation valuePresenter,
                                        boolean hasChildren) {
            if (isHintHidden()) return;

            SimpleColoredText text = new SimpleColoredText();
            text.append(myExpression, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
            XValueNodeImpl.buildText(valuePresenter, text);
            if (!hasChildren) {
              showHint(HintUtil.createInformationLabel(text));
            }
            else if (getType() == ValueHintType.MOUSE_CLICK_HINT) {
              showTree(result, myExpression);
            }
            else {
              JComponent component = createExpandableHintComponent(text, new Runnable() {
                @Override
                public void run() {
                  showTree(result, myExpression);
                }
              });
              showHint(component);
            }
          }

          @Override
          public void setFullValueEvaluator(@NotNull XFullValueEvaluator fullValueEvaluator) {
            //todo[nik] implement?
          }

          @Override
          public boolean isObsolete() {
            //todo[nik]
            return false;
          }
        }, XValuePlace.TOOLTIP);
      }

      @Override
      public void errorOccurred(@NotNull final String errorMessage) {
        LOG.debug("Cannot evaluate '" + myExpression + "':" + errorMessage);
      }
    }, myExpressionPosition);
  }

  private void showTree(final XValue value, final String name) {
    XValueMarkers<?,?> valueMarkers = ((XDebugSessionImpl)myDebugSession).getValueMarkers();
    XDebuggerTree tree = new XDebuggerTree(myDebugSession.getProject(), myDebugSession.getDebugProcess().getEditorsProvider(),
                                           myDebugSession.getCurrentPosition(), XDebuggerActions.INSPECT_TREE_POPUP_GROUP, valueMarkers);
    tree.getModel().addTreeModelListener(createTreeListener(tree));
    XValueHintTreeComponent component = new XValueHintTreeComponent(this, tree, Pair.create(value, name));
    showTreePopup(component, tree, name);
  }
}
