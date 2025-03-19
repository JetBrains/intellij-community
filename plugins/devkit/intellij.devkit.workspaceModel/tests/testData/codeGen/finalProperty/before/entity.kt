package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.ModifiableWorkspaceEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage


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