// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.project.ExternalSystemOperationDescriptor
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.gradle.importing.syncAction.GradleProjectResolverTestCase
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID
import org.junit.Test

class GradleOperationDescriptorPropagationTest : GradleProjectResolverTestCase() {

  @Test
  fun testProjectImport() {
    val externalSystemTaskId = Ref<ExternalSystemTaskId>()
    whenModelFetchCompleted(testRootDisposable) { resolverContext ->
      externalSystemTaskId.set(resolverContext.externalSystemTaskId)
    }

    createSettingsFile("")
    importProject()

    val projectNode = ExternalSystemApiUtil.findProjectNode(myProject, SYSTEM_ID, projectPath)!!
    val operationDescriptorNode = ExternalSystemApiUtil.find(projectNode, ExternalSystemOperationDescriptor.OPERATION_DESCRIPTOR_KEY)!!
    val operationId = operationDescriptorNode.data.activityId
    val expectedOperationId = externalSystemTaskId.get().id
    assertEquals(expectedOperationId, operationId)
  }
}
