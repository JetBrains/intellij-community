// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.presentation.PresentationProvider
import com.intellij.ide.structureView.logical.ContainerElementsProvider
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ClassExtension
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class LogicalContainerPresentationProvider<T: ContainerElementsProvider<*, *>>: PresentationProvider<T>() {

  companion object {
    private val PROVIDER_EP = ExtensionPointName<PresentationProvider<*>>("com.intellij.presentationProvider")
    private val PROVIDERS = ClassExtension<PresentationProvider<*>>(PROVIDER_EP.name)

    fun <T: ContainerElementsProvider<*, *>> getForObject(obj: T): LogicalContainerPresentationProvider<T>? {
      return PROVIDERS.findSingle(obj::class.java) as? LogicalContainerPresentationProvider<T>
    }
  }

  /**
   * If true, then elements will be shown not grouped but right under the parent model node
   */
  open fun isFlatElements(): Boolean = false

  /**
   * Allows to customise representation for the group's node
   * t - is a parent model object for the group
   */
  open fun getColoredText(t: Any): List<PresentableNodeDescriptor.ColoredFragment> = emptyList()

  override fun getTypeName(t: T?): String = ""

}