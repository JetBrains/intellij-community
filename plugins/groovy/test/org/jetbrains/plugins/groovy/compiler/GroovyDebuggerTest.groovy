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

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextUtil
import com.intellij.debugger.impl.DebuggerManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.debugger.ui.DebuggerPanelsManager
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager

/**
 * @author peter
 */
class GroovyDebuggerTest extends GroovyCompilerTestCase {

  @Override
  protected void setUp() {
    edt {
      super.setUp()
      addGroovyLibrary(myModule);
    }

    GroovyPositionManager.registerPositionManager(project)
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
    super.tearDown()
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addJdk(StringUtil.getPackageName(FileUtil.toSystemIndependentName(SystemProperties.javaHome), '/' as char))
  }

  private void runDebugger(Closure cl) {
    edt {
      runProcess('Foo', myModule, DefaultDebugExecutor, GenericDebuggerRunner, [onTextAvailable:{ evt, type -> /*print evt.text*/}] as ProcessAdapter)
    }
    cl.call()
    resume()
    debugProcess.executionResult.processHandler.waitFor()
  }

  public void testSimpleEvaluate() {
    def foo = myFixture.addFileToProject("Foo.groovy", "println 'hello'");
    make()

    edt {
      DebuggerManagerImpl.getInstanceEx(project).breakpointManager.addLineBreakpoint(foo.viewProvider.document, 0)
    }

    runDebugger {
      waitForBreakpoint()
      eval '2?:3', '2'
      eval 'null?:3', '3'
    }
  }

  private def resume() {
    debugProcess.managerThread.invoke(debugProcess.createResumeCommand(debugProcess.suspendManager.pausedContext))
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
    return getDebugSession().process
  }

  private DebuggerSession getDebugSession() {
    return DebuggerPanelsManager.getInstance(project).sessionTab.session
  }

  private def managed(Closure cl) {
    def result = null
    def ctx = DebuggerContextUtil.createDebuggerContext(debugSession, debugProcess.suspendManager.pausedContext)
    debugProcess.managerThread.invokeAndWait(new DebuggerContextCommandImpl(ctx) {
                                             @Override
                                             void threadAction() {
                                               result = cl()
                                             }
                                             })
    return result
  }

  private String eval(final String codeText, String expected) throws EvaluateException {
    final SuspendContextImpl suspendContext = debugProcess.suspendManager.pausedContext

    Semaphore semaphore = new Semaphore()
    semaphore.down()
    semaphore.down()

    EvaluationContextImpl ctx
    def item = new WatchItemDescriptor(project, new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText))
    managed {
      ctx = new EvaluationContextImpl(suspendContext, suspendContext.frameProxy, suspendContext.frameProxy.thisObject())
      item.setContext(ctx)
      item.updateRepresentation(ctx, { semaphore.up() } as DescriptorLabelListener)
    }
    semaphore.waitFor()

    String result = managed { DebuggerUtils.getValueAsString(ctx, item.value) }
    assert result == expected
  }


}
