// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

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
    if (runner) {
      builder.runner(runner)
    }

    Semaphore semaphore = new Semaphore()
    semaphore.down()
    AtomicReference<ProcessHandler> processHandler = new AtomicReference<ProcessHandler>()

    ExecutionEnvironment environment = builder.build({ RunContentDescriptor descriptor ->
          if (descriptor == null) {
            throw new AssertionError((Object)"Null descriptor!")
          }
          disposeOnTearDown(descriptor)
          final ProcessHandler handler = descriptor.getProcessHandler()
          assert handler != null
          handler.addProcessListener(listener)
          processHandler.set(handler)
          semaphore.up()
        })

    runInEdtAndWait {
      environment.runner.execute(environment)
    }
    if (!semaphore.waitFor(20000)) {
      throw new AssertionError((Object)"Process took too long")
    }
    return processHandler.get()
  }
}