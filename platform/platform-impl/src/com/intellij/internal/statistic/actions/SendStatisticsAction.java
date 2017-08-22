/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.statistic.actions;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.StatisticsResult;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.ApplicationStatisticsPersistenceComponent;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class SendStatisticsAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Collecting And Sending Statistics", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        UsageStatisticsPersistenceComponent statisticsPersistenceComponent = UsageStatisticsPersistenceComponent.getInstance();
        boolean sendAllowed = statisticsPersistenceComponent.isAllowed();
        statisticsPersistenceComponent.setAllowed(true);
        ApplicationStatisticsPersistenceComponent.persistOpenedProjects();
        statisticsPersistenceComponent.setAllowed(sendAllowed);
        StatisticsService service = StatisticsUploadAssistant.getStatisticsService();
        final StatisticsResult result = service.send();

        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMultilineInputDialog(project, "Result: " + result.getCode(), "Statistics Result",
                                                StringUtil.replace(result.getDescription(), ";", "\n"),
                                                null, null), ModalityState.NON_MODAL, project.getDisposed());
      }
    });
  }
}
