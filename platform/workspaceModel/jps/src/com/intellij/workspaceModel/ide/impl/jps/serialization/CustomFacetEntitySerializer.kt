// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface CustomFacetEntitySerializer<T> {
  val entityType: Class<T>
  fun serialize(rootElement: Element, entity: T): Element

  companion object {
    val EP_NAME: ExtensionPointName<CustomFacetEntitySerializer<WorkspaceEntity>> = ExtensionPointName.create("com.intellij.workspaceModel.customFacetEntitySerializer")
  }
}