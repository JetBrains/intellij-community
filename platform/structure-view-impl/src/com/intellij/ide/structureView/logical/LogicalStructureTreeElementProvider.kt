// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.extensions.ExtensionPointName

interface LogicalStructureTreeElementProvider<T> {

  companion object {
    private const val EP_FQN = "com.intellij.lang.logicalStructureTreeElementProvider"

    val EP_NAME = ExtensionPointName.create<LogicalStructureTreeElementProvider<*>>(EP_FQN)

    fun <T> getTreeElement(model: T): StructureViewTreeElement? {
      val provider = EP_NAME.extensionList
        .firstOrNull { it.getModelClass().isInstance(model) } as? LogicalStructureTreeElementProvider<T>
      return provider?.getTreeElement(model)
    }
  }

  fun getModelClass(): Class<T>

  fun getTreeElement(model: T): StructureViewTreeElement

}
