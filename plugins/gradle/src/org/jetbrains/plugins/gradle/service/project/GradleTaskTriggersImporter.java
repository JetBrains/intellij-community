// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GradleTaskTriggersImporter implements ConfigurationHandler {

  private static final Map<String, ExternalSystemTaskActivator.Phase> PHASE_MAP;

  static  {
    PHASE_MAP = new HashMap<>();
    PHASE_MAP.put("beforeSync", ExternalSystemTaskActivator.Phase.BEFORE_SYNC);
    PHASE_MAP.put("afterSync", ExternalSystemTaskActivator.Phase.AFTER_SYNC);
    PHASE_MAP.put("beforeBuild", ExternalSystemTaskActivator.Phase.BEFORE_COMPILE);
    PHASE_MAP.put("afterBuild", ExternalSystemTaskActivator.Phase.AFTER_COMPILE);
    PHASE_MAP.put("beforeRebuild", ExternalSystemTaskActivator.Phase.BEFORE_REBUILD);
    PHASE_MAP.put("afterRebuild", ExternalSystemTaskActivator.Phase.AFTER_REBUILD);
  }

  @Override
  public void apply(@NotNull Project project,
                    @NotNull IdeModifiableModelsProvider modelsProvider,
                    @NotNull ConfigurationData configuration) {
    Object obj = configuration.find("taskTriggersConfig");
    if (!(obj instanceof Map)) {
      return;
    }

    final Map<String, Collection> taskTriggerConfig = (Map<String, Collection>)obj;
    final ExternalSystemTaskActivator activator = ExternalProjectsManagerImpl.getInstance(project).getTaskActivator();
    taskTriggerConfig.forEach((phaseName, tasks) -> {
      final ExternalSystemTaskActivator.Phase phase = PHASE_MAP.get(phaseName);
      ((Collection<Map>)tasks).forEach(taskInfo -> {
        final Object projectPath = taskInfo.get("projectPath");
        final Object taskPath = taskInfo.get("taskPath");
        if (projectPath instanceof String
        && taskPath instanceof String) {
          activator.addTask(new ExternalSystemTaskActivator.TaskActivationEntry(GradleConstants.SYSTEM_ID,
                                                                                phase,
                                                                                (String)projectPath,
                                                                                (String)taskPath));
        }
      });
    });
  }
}
