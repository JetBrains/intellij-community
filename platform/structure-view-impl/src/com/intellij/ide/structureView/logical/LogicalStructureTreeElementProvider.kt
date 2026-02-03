// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Provides logical structure view for logical elements
 * It is supposed to apply when we want to reuse existing tree for some logical elements branch
 * By default it's better to provide [com.intellij.ide.presentation.PresentationProvider] for [T]
 */
@ApiStatus.Experimental
interface LogicalStructureTreeElementProvider<T> {

  companion object {
    fun <T> getTreeElement(model: T): StructureViewTreeElement? {
      for (provider in EP_NAME.extensionList) {
        if (!provider.getModelClass().isInstance(model)) continue
        val treeElement = (provider as? LogicalStructureTreeElementProvider<T>)?.getTreeElement(model)
        if (treeElement != null) return treeElement
      }
      return null
    }
  }

  fun getModelClass(): Class<T>

  fun getTreeElement(model: T): StructureViewTreeElement?

}

private const val EP_FQN = "com.intellij.lang.logicalStructureTreeElementProvider"

private val EP_NAME = ExtensionPointName.create<LogicalStructureTreeElementProvider<*>>(EP_FQN)
