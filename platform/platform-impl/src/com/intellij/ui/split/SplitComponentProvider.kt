// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.split

import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.ui.components.JBLabel
import com.intellij.ui.split.SplitComponentProvider.Companion.createComponent
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Creates a frontend's UI component that is going to be inserted into LUX/BeControls.
 * Its content is based on the backend's model which id (of type [T]) is provided in the [createComponent] function.
 *
 * To make the binding between frontend and the backend type-safe [binding] should be provided.
 * Typically, it should be a global object shared between frontend and backend.
 *
 * @see SplitComponentBinding.createComponent
 */
@ApiStatus.Internal
interface SplitComponentProvider<T : Id> {
  val binding: SplitComponentBinding<T>

  /**
   * Creates frontend's UI component based on the backend model with id: [modelId]
   *
   * @param scope that is going to be canceled when [CoroutineScope] passed to [SplitComponentFactory.createComponent] is canceled.
   */
  @RequiresEdt
  fun createComponent(project: Project, scope: CoroutineScope, modelId: T): JComponent?

  companion object {
    private val EP = ExtensionPointName<SplitComponentProvider<*>>("com.intellij.frontend.splitComponentProvider")

    @ApiStatus.Internal
    fun createComponent(project: Project, cs: CoroutineScope, placeId: String, modelUid: UID): JComponent {
      return createComponent(placeId, modelUid) { provider ->
        provider.createComponentByUid(project, cs, modelUid)
      }
    }

    // The separate method with [binding] is needed for the local case when modelId won't be serialized/deserialized by Rd models
    internal fun <T : Id> createComponent(project: Project, cs: CoroutineScope, binding: SplitComponentBinding<T>, modelId: T): JComponent {
      return createComponent(binding.placeId, modelId.uid) { provider ->
        // provider should have generic of type T because it has the same binding as the passed one
        @Suppress("UNCHECKED_CAST")
        (provider as SplitComponentProvider<T>).createComponent(project, cs, modelId)
      }
    }

    private fun <T : Id> SplitComponentProvider<T>.createComponentByUid(
      project: Project,
      cs: CoroutineScope,
      modelUId: UID,
    ): JComponent? {
      return createComponent(project, cs, binding.deserializeModelId(modelUId))
    }

    private fun createComponent(
      placeId: String,
      modelUid: UID,
      componentFactory: (SplitComponentProvider<*>) -> JComponent?,
    ): JComponent {
      val extensions = EP.extensionList.filter { it.binding.placeId == placeId }
      val provider = extensions.firstOrNull()
      if (extensions.size > 1) {
        fileLogger().warn("Multiple provider for place: $placeId are registered. First one ($provider) is used")
      }
      if (provider != null) {
        val component = componentFactory(provider)
        if (component != null) {
          return component
        }
        else {
          fileLogger().warn("Provider ($provider) couldn't create component for id ${modelUid}")
        }
      }
      else {
        fileLogger().warn("Couldn't find provider for place: $placeId")
      }
      val component = JBLabel(IdeBundle.message("split.component.missing", "$placeId/$modelUid"))
      return component
    }
  }
}