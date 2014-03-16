package org.jetbrains.debugger.frame;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecutionStackImpl extends XExecutionStack {
  private final CallFrameView topFrame;
  private final DebuggerViewSupport debugProcess;

  public ExecutionStackImpl(@NotNull SuspendContext suspendContext, @NotNull DebuggerViewSupport debugProcess, @Nullable Script script) {
    super("");

    this.debugProcess = debugProcess;
    topFrame = suspendContext.getTopFrame() == null ? null : new CallFrameView(suspendContext.getTopFrame(), debugProcess, script);
  }

  @Override
  @Nullable
  public CallFrameView getTopFrame() {
    return topFrame;
  }

  @Override
  public void computeStackFrames(final int firstFrameIndex, final XStackFrameContainer container) {
    SuspendContext suspendContext = debugProcess.getVm().getSuspendContextManager().getContextOrFail();
    suspendContext.getCallFrames().doWhenDone(new ContextDependentAsyncResultConsumer<CallFrame[]>(suspendContext) {
      @Override
      protected void consume(CallFrame[] frames, @NotNull Vm vm) {
        int count = frames.length - firstFrameIndex;
        List<XStackFrame> result;
        if (count < 1) {
          result = Collections.emptyList();
        }
        else {
          result = new ArrayList<XStackFrame>(count);
          for (int i = firstFrameIndex; i < frames.length; i++) {
            CallFrame frame = frames[i];
            // if script is null, it is native function (Object.forEach for example), so, skip it
            Script script = vm.getScriptManager().getScript(frame);
            if (script != null) {
              result.add(new CallFrameView(frame, debugProcess, script));
            }
          }
        }
        container.addStackFrames(result, true);
      }
    });
  }
}