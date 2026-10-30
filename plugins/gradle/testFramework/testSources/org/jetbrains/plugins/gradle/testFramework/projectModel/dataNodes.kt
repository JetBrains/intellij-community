// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ModuleSdkData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.project.ProjectSdkData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData


val DataNode<ProjectData>.moduleNodes: Collection<DataNode<ModuleData>>
  get() = ExternalSystemApiUtil.getChildren(this, ProjectKeys.MODULE)

val DataNode<ModuleData>.sourceSetNodes: Collection<DataNode<GradleSourceSetData>>
  get() = ExternalSystemApiUtil.getChildren(this, GradleSourceSetData.KEY)

val DataNode<ProjectData>.projectSdkNode: DataNode<ProjectSdkData>?
  get() = ExternalSystemApiUtil.find(this, ProjectSdkData.KEY)

val DataNode<ModuleData>.moduleSdkNode: DataNode<ModuleSdkData>?
  get() = ExternalSystemApiUtil.find(this, ModuleSdkData.KEY)