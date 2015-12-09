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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.console.DuplexConsoleView;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEventUnsupported;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 12/4/2015
 */
public class GradleRunnerUtil {

  public static DuplexConsoleView attachTaskExecutionView(@NotNull final Project project,
                                                          @NotNull final ConsoleView consoleView,
                                                          final boolean isTaskConsoleEnabledByDefault,
                                                          @Nullable final String stateStorageKey,
                                                          @NotNull final ProcessHandler processHandler,
                                                          @NotNull final ExternalSystemTaskId taskId) {
    final String tripleStateStorageKey = stateStorageKey != null ? stateStorageKey + "_str" : null;
    if (stateStorageKey != null && isTaskConsoleEnabledByDefault && !PropertiesComponent.getInstance().isValueSet(tripleStateStorageKey)) {
      PropertiesComponent.getInstance().setValue(tripleStateStorageKey, Boolean.TRUE.toString());
      PropertiesComponent.getInstance().setValue(stateStorageKey, Boolean.TRUE);
    }

    final TaskExecutionView gradleExecutionConsole = new TaskExecutionView(project);
    final Ref<DuplexConsoleView> duplexConsoleViewRef = Ref.create();
    final DuplexConsoleView duplexConsoleView =
      new DuplexConsoleView<ConsoleView, ConsoleView>(gradleExecutionConsole, consoleView, stateStorageKey) {

        @Override
        public void enableConsole(boolean primary) {
          super.enableConsole(primary);
          if (stateStorageKey != null) {
            PropertiesComponent.getInstance().setValue(tripleStateStorageKey, Boolean.toString(primary));
          }
        }

        @NotNull
        @Override
        public AnAction[] createConsoleActions() {

          final DefaultActionGroup textActionGroup = new DefaultActionGroup() {
            @Override
            public void update(AnActionEvent e) {
              super.update(e);
              if (duplexConsoleViewRef.get() != null) {
                e.getPresentation().setVisible(!duplexConsoleViewRef.get().isPrimaryConsoleEnabled());
              }
            }
          };
          final AnAction[] consoleActions = consoleView.createConsoleActions();
          for (AnAction anAction : consoleActions) {
            textActionGroup.add(anAction);
          }

          final List<AnAction> anActions = ContainerUtil.newArrayList(super.createConsoleActions());
          anActions.add(textActionGroup);
          return ArrayUtil.toObjectArray(anActions, AnAction.class);
        }
      };

    duplexConsoleViewRef.set(duplexConsoleView);

    duplexConsoleView.setDisableSwitchConsoleActionOnProcessEnd(false);
    duplexConsoleView.getSwitchConsoleActionPresentation().setIcon(AllIcons.Actions.ChangeView);
    duplexConsoleView.getSwitchConsoleActionPresentation().setText(GradleBundle.message("gradle.runner.toggle.tree.text.action.name"));

    final ExternalSystemProgressNotificationManager progressManager =
      ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    final ExternalSystemTaskNotificationListenerAdapter taskListener = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull final ExternalSystemTaskNotificationEvent event) {
        if (event instanceof ExternalSystemTaskExecutionEvent) {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              if(((ExternalSystemTaskExecutionEvent)event).getProgressEvent() instanceof ExternalSystemProgressEventUnsupported) {
                duplexConsoleView.enableConsole(false);
              }
              gradleExecutionConsole.onStatusChange((ExternalSystemTaskExecutionEvent)event);
            }
          });
        }
      }
    };
    progressManager.addNotificationListener(taskId, taskListener);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        progressManager.removeNotificationListener(taskListener);
      }
    });

    return duplexConsoleView;
  }
}
