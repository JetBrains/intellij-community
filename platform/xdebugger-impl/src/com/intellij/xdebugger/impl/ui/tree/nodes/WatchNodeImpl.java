// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.concurrency.Promises;

public class WatchNodeImpl extends XValueNodeImpl implements WatchNode {
  private final XExpression myExpression;

  public WatchNodeImpl(@NotNull XDebuggerTree tree,
                       @NotNull WatchesRootNode parent,
                       @NotNull XExpression expression,
                       @Nullable XStackFrame stackFrame) {
    this(tree, parent, expression, stackFrame, expression.getExpression());
  }

  WatchNodeImpl(@NotNull XDebuggerTree tree,
                @NotNull WatchesRootNode parent,
                @NotNull XExpression expression,
                @Nullable XStackFrame stackFrame,
                @NotNull String name) {
    this(tree, parent, expression, name, new XWatchValue(expression, tree, stackFrame));
  }

  WatchNodeImpl(@NotNull XDebuggerTree tree,
                @NotNull WatchesRootNode parent,
                @NotNull XExpression expression,
                @NotNull String name,
                @NotNull XValue value) {
    super(tree, parent, name, value);
    myExpression = expression;
  }

  protected WatchNodeImpl(XDebuggerTree tree, XDebuggerTreeNode parent, XExpression expression, XNamedValue value) {
    super(tree, parent, expression.getExpression(), value);
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
    if (container instanceof XWatchValue) {
      XValue value = ((XWatchValue)container).myValue;
      if (value != null) {
        return value;
      }
    }
    return container;
  }

  @NotNull
  public XEvaluationOrigin getEvaluationOrigin() {
    return XEvaluationOrigin.WATCH;
  }

  protected void evaluated() {
  }

  public void computePresentationIfNeeded() {
    if (getValuePresentation() == null) {
      getValueContainer().computePresentation(this, XValuePlace.TREE);
    }
  }

  @Override
  protected boolean shouldUpdateInlineDebuggerData() { // regular watches do not have inline data
    return false;
  }

  private static class XWatchValue extends XNamedValue {
    private final XExpression myExpression;
    private final XDebuggerTree myTree;
    private final XStackFrame myStackFrame;
    private volatile XValue myValue;

    XWatchValue(XExpression expression, XDebuggerTree tree, XStackFrame stackFrame) {
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
            return;
          }
        }
        else {
          return; // do not set anything if view is not visible, otherwise the code in computePresentationIfNeeded() will not work
        }
      }

      node.setPresentation(AllIcons.Debugger.Db_watch, EMPTY_PRESENTATION, false);
    }

    private class MyEvaluationCallback extends XEvaluationCallbackBase implements XEvaluationCallbackWithOrigin, Obsolescent {
      @NotNull private final XValueNode myNode;
      @NotNull private final XValuePlace myPlace;

      MyEvaluationCallback(@NotNull XValueNode node, @NotNull XValuePlace place) {
        myNode = node;
        myPlace = place;
      }

      @Override
      public XEvaluationOrigin getOrigin() {
        if (myNode instanceof WatchNodeImpl watchNode) {
          return watchNode.getEvaluationOrigin();
        }
        return XEvaluationOrigin.UNSPECIFIED_WATCH;
      }

      @Override
      public boolean isObsolete() {
        return myNode.isObsolete();
      }

      @Override
      public void evaluated(@NotNull XValue result) {
        myValue = result;
        if (myNode instanceof WatchNodeImpl watchNode) {
          watchNode.evaluated();
        }
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
      return Promises.resolvedPromise(myExpression);
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