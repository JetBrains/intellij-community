/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger.frame;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class ExecutionStackImpl extends XExecutionStack {
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

    suspendContext.getFrames().done(new ContextDependentAsyncResultConsumer<CallFrame[]>(suspendContext) {
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
            if (i == 0) {
              result.add(topCallFrameView);
              continue;
            }

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