// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.ui

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.view.ExternalProjectsView
import com.intellij.openapi.externalSystem.view.ExternalSystemNode
import com.intellij.openapi.externalSystem.view.ExternalSystemViewContributor
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.nullize
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleViewContributor : ExternalSystemViewContributor() {
  override fun getSystemId() = GradleConstants.SYSTEM_ID

  override fun getKeys(): List<Key<*>> = emptyList()

  override fun createNodes(externalProjectsView: ExternalProjectsView,
                           dataNodes: MultiMap<Key<*>?, DataNode<*>?>?): List<ExternalSystemNode<*>> = emptyList()

  override fun getDisplayName(node: DataNode<*>): String? {
    val task = node.data as? TaskData ?: return null
    return task.name.run { substringAfterLast(':').nullize() ?: this }
  }
}