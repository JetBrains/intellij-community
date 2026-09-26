// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PackagingElementEntityModifications")

package com.intellij.java.workspace.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder

@GeneratedCodeApiVersion(3)
interface PackagingElementEntityBuilder<T : PackagingElementEntity> : WorkspaceEntityBuilder<T> {
  override var entitySource: EntitySource
  var parentEntity: CompositePackagingElementEntityBuilder<out CompositePackagingElementEntity>?
}
