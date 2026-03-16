// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default

interface FinalFieldsEntity: WorkspaceEntity {
  val descriptor: AnotherDataClass
  val description: String
    @Default get() = "Default description"
  val anotherVersion: Int
    @Default get() = 0

  val version: Int get() = descriptor.version
  val source: Boolean get() = descriptor.source
  val displayName: String? get() = descriptor.displayName
  val gitUrl: String? get() = descriptor.url
  val gitRevision: String?  get() = descriptor.revision

  fun isEditable(): Boolean {
    return descriptor.source && displayName != null
  }
  fun isReadOnly(): Boolean {
    return !isEditable() && descriptor.url != null
  }

}

data class AnotherDataClass(val name: String, val version: Int, val source: Boolean, val displayName: String? = null, val url: String? = null,
                            val revision: String? = null)