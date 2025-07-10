// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.split

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.ui.components.JBLabel
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

/**
 * Creates a UI component, that receives its data from a [SplitComponentModel] object. Provider should be registered in XML with id
 * matching the corresponding model's one. 'Attached' model can be obtained on the backend side using [SplitComponentFactory.getModel].
 */
@ApiStatus.Experimental
interface SplitComponentProvider {
  companion object {
    private val EP = KeyedExtensionCollector<SplitComponentProvider, String>("com.intellij.frontend.splitComponentProvider")

    @ApiStatus.Internal
    fun createComponent(id: SplitComponentIdWithProvider) : ComponentContainer {
      val provider = EP.findSingle(id.providerId)
      if (provider != null) {
        val container = provider.createComponent(id.componentId)
        if (container != null) {
          return container
        }
        else {
          fileLogger().warn("Provider ($provider) couldn't create component for id=$id")
        }
      }
      else {
        fileLogger().warn("Couldn't find provider for id=$id")
      }
      val component = JBLabel(IdeBundle.message("split.component.missing", id))
      return object : ComponentContainer {
        override fun getComponent() = component
        override fun getPreferredFocusableComponent() = null
        override fun dispose() {}
      }.apply {
        Disposer.dispose(this)
      }
    }
  }

  /**
   * [ComponentContainer.getPreferredFocusableComponent] in the result is not used currently in rem-dev scenarios. It can only be useful
   * in monolith mode now.
   *
   * NOTE. The returned object should take care about its disposal itself. Frontend will keep the returned component cached until its
   * disposal. Supposedly, the disposal should happen when the associated modal signals it (e.g. when it's disposed itself).
   */
  @RequiresEdt
  fun createComponent(id: SplitComponentId) : ComponentContainer?
}

private class SplitComponentProviderBean : BaseKeyedLazyInstance<SplitComponentProvider>(), KeyedLazyInstance<SplitComponentProvider> {
  @Attribute("id")
  @RequiredElement
  var id : String = ""

  @Attribute("implementation")
  @RequiredElement
  var implementation: String = ""

  override fun getKey(): String {
    return id
  }

  override fun getImplementationClassName(): String {
    return implementation
  }
}