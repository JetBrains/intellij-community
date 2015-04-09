/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.execution;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.execution.TaskCompletionProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextAccessor;
import com.intellij.util.containers.ContainerUtil;
import icons.ExternalSystemIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/25/2014
 */
public class GradleArgumentsCompletionProvider extends TaskCompletionProvider {

  public GradleArgumentsCompletionProvider(@NotNull Project project, @NotNull TextAccessor workDirectoryField) {
    super(project, GradleConstants.SYSTEM_ID, workDirectoryField, GradleCommandLineOptionsProvider.getSupportedOptions());
  }

  protected List<LookupElement> getVariants(@NotNull final DataNode<ProjectData> projectDataNode, @NotNull final String modulePath) {
    final DataNode<ModuleData> moduleDataNode = findModuleDataNode(projectDataNode, modulePath);
    if (moduleDataNode == null) {
      return Collections.emptyList();
    }

    final ModuleData moduleData = moduleDataNode.getData();
    final boolean isRoot = projectDataNode.getData().getLinkedExternalProjectPath().equals(moduleData.getLinkedExternalProjectPath());
    final Collection<DataNode<TaskData>> tasks = ExternalSystemApiUtil.getChildren(moduleDataNode, ProjectKeys.TASK);
    List<LookupElement> elements = ContainerUtil.newArrayListWithCapacity(tasks.size());

    for (DataNode<TaskData> taskDataNode : tasks) {
      final TaskData taskData = taskDataNode.getData();
      elements.add(LookupElementBuilder.create(taskData.getName()).withIcon(ExternalSystemIcons.Task));
      if (!taskData.isInherited()) {
        elements.add(LookupElementBuilder.create((isRoot ? ':' : moduleData.getId() + ':') + taskData.getName())
                       .withIcon(ExternalSystemIcons.Task));
      }
    }
    return elements;
  }
}
