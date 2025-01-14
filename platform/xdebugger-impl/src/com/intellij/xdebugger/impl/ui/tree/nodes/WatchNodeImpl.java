// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XErrorValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XAlwaysEvaluatedWatch;
import com.intellij.xdebugger.impl.XWatch;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XRendererDecoratorPresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.atomic.AtomicBoolean;

public class WatchNodeImpl extends XValueNodeImpl implements WatchNode {
  private final XWatch myWatch;

  public WatchNodeImpl(@NotNull XDebuggerTree tree,
                       @NotNull XDebuggerTreeNode parent,
                       @NotNull XWatch watch,
                       @Nullable XStackFrame stackFrame,
                       @Nullable String name,
                       @Nullable XValue value) {
    super(tree, parent,
          name == null ? renderName(watch.getExpression()) : name,
          value == null ? new XWatchValue(watch, tree, stackFrame) : value);
    myWatch = watch;
  }

  public WatchNodeImpl(@NotNull XDebuggerTree tree,
                       @NotNull WatchesRootNode parent,
                       @NotNull XExpression expression,
                       @Nullable XStackFrame stackFrame) {
    this(tree, parent, new XAlwaysEvaluatedWatch(expression), stackFrame, null, null);
  }

  protected WatchNodeImpl(XDebuggerTree tree, XDebuggerTreeNode parent, XExpression expression, XNamedValue value) {
    this(tree, parent, new XAlwaysEvaluatedWatch(expression), null, null, value);
  }

  public XWatch getXWatch() {
    return myWatch;
  }

  protected static String renderName(XExpression expression) {
    StringBuilder output = new StringBuilder();
    XValuePresentationUtil.renderName(expression.getExpression(), Integer.MAX_VALUE, s -> output.append(s));
    return output.toString();
  }

  @ApiStatus.Internal
  @Override
  protected boolean isChanged() {
    // Do not show paused watches as changed
    return super.isChanged() && !myWatch.isPaused();
  }

  @Override
  public @NotNull XExpression getExpression() {
    return myWatch.getExpression();
  }

  @Override
  public @NotNull XValue getValueContainer() {
    XValue container = super.getValueContainer();
    if (container instanceof XWatchValue) {
      XValue value = ((XWatchValue)container).myValue;
      if (value != null) {
        return value;
      }
    }
    return container;
  }

  public @NotNull XEvaluationOrigin getEvaluationOrigin() {
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
    private final XWatch myWatch;
    private final XDebuggerTree myTree;
    private final XStackFrame myStackFrame;
    private volatile XValue myValue;
    private final AtomicBoolean myEvaluateRequested = new AtomicBoolean(false);

    XWatchValue(XWatch watch, XDebuggerTree tree, XStackFrame stackFrame) {
      super(watch.getExpression().getExpression());
      myWatch = watch;
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
      clearPausedLink(node);
      if (myWatch.isPaused() && !myEvaluateRequested.getAndSet(false)) {
        showPausedPresentation(node, place);
        return;
      }
      if (myStackFrame != null) {
        if (myTree.isShowing() || ApplicationManager.getApplication().isUnitTestMode()) {
          XDebuggerEvaluator evaluator = myStackFrame.getEvaluator();
          if (evaluator != null) {
            evaluator.evaluate(myWatch.getExpression(), new MyEvaluationCallback(node, place), myStackFrame.getSourcePosition());
            return;
          }
        }
        else {
          return; // do not set anything if view is not visible, otherwise the code in computePresentationIfNeeded() will not work
        }
      }

      node.setPresentation(AllIcons.Debugger.Db_watch, EMPTY_PRESENTATION, false);
    }

    private void clearPausedLink(@NotNull XValueNode node) {
      if (node instanceof XValueNodeImpl xValueNode
          && xValueNode.getFullValueEvaluator() instanceof EvaluateLink) {
        xValueNode.clearFullValueEvaluator();
      }
    }

    private void showPausedPresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(AllIcons.Actions.Pause, PAUSED_PRESENTATION, false);
      node.setFullValueEvaluator(new EvaluateLink(node, place));
    }

    private class MyEvaluationCallback extends XEvaluationCallbackBase implements XEvaluationCallbackWithOrigin, Obsolescent {
      private final @NotNull XValueNode myNode;
      private final @NotNull XValuePlace myPlace;

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

