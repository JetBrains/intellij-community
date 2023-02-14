package com.intellij.workspaceModel.test.api

import com.intellij.workspaceModel.deft.api.annotations.Default
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type


interface FinalFieldsEntity : WorkspaceEntity {
  val descriptor: AnotherDataClass

  val version: Int get() = descriptor.version
  val source: Boolean get() = descriptor.source
  val displayName: String? get() = descriptor.displayName
  val gitUrl: String? get() = descriptor.url
  val gitRevision: String? get() = descriptor.revision

  fun isEditable(): Boolean {
    return descriptor.source && displayName != null
  }

  fun isReadOnly(): Boolean {
    return !isEditable() && descriptor.url != null
  }
}

data class AnotherDataClass(val name: String,
                            val version: Int,
                            val source: Boolean,
                            val displayName: String? = null,
                            val url: String? = null,
                            val revision: String? = null)