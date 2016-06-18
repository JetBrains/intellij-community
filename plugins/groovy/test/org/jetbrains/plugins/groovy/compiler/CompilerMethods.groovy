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
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.Semaphore
import groovy.transform.CompileStatic

import java.util.concurrent.atomic.AtomicReference

@CompileStatic
trait CompilerMethods {

  abstract Project getProject()

  abstract <T extends Disposable> T disposeOnTearDown(T disposable)

  ProcessHandler runConfiguration(Class<? extends Executor> executorClass,
                                  final ProcessListener listener,
                                  ProgramRunner runner,
                                  RunProfile configuration) throws ExecutionException {
    final Executor executor = Executor.EXECUTOR_EXTENSION_NAME.findExtension(executorClass);
    final ExecutionEnvironment environment = new ExecutionEnvironmentBuilder(getProject(), executor).runProfile(configuration).build();
    final Semaphore semaphore = new Semaphore();
    semaphore.down();

    final AtomicReference<ProcessHandler> processHandler = new AtomicReference<ProcessHandler>();
    runner.execute(environment, { RunContentDescriptor descriptor ->
      if (descriptor == null) {
        throw new AssertionError((Object)"Null descriptor!");
      }
      disposeOnTearDown({ Disposer.dispose(descriptor) } as Disposable);
      final ProcessHandler handler = descriptor.getProcessHandler();
      assert handler != null;
      handler.addProcessListener(listener);
      processHandler.set(handler);
      semaphore.up();
    });
    if (!semaphore.waitFor(20000)) {
      throw new AssertionError((Object)"Process took too long")
    }
    return processHandler.get();
  }
}