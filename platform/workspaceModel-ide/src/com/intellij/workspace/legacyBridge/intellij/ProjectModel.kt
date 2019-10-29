package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.workspace.api.EntityStoreChanged
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.api.TypedEntityStore
import com.intellij.util.messages.Topic
import java.util.*

object ProjectModelTopics {
  val CHANGED = Topic("Project Model Changed", ProjectModelChangeListener::class.java)
}

interface ProjectModelChangeListener : EventListener {
  fun beforeChanged(event: EntityStoreChanged) {}
  fun changed(event: EntityStoreChanged) {}
}

interface ProjectModel {
  val entityStore: TypedEntityStore

  fun <R> updateProjectModel(updater: (TypedEntityStorageBuilder) -> R): R

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectModel = ServiceManager.getService(project, ProjectModel::class.java)
  }
}
