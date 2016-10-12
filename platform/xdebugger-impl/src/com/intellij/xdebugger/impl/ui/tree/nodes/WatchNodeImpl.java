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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

/**
 * @author nik
 */
public class WatchNodeImpl extends XValueNodeImpl implements WatchNode {
  private final XExpression myExpression;

  public WatchNodeImpl(@NotNull XDebuggerTree tree,
                       @NotNull WatchesRootNode parent,
                       @NotNull XExpression expression,
                       @Nullable XStackFrame stackFrame) {
    super(tree, parent, expression.getExpression(), new XWatchValue(expression, tree, stackFrame));
    myExpression = expression;
  }

  @Override
  @NotNull
  public XExpression getExpression() {
    return myExpression;
  }

  @NotNull
  @Override
  public XValue getValueContainer() {
    XValue container = super.getValueContainer();
    XValue value = ((XWatchValue)container).myValue;
    return value != null ? value : container;
  }

  void computePresentationIfNeeded() {
    if (getValuePresentation() == null) {
      getValueContainer().computePresentation(this, XValuePlace.TREE);
    }
  }

  private static class XWatchValue extends XNamedValue {
    private final XExpression myExpression;
    private final XDebuggerTree myTree;
    private final XStackFrame myStackFrame;
    private volatile XValue myValue;

    public XWatchValue(XExpression expression, XDebuggerTree tree, XStackFrame stackFrame) {
      super(expression.getExpression());
      myExpression = expression;
      myTree = tree;
      myStackFrame = stackFrame;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
      if (myValue != null) {
        myValue.computeChildren(node);
      }
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      if (myStackFrame != null) {
        if (myTree.isShowing() || ApplicationManager.getApplication().isUnitTestMode()) {
          XDebuggerEvaluator evaluator = myStackFrame.getEvaluator();
          if (evaluator != null) {
            evaluator.evaluate(myExpression, new MyEvaluationCallback(node, place), myStackFrame.getSourcePosition());
          }
        }
      }
      else {
        node.setPresentation(AllIcons.Debugger.Watch, EMPTY_PRESENTATION, false);
      }
    }

    private class MyEvaluationCallback extends XEvaluationCallbackBase implements Obsolescent {
      @NotNull private final XValueNode myNode;
      @NotNull private final XValuePlace myPlace;

      public MyEvaluationCallback(@NotNull XValueNode node, @NotNull XValuePlace place) {
        myNode = node;
        myPlace = place;
      }

      @Override
      public boolean isObsolete() {
        return myNode.isObsolete();
      }

      @Override
      public void evaluated(@NotNull XValue result) {
        myValue = result;
        result.computePresentation(myNode, myPlace);
      }

      @Override
      public void errorOccurred(@NotNull String errorMessage) {
        myNode.setPresentation(XDebuggerUIConstants.ERROR_MESSAGE_ICON, new XErrorValuePresentation(errorMessage), false);
      }
    }

    private static final XValuePresentation EMPTY_PRESENTATION = new XValuePresentation() {
      @NotNull
      @Override
      public String getSeparator() {
        return "";
      }

      @Override
      public void renderValue(@NotNull XValueTextRenderer renderer) {
      }
    };

    @Override
    @Nullable
    public String getEvaluationExpression() {
      return myValue != null ? myValue.getEvaluationExpression() : null;
    }

    @Override
    @NotNull
    public Promise<XExpression> calculateEvaluationExpression() {
      return Promise.resolve(myExpression);
    }

    @Override
    @Nullable
    public XInstanceEvaluator getInstanceEvaluator() {
      return myValue != null ? myValue.getInstanceEvaluator() : null;
    }

    @Override
    @Nullable
    public XValueModifier getModifier() {
      return myValue != null ? myValue.getModifier() : null;
    }

    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
      if (myValue != null) {
        myValue.computeSourcePosition(navigatable);
      }
    }

    @Override
    @NotNull
    public ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
      return ThreeState.NO;
    }

    @Override
    public boolean canNavigateToSource() {
      return myValue != null && myValue.canNavigateToSource();
    }

    @Override
    public boolean canNavigateToTypeSource() {
      return myValue != null && myValue.canNavigateToTypeSource();
    }

    @Override
    public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {
      if (myValue != null) {
        myValue.computeTypeSourcePosition(navigatable);
      }
    }

    @Override
    @Nullable
    public XReferrersProvider getReferrersProvider() {
      return myValue != null ? myValue.getReferrersProvider() : null;
    }
  }
}