      @Override
      public void invalidExpression(@NotNull String errorMessage) {
        // show as error for evaluation result nodes
        if (myNode instanceof WatchesRootNode.ResultNode) {
          errorOccurred(errorMessage);
          return;
        }
        myNode.setPresentation(AllIcons.Debugger.Db_watch, new GrayedPresentation(errorMessage), false);
      }
    }

    private static class GrayedPresentation extends XValuePresentation implements XRendererDecoratorPresentation {
      private final @NotNull String myErrorMessage;

      private GrayedPresentation(@NotNull String errorMessage) { myErrorMessage = errorMessage; }

      @Override
      public void renderValue(@NotNull XValueTextRenderer renderer) {
        renderer.renderComment(myErrorMessage);
      }

      @Override
      public @NotNull XCustomizableTextRenderer decorate(@NotNull XCustomizableTextRenderer renderer) {
        return new XCustomizableTextRenderer() {
          @Override
          public void renderComment(@NotNull String comment) {
            renderer.renderComment(comment);
          }

          @Override
          public void renderValue(@NotNull String value) {
            renderComment(value);
          }

          @Override
          public void renderStringValue(@NotNull String value, @Nullable String additionalSpecialCharsToHighlight, int maxLength) {
            renderComment(value);
          }

          @Override
          public void renderSpecialSymbol(@NotNull String symbol) {
            renderComment(symbol);
          }

          @Override
          public void renderError(@NotNull String error) {
            renderComment(error);
          }

          @Override
          public void renderRaw(@NotNull String text, @NotNull SimpleTextAttributes attributes) {
            renderComment(text);
          }

          @Override
          public void renderKeywordValue(@NotNull String value) {
            renderComment(value);
          }

          @Override
          public void renderNumericValue(@NotNull String value) {
            renderComment(value);
          }

          @Override
          public void renderStringValue(@NotNull String value) {
            renderComment(value);
          }

          @Override
          public void renderValue(@NotNull String value, @NotNull TextAttributesKey key) {
            renderComment(value);
          }
        };
      }
    }

    private static final XValuePresentation EMPTY_PRESENTATION = new XValuePresentation() {
      @Override
      public @NotNull String getSeparator() {
        return "";
      }

      @Override
      public void renderValue(@NotNull XValueTextRenderer renderer) {
      }
    };

    @Override
    public @Nullable String getEvaluationExpression() {
      return myValue != null ? myValue.getEvaluationExpression() : null;
    }

    @Override
    public @NotNull Promise<XExpression> calculateEvaluationExpression() {
      return Promises.resolvedPromise(myWatch.getExpression());
    }

    @Override
    public @Nullable XInstanceEvaluator getInstanceEvaluator() {
      return myValue != null ? myValue.getInstanceEvaluator() : null;
    }

    @Override
    public @Nullable XValueModifier getModifier() {
      return myValue != null ? myValue.getModifier() : null;
    }

    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
      if (myValue != null) {
        myValue.computeSourcePosition(navigatable);
      }
    }

    @Override
    public @NotNull ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
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
    public @Nullable XReferrersProvider getReferrersProvider() {
      return myValue != null ? myValue.getReferrersProvider() : null;
    }

    private class EvaluateLink extends XFullValueEvaluator {
      private final @NotNull XValueNode myNode;
      private final @NotNull XValuePlace myPlace;

      private EvaluateLink(@NotNull XValueNode node, @NotNull XValuePlace place) {
        super(XDebuggerBundle.message("xdebugger.evaluate.paused.watch"));
        myNode = node;
        myPlace = place;
        setShowValuePopup(false);
      }

      @Override
      public void startEvaluation(@NotNull XFullValueEvaluationCallback callback) {
        callback.evaluated("");
        myEvaluateRequested.set(true);
        computePresentation(myNode, myPlace);
      }
    }
  }

  @ApiStatus.Internal
  public static final XValuePresentation PAUSED_PRESENTATION = new XValuePresentation() {
    @Override
    public @NotNull String getSeparator() {
      return " ";
    }

    @Override
    public void renderValue(@NotNull XValueTextRenderer renderer) {
      renderer.renderComment(XDebuggerBundle.message("xdebugger.watch.is.paused.message"));
    }
  };
}