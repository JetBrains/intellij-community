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
  private final SuspendContext suspendContext;
  private final Script topFrameScript;
  private CallFrameView topCallFrameView;
  private final DebuggerViewSupport debugProcess;

  public ExecutionStackImpl(@NotNull SuspendContext suspendContext, @NotNull DebuggerViewSupport debugProcess, @Nullable Script topFrameScript) {
    super("");

    this.debugProcess = debugProcess;
    this.suspendContext = suspendContext;
    this.topFrameScript = topFrameScript;
  }

  @Override
  @Nullable
  public CallFrameView getTopFrame() {
    CallFrame topCallFrame = suspendContext.getTopFrame();
    if (topCallFrameView == null || topCallFrameView.getCallFrame() != topCallFrame) {
      topCallFrameView = topCallFrame == null ? null : new CallFrameView(topCallFrame, debugProcess, topFrameScript);
    }
    return topCallFrameView;
  }

  @Override
  public void computeStackFrames(final int firstFrameIndex, final XStackFrameContainer container) {
    SuspendContext suspendContext = debugProcess.getVm().getSuspendContextManager().getContext();
    // WipSuspendContextManager set context to null on resume _before_ vm.getDebugListener().resumed() call() (in any case, XFramesView can queue event to EDT), so, IDE state could be outdated compare to VM (our) state
    if (suspendContext == null) {
      return;
    }

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