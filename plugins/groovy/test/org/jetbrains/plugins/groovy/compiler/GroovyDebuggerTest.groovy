/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.EvaluatingComputable
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerManagerImpl
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.debugger.impl.PositionUtil
import com.intellij.debugger.ui.DebuggerPanelsManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager

/**
 * @author peter
 */
class GroovyDebuggerTest extends GroovyCompilerTestCase {
  DebuggerManagerThreadImpl managerThread

  @Override
  protected void setUp() {
    edt {
      super.setUp()
      addGroovyLibrary(myModule);
    }

    GroovyPositionManager.registerPositionManager(project)
    managerThread = DebuggerManagerThreadImpl.createTestInstance()
  }

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) {
    runnable.run()
  }

  @Override
  protected void tearDown() {
    managerThread = null
    super.tearDown()
  }

  private void startDebugging() {
    edt {
      runProcess('Foo', myModule, DefaultDebugExecutor, GenericDebuggerRunner, [onTextAvailable:{ evt, type -> /*print evt.text*/}] as ProcessAdapter)
    }
  }

  public void testSimpleEvaluate() {
    def foo = myFixture.addFileToProject("Foo.groovy", "println 'hello'");
    make()

    edt {
      DebuggerManagerImpl.getInstanceEx(project).breakpointManager.addLineBreakpoint(foo.viewProvider.document, 0)
    }

    startDebugging()
    def context = waitForBreakpoint()
    assert eval(context, '2+2') == '4'
    resume()

    debugProcess.executionResult.processHandler.waitFor()
  }

  private def resume() {
    managerThread.invoke(debugProcess.createResumeCommand(debugProcess.suspendManager.pausedContext))
  }

  private SuspendContextImpl waitForBreakpoint() {
    int i = 0
    def suspendManager = debugProcess.suspendManager
    while (i++ < 1000 && !suspendManager.pausedContext && !debugProcess.executionResult.processHandler.processTerminated) {
      Thread.sleep(10)
    }

    def context = suspendManager.pausedContext
    assert context : 'too long process'
    return context
  }

  private DebugProcessImpl getDebugProcess() {
    return DebuggerPanelsManager.getInstance(project).sessionTab.session.process
  }

  private def managed(Closure cl) {
    def result = null
    managerThread.invokeAndWait(new DebuggerCommandImpl() {
                         @Override
                         protected void action() {
                           result = cl()
                         }
                         })
    return result
  }

  private String eval(final SuspendContextImpl suspendContext, final String codeText) throws EvaluateException {
    return managed({
      final ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, {
          final EvaluatorBuilder builder = EvaluatorBuilderImpl.getInstance();
          return builder.build(
            new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText),
            PositionUtil.getContextElement(suspendContext),
            ContextUtil.getSourcePosition(suspendContext)
          );
        } as EvaluatingComputable<ExpressionEvaluator>)
      return evaluator.evaluate(new EvaluationContextImpl(suspendContext, suspendContext.frameProxy, suspendContext.frameProxy.thisObject()))
    }) as String
  }


}
