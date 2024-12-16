// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.util.concurrency.Semaphore;
import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicReference;

public interface CompilerMethods {
  Project getProject();

  Disposable disposeOnTearDown(Disposable disposable);

  default ProcessHandler runConfiguration(Class<? extends Executor> executorClass,
                                  final ProcessListener listener,
                                  RunProfile configuration,
                                  ProgramRunner runner) throws ExecutionException {
    final Executor executor = Extensions.findExtension(Executor.EXECUTOR_EXTENSION_NAME, executorClass);
    ExecutionEnvironmentBuilder builder = new ExecutionEnvironmentBuilder(getProject(), executor).runProfile(configuration);
    if (runner != null) {
      builder.runner(runner);
    }

    Semaphore semaphore = new Semaphore();
    semaphore.down();
    AtomicReference<ProcessHandler> processHandler = new AtomicReference<>();

    ExecutionEnvironment environment = builder.build(descriptor -> {
      if (descriptor == null) {
        throw new AssertionError("Null descriptor!");
      }
      disposeOnTearDown(descriptor);
      final ProcessHandler handler = descriptor.getProcessHandler();
      TestCase.assertNotNull(handler);
      handler.addProcessListener(listener);
      processHandler.set(handler);
      semaphore.up();
    });

    EdtTestUtil.runInEdtAndWait(() -> environment.getRunner().execute(environment));
    if (!semaphore.waitFor(20000)) {
      throw new AssertionError("Process took too long");
    }
    return processHandler.get();
  }
}