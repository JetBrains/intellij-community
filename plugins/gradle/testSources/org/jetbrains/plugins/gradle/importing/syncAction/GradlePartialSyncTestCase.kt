// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import org.jetbrains.plugins.gradle.util.GradleConstants.SYSTEM_ID

abstract class GradlePartialSyncTestCase : GradleProjectResolverTestCase() {

  fun importProject(importSpec: ImportSpecBuilder.() -> Unit) {
    val importSpecBuilder = ImportSpecBuilder(myProject, SYSTEM_ID)
    importSpecBuilder.importSpec()
    importSpecBuilder.use(ProgressExecutionMode.MODAL_SYNC)
    ExternalSystemUtil.refreshProject(projectPath, importSpecBuilder)
  }

  fun getProjectDataStructure(): DataNode<ProjectData> {
    return ExternalSystemApiUtil.findProjectNode(myProject, SYSTEM_ID, projectPath)!!
  }

  class TestPartialProjectResolverExtension : AbstractTestProjectResolverExtension() {
    override val serviceClass = TestPartialProjectResolverService::class.java
  }

  class TestPartialProjectResolverService : AbstractTestProjectResolverService()
}