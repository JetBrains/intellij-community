// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.presentation.PresentationProvider
import com.intellij.ide.structureView.logical.ContainerElementsProvider
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

  open fun isFlatElements(): Boolean = false

  override fun getTypeName(t: T?): String = ""

}