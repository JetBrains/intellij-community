// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.deft.api.annotations.Ignore
import com.intellij.workspaceModel.storage.WorkspaceEntity


interface IgnoredFieldsEntity: WorkspaceEntity {
  val descriptor: AnotherDataClass
  val description: String get() = "Default description"
  val anotherVersion: Int get() {
    return 0
  }

  @Ignore val version: Int get() = descriptor.version
  @Ignore val source: Boolean get() = descriptor.source
  @Ignore val displayName: String? get() = descriptor.displayName
  @Ignore val gitUrl: String? get() = descriptor.url
  @Ignore val gitRevision: String?  get() = descriptor.revision

  fun isEditable(): Boolean {
    return descriptor.source && displayName != null && displayName == "AnotherData"
  }
  fun isReadOnly(): Boolean {
    return !isEditable() && descriptor.url != null
  }
}


data class AnotherDataClass(val name: String, val version: Int, val source: Boolean, val displayName: String?, val url: String?, val revision: String?)