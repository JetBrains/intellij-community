package org.jetbrains.debugger.frame;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.DebuggerViewSupport;
import org.jetbrains.debugger.EvaluateContext;
import org.jetbrains.debugger.Script;
import org.jetbrains.debugger.SuspendContext;
import org.jetbrains.debugger.values.StringValue;
import org.jetbrains.debugger.values.Value;

public class SuspendContextImpl extends XSuspendContext {
  private final ExecutionStackImpl executionStack;
  private final SuspendContext suspendContext;

  protected SuspendContextImpl(@NotNull SuspendContext suspendContext, @NotNull DebuggerViewSupport debugProcess, @Nullable Script topFrameScript) {
    executionStack = new ExecutionStackImpl(suspendContext, debugProcess, topFrameScript);
    this.suspendContext = suspendContext;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void clearObjectCaches() {
    suspendContext.getValueManager().clearCaches();
  }

  @Override
  @NotNull
  public XExecutionStack getActiveExecutionStack() {
    return executionStack;
  }

  public AsyncResult<String> evaluateLogExpression(@NotNull String expression) {
    CallFrameView frame = executionStack.getTopFrame();
    if (frame == null) {
      return new AsyncResult.Rejected<String>("Top frame is null");
    }
    return evaluateExpression(frame.getCallFrame().getEvaluateContext(), expression);
  }

  private static String formatErrorMessage(@Nullable String reason) {
    String messagePrefix = "Can not evaluate log expression";
    return reason != null ? messagePrefix + ": " + reason : messagePrefix;
  }

  @NotNull
  private static AsyncResult<String> evaluateExpression(@NotNull EvaluateContext evaluateContext, @NotNull String expression) {
    final AsyncResult<String> result = new AsyncResult<String>();
    evaluateContext.evaluate(expression).doWhenDone(new Consumer<Value>() {
      @Override
      public void consume(final Value value) {
        if (value == null) {
          result.setDone("Log expression result doesn't have value");
        }
        else {
          if (value instanceof StringValue && ((StringValue)value).isTruncated()) {
            ((StringValue)value).reloadHeavyValue().doWhenDone(new Runnable() {
              @Override
              public void run() {
                result.setDone(value.getValueString());
              }
            }).doWhenRejected(new Runnable() {
              @Override
              public void run() {
                result.setRejected(formatErrorMessage("whole expression result can not be loaded"));
              }
            });
          }
          else {
            result.setDone(value.getValueString());
          }
        }
      }
    }).doWhenRejected(new PairConsumer<Value, String>() {
      @Override
      public void consume(Value value, String error) {
        if (value == null) {
          result.setRejected(formatErrorMessage(error));
        }
        else {
          result.setRejected(formatErrorMessage(value.getValueString()));
        }
      }
    });
    return result;
  }
}