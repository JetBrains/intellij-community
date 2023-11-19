// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.model.taskModel.GradleTaskCache;
import com.intellij.gradle.toolingExtension.util.GradleNegotiationUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.jar.JarTaskManifestConfiguration;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.jar.JarTaskManifestConfigurationImpl;

import java.util.HashMap;
import java.util.Map;

public class JarTaskManifestModelBuilder extends AbstractModelBuilderService {
  public static final String JAR_TASK = "jar";

  @Override
  public boolean canBuild(String modelName) {
    return JarTaskManifestConfiguration.class.getName().equals(modelName);
  }

  @Override
  public JarTaskManifestConfigurationImpl buildAll(@NotNull String modelName,
                                                   @NotNull Project project,
                                                   @NotNull ModelBuilderContext context) {
    GradleTaskCache taskCache = GradleTaskCache.getInstance(context);
    Map<String, String> projectIdentityPathToModuleName = new HashMap<>();
    for (Task task : taskCache.getAllTasks(project)) {
      if (task instanceof Jar && JAR_TASK.equals(task.getName())) {
        Jar jar = (Jar)task;
        if (!jar.getManifest().getAttributes().isEmpty()) {
          // TODO: all manifest attributes
          projectIdentityPathToModuleName.put(
            identityPath(project),
            (String)jar.getManifest().getAttributes().get("Automatic-Module-Name"));
        }
      }
    }
    return new JarTaskManifestConfigurationImpl(projectIdentityPathToModuleName);
  }

  private static String identityPath(Project project) {
    // composite builds
    String identityPath = GradleNegotiationUtil.getProjectIdentityPath(project);
    return (identityPath == null || ":".equals(identityPath)) ? project.getPath() : identityPath;
  }
}