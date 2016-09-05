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
package org.jetbrains.plugins.groovy.compiler

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextUtil
import com.intellij.debugger.impl.DebuggerManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.Semaphore
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

@CompileStatic
trait DebuggerMethods extends CompilerMethods {

  private static final int ourTimeout = 60000

  abstract Logger getLogger()

  DebugProcessImpl getDebugProcess() {
    debugSession?.process
  }

  DebuggerSession getDebugSession() {
    DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
  }

  void runDebugger(RunProfile configuration, Closure cl) {
    runInEdtAndWait {
      def listener = [onTextAvailable: { ProcessEvent evt, type ->
        if (type == ProcessOutputTypes.STDERR) {
          println evt.text
        }
      }] as ProcessAdapter
      runConfiguration(DefaultDebugExecutor, listener, configuration)
    }
    logger.debug("after start")
    try {
      cl.call()
    }
    catch (Throwable t) {
      t.printStackTrace()
      throw t
    }
    finally {
      def handler = debugProcess.processHandler
      resume()
      if (!handler.waitFor(ourTimeout)) {
        if (handler instanceof OSProcessHandler) {
          OSProcessManager.instance.killProcessTree((handler as OSProcessHandler).process)
        }
        else {
          println "can't terminate $handler"
        }
        throw new AssertionError((Object)'too long waiting for process termination')
      }
    }
  }

  void addBreakpoint(VirtualFile file, int line) {
    runInEdtAndWait {
      DebuggerManagerImpl.getInstanceEx(project).breakpointManager.addLineBreakpoint(FileDocumentManager.instance.getDocument(file), line)
    }
  }

  SuspendContextImpl waitForBreakpoint() {
    logger.debug("waitForBreakpoint")
    Semaphore semaphore = new Semaphore()
    semaphore.down()
    def process = debugProcess
    // wait for all events processed
    process.managerThread.schedule {
      semaphore.up()
    }
    def finished = semaphore.waitFor(ourTimeout)
    assert finished: 'Too long debugger actions'

    int i = 0
    def suspendManager = process.suspendManager
    while (i++ < ourTimeout / 10 && !suspendManager.pausedContext && !process.processHandler.processTerminated) {
      Thread.sleep(10)
    }

    def context = suspendManager.pausedContext
    assert context: "too long process, terminated=${process.processHandler.processTerminated}"
    return context
  }

  void resume() {
    if (debugSession == null) return
    debugProcess.managerThread.invoke(debugProcess.createResumeCommand(debugProcess.suspendManager.pausedContext))
  }

  SourcePosition getSourcePosition() {
    managed {
      EvaluationContextImpl context = evaluationContext()
      def a = { ContextUtil.getSourcePosition(context) } as Computable<SourcePosition>
      ApplicationManager.getApplication().runReadAction(a)
    }
  }

  EvaluationContextImpl evaluationContext() {
    final SuspendContextImpl suspendContext = debugProcess.suspendManager.pausedContext
    new EvaluationContextImpl(suspendContext, suspendContext.frameProxy, suspendContext.frameProxy.thisObject())
  }

  void eval(final String codeText, String expected) throws EvaluateException {
    eval(codeText, expected, null)
  }

  void eval(final String codeText, String expected, FileType fileType) throws EvaluateException {
    Semaphore semaphore = new Semaphore()
    semaphore.down()

    EvaluationContextImpl ctx
    def item = new WatchItemDescriptor(project, new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText, "", fileType))
    managed {
      ctx = evaluationContext()
      item.setContext(ctx)
      item.updateRepresentation(ctx, {} as DescriptorLabelListener)
      semaphore.up()
    }
    assert semaphore.waitFor(ourTimeout): "too long evaluation: $item.label $item.evaluateException"

    String result = managed {
      def e = item.evaluateException
      if (e) {
        return ExceptionUtil.getThrowableText(e)
      }
      return DebuggerUtils.getValueAsString(ctx, item.value)
    }
    assert result == expected
  }

  def <T> T managed(Closure<T> cl) {
    def ctx = DebuggerContextUtil.createDebuggerContext(debugSession, debugProcess.suspendManager.pausedContext)
    def command = new ManagedCommand<T>(ctx, cl)
    debugProcess.managerThread.invoke(command)
    def finished = command.semaphore.waitFor(ourTimeout)
    assert finished: 'Too long debugger action'
    return command.result
  }

  private static class ManagedCommand<T> extends DebuggerContextCommandImpl {

    private final Semaphore mySemaphore
    private final Closure<T> myAction
    private T myResult

    ManagedCommand(@NotNull DebuggerContextImpl debuggerContext, Closure<T> action) {
      super(debuggerContext)
      mySemaphore = new Semaphore()
      mySemaphore.down()
      myAction = action
    }

    Semaphore getSemaphore() {
      mySemaphore
    }

    T getResult() {
      myResult
    }

    @Override
    void threadAction() {
      try {
        myResult = myAction()
      }
      finally {
        mySemaphore.up()
      }
    }

    @Override
    protected void commandCancelled() {
      println DebugUtil.currentStackTrace()
    }
  }
}