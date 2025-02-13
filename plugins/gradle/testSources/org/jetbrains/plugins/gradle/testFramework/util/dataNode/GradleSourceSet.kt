// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util.dataNode

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.platform.externalSystem.testFramework.Module
import com.intellij.platform.externalSystem.testFramework.NamedNode
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

class GradleSourceSet : NamedNode<GradleSourceSetData>("sourceSet") {

  var sourceSetName: String
    get() = props["sourceSetName"]!!
    set(value) {
      props["sourceSetName"] = value
    }

  override fun createDataNode(parentData: Any?): DataNode<GradleSourceSetData> {
    val moduleData = parentData as ModuleData
    val data = GradleSourceSetData(
      moduleData.id + ":" + sourceSetName,
      moduleData.externalName + ":" + sourceSetName,
      moduleData.internalName + "." + sourceSetName,
      moduleData.moduleFileDirectoryPath,
      moduleData.linkedExternalProjectPath
    )
    return DataNode(GradleSourceSetData.KEY, data, null)
  }

  companion object {

    fun Module.gradleSourceSet(name: String) {
      initChild(GradleSourceSet()) {
        this.sourceSetName = name
      }
    }
  }
}