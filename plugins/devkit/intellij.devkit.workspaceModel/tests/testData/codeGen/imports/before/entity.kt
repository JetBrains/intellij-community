// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId // stoopid and unnecessary
import java.net.URL // stoopid but necessary (user-added)
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

interface EntityWithManyImports : WorkspaceEntityWithSymbolicId {
  val version: Int
  val name: String
  val files: List<@Child SimpleEntity>
  val pointer: EntityPointer<SimpleEntity>

  override val symbolicId: SimpleId
    get() = SimpleId(name)
}

data class SimpleId(val name: String) : SymbolicEntityId<EntityWithManyImports> {
  override val presentableName: String
    get() = name
}

interface SimpleEntity : WorkspaceEntity {
  val url: VirtualFileUrl
  val parent: EntityWithManyImports
}

data class UnrelatedToEntities(val name: String, val data: EntityPointer<SimpleEntity>) {
  fun doSomething(src: EntitySource) {
  }
}
