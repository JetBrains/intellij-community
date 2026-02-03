// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical.model

import com.intellij.ide.presentation.PresentationProvider
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.ClassExtension
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * Adds additional features for common PresentationProvider
 *
 * Important: do not define the providers which implement this class via @Presentation annotation,
 *  only <presentationProvider> extension point is allowed
 */
@ApiStatus.Experimental
abstract class LogicalModelPresentationProvider<T>: PresentationProvider<T>() {

  companion object {
    private val PROVIDER_EP = ExtensionPointName<PresentationProvider<*>>("com.intellij.presentationProvider")
    private val PROVIDERS = ClassExtension<PresentationProvider<*>>(PROVIDER_EP.name)

    fun <T> getForObject(obj: T): LogicalModelPresentationProvider<T>? {
      return PROVIDERS.forKey(obj!!::class.java)
        .firstOrNull { it is LogicalModelPresentationProvider<*> } as? LogicalModelPresentationProvider<T>
    }
  }

  open fun isAutoExpand(t: T): Boolean = false

  open fun getColoredText(t: T): List<PresentableNodeDescriptor.ColoredFragment> = emptyList()

  open fun handleClick(t: T, fragmentIndex: Int): CompletableFuture<Boolean> {
    return CompletableFuture.completedFuture(false)
  }

  open fun getTooltipText(t: T): String? = null

}