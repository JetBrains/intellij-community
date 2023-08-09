package org.jetbrains.plugins.gradle.performanceTesting;

import com.intellij.openapi.externalSystem.action.ExternalSystemActionUtil;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Objects;

/**
 * The command executes a gradle task by name
 * Syntax: %executeGradleTask [task name]
 * Example: %executeGradleTask my-task
 */
public final class ExecuteGradleTaskCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "executeGradleTask";

  public ExecuteGradleTaskCommand(@NotNull String command, int line) {
    super(command, line);
  }

  @NotNull
  @Override
  protected Promise<Object> _execute(@NotNull PlaybackContext context) {
    String taskName = extractCommandArgument(PREFIX).split(" ")[0];
    ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();
    TaskData task = new TaskData(GradleConstants.SYSTEM_ID, taskName, Objects.requireNonNull(project.getBasePath()), "Description");
    runTask(project, task, actionCallback);
    return Promises.toPromise(actionCallback);
  }

  private static void runTask(@NotNull Project project,
                              @NotNull TaskData taskData,
                              @NotNull ActionCallback actionCallback) {
    final ExternalTaskExecutionInfo taskExecutionInfo = ExternalSystemActionUtil.buildTaskInfo(taskData);
    TaskCallback callback = new TaskCallback() {
      @Override
      public void onSuccess() {
        actionCallback.setDone();
      }

      @Override
      public void onFailure() {
        actionCallback.setRejected();
      }
    };

    ExternalSystemUtil.runTask(taskExecutionInfo.getSettings(), taskExecutionInfo.getExecutorId(), project, GradleConstants.SYSTEM_ID,
                               callback,
                               ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }
}
