package org.jetbrains.debugger.frame;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncFunction;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.*;
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

  @NotNull
  public Promise<String> evaluateExpression(@NotNull String expression) {
    CallFrameView frame = executionStack.getTopFrame();
    if (frame == null) {
      return Promise.reject("Top frame is null");
    }
    else {
      return evaluateExpression(frame.getCallFrame().getEvaluateContext(), expression);
    }
  }

  @NotNull
  private static Promise<String> evaluateExpression(@NotNull EvaluateContext evaluateContext, @NotNull String expression) {
    return evaluateContext.evaluate(expression).then(new AsyncFunction<EvaluateResult, String>() {
      @NotNull
      @Override
      public Promise<String> fun(EvaluateResult result) {
        Value value = result.value;
        if (value == null) {
          return Promise.resolve("Log expression result doesn't have value");
        }
        else {
          if (value instanceof StringValue && ((StringValue)value).isTruncated()) {
            return ((StringValue)value).getFullString();
          }
          else {
            return Promise.resolve(value.getValueString());
          }
        }
      }
    });
  }
}