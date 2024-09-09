// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.presentation.PresentationProvider
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ClassExtension

abstract class LogicalModelPresentationProvider<T>: PresentationProvider<T>() {

  companion object {
    private val PROVIDER_EP = ExtensionPointName<PresentationProvider<*>>("com.intellij.presentationProvider")
    private val PROVIDERS = ClassExtension<PresentationProvider<*>>(PROVIDER_EP.name)

    fun <T> getForObject(obj: T): LogicalModelPresentationProvider<T>? {
      return PROVIDERS.findSingle(obj!!::class.java) as? LogicalModelPresentationProvider<T>
    }
  }

  open fun isAutoExpand(t: T): Boolean = false

  open fun getColoredText(t: T): List<PresentableNodeDescriptor.ColoredFragment> = emptyList()

}