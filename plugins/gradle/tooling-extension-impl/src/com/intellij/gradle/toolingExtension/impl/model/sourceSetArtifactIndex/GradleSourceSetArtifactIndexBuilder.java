// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.sourceSetArtifactIndex;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil;
import com.intellij.gradle.toolingExtension.impl.util.javaPluginUtil.JavaPluginUtil;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.io.File;
import java.util.*;

public class GradleSourceSetArtifactIndexBuilder extends AbstractModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return GradleSourceSetArtifactBuildRequest.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    GradleSourceSetArtifactModel sourceSetArtifactModel = new GradleSourceSetArtifactModel();

    SourceSetContainer sourceSetContainer = JavaPluginUtil.getSourceSetContainer(project);
    if (sourceSetContainer != null && !sourceSetContainer.isEmpty()) {
      Map<String, SourceSet> sourceSetArtifactMap = new HashMap<>();
      Map<String, String> sourceSetOutputArtifactMap = new HashMap<>();
      for (SourceSet sourceSet : sourceSetContainer) {
        Task task = project.getTasks().findByName(sourceSet.getJarTaskName());
        if (task instanceof AbstractArchiveTask) {
          File archiveFile = GradleTaskUtil.getTaskArchiveFile((AbstractArchiveTask)task);
          sourceSetArtifactMap.put(archiveFile.getPath(), sourceSet);
          for (File file : sourceSet.getOutput().getClassesDirs().getFiles()) {
            sourceSetOutputArtifactMap.put(file.getPath(), archiveFile.getPath());
          }
          File resourcesDir = Objects.requireNonNull(sourceSet.getOutput().getResourcesDir());
          sourceSetOutputArtifactMap.put(resourcesDir.getPath(), archiveFile.getPath());
        }
      }
      sourceSetArtifactModel.setSourceSetArtifactMap(sourceSetArtifactMap);
      sourceSetArtifactModel.setSourceSetOutputArtifactMap(sourceSetOutputArtifactMap);
    }

    GradleSourceSetArtifactIndex.getInstance(context)
      .setSourceSetArtifactModel(project, sourceSetArtifactModel);

    return null;
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    GradleSourceSetArtifactIndex.getInstance(context)
      .markSourceSetArtifactModelAsError(project);

    context.getMessageReporter().createMessage()
      .withGroup(Messages.SOURCE_SET_ARTIFACT_INDEX_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Source set artifact index building failure")
      .withException(exception)
      .reportMessage(project);
  }
}

