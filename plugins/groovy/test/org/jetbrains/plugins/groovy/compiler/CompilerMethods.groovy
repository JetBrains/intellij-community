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

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.Semaphore
import groovy.transform.CompileStatic

import java.util.concurrent.atomic.AtomicReference

@CompileStatic
trait CompilerMethods {

  abstract Project getProject()

  abstract Disposable disposeOnTearDown(Disposable disposable)

  ProcessHandler runConfiguration(Class<? extends Executor> executorClass,
                                  final ProcessListener listener,
                                  RunProfile configuration,
                                  ProgramRunner runner = null) throws ExecutionException {
    final Executor executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass)
    def builder = new ExecutionEnvironmentBuilder(getProject(), executor).runProfile(configuration)
    if (runner) builder.runner(runner)

    final ExecutionEnvironment environment = builder.build()
    final Semaphore semaphore = new Semaphore()
    semaphore.down()

    final AtomicReference<ProcessHandler> processHandler = new AtomicReference<ProcessHandler>()
    environment.runner.execute(environment) { RunContentDescriptor descriptor ->
      if (descriptor == null) {
        throw new AssertionError((Object)"Null descriptor!")
      }
      disposeOnTearDown descriptor
      final ProcessHandler handler = descriptor.getProcessHandler()
      assert handler != null
      handler.addProcessListener(listener)
      processHandler.set(handler)
      semaphore.up()
    }
    if (!semaphore.waitFor(20000)) {
      throw new AssertionError((Object)"Process took too long")
    }
    return processHandler.get()
  }
}