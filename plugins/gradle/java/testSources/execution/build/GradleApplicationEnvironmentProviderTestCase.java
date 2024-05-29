// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.execution.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleSettingsImportingTestCase;
import org.junit.Assert;

/**
 * @author Vladislav.Soroka
 */
public abstract class GradleApplicationEnvironmentProviderTestCase extends GradleSettingsImportingTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getCurrentExternalProjectSettings().setDelegatedBuild(true);
  }

  void assertAppRunOutput(RunnerAndConfigurationSettings configurationSettings, String... checks) {
    String output = runAppAndGetOutput(configurationSettings);
    for (String check : checks) {
      assertTrue(String.format("App output should contain substring: %s, but was:\n%s", check, output), output.contains(check));
    }
  }

  private @NotNull String runAppAndGetOutput(RunnerAndConfigurationSettings configurationSettings) {
    final Semaphore done = new Semaphore();
    done.down();
    ExternalSystemProgressNotificationManager notificationManager =
      ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
    StringBuilder out = new StringBuilder();
    ExternalSystemTaskNotificationListener listener = new ExternalSystemTaskNotificationListener() {
      private volatile ExternalSystemTaskId myId = null;

      @Override
      public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        if (myId != null) {
          throw new IllegalStateException("This test listener is not supposed to listen to more than 1 task");
        }
        myId = id;
      }

      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        if (!id.equals(myId)) {
          throw new IllegalStateException("This test listener is not supposed to listen to more than 1 task");
        }
        if (StringUtil.isEmptyOrSpaces(text)) return;
        (stdOut ? System.out : System.err).print(text);
        out.append(text);
      }

      @Override
      public void onEnd(@NotNull ExternalSystemTaskId id) {
        if (!id.equals(myId)) {
          throw new IllegalStateException("This test listener is not supposed to listen to more than 1 task");
        }
        done.up();
      }
    };

    try {
      notificationManager.addNotificationListener(listener);
      // `waitForSmartMode` should be removed after IDEA-354120. Application run configurations should not rely on Smart-mode.
      DumbService.getInstance(myProject).waitForSmartMode();
      edt(() -> {
        try {
          ExecutionEnvironment environment =
            ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configurationSettings)
              .contentToReuse(null)
              .dataContext(null)
              .activeTarget()
              .build();
          ProgramRunnerUtil.executeConfiguration(environment, false, true);
        }
        catch (ExecutionException e) {
          fail(e.getMessage());
        }
      });
      Assert.assertTrue("Execution did not finish in 30 seconds. Flushing available build output:\n" + out,
                        done.waitFor(30000));
    }
    finally {
      notificationManager.removeNotificationListener(listener);
    }
    return out.toString();
  }
}