// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.ide

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.TypedEntityStore
import java.util.*

object WorkspaceModelTopics {
  val CHANGED = Topic("Workspace Model Changed", WorkspaceModelChangeListener::class.java)
}

interface WorkspaceModelChangeListener : EventListener {
  fun beforeChanged(event: EntityStoreChanged) {}
  fun changed(event: EntityStoreChanged) {}
}

interface WorkspaceModel {
  val entityStore: TypedEntityStore

  fun <R> updateProjectModel(updater: (TypedEntityStorageBuilder) -> R): R

  companion object {
    @JvmStatic
    fun getInstance(project: Project): WorkspaceModel = ServiceManager.getService(project, WorkspaceModel::class.java)
  }
}
