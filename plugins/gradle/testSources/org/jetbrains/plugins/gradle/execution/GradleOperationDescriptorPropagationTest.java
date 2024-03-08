// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemOperationDescriptor;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.jetbrains.plugins.gradle.service.project.ModifiableGradleProjectModel;
import org.jetbrains.plugins.gradle.service.project.ProjectModelContributor;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;

public class GradleOperationDescriptorPropagationTest extends GradleImportingTestCase {

  @Test
  public void testProjectImport() throws IOException {
    ExternalSystemTaskIdRevealingModelContributor taskIdRevealingModelContributor = new ExternalSystemTaskIdRevealingModelContributor();
    ProjectModelContributor.EP_NAME.getPoint()
      .registerExtension(taskIdRevealingModelContributor, getTestRootDisposable());

    createSettingsFile("");
    importProject();

    ProjectDataManager manager = ProjectDataManager.getInstance();
    final Collection<ExternalProjectInfo> data = manager.getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID);

    assertSize(1, data);

    final DataNode<ProjectData> rootNode = data.iterator().next().getExternalProjectStructure();
    final DataNode<ExternalSystemOperationDescriptor> operationDescriptorNode =
      ExternalSystemApiUtil.find(rootNode, ExternalSystemOperationDescriptor.OPERATION_DESCRIPTOR_KEY);

    assertNotNull(operationDescriptorNode);

    long operationId = operationDescriptorNode.getData().getActivityId();
    long expectedOperationId = taskIdRevealingModelContributor.getExternalSystemTaskId().getId();
    assertEquals(expectedOperationId, operationId);
  }

  private static class ExternalSystemTaskIdRevealingModelContributor implements ProjectModelContributor {

    private ExternalSystemTaskId myExternalSystemTaskId;

    @Override
    public void accept(
      @NotNull ModifiableGradleProjectModel modifiableGradleProjectModel,
      @NotNull ProjectResolverContext resolverContext
    ) {
      myExternalSystemTaskId = resolverContext.getExternalSystemTaskId();
    }

    private ExternalSystemTaskId getExternalSystemTaskId() {
      return myExternalSystemTaskId;
    }
  }
}
