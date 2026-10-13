// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.split

import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Provides a way to replace some parts of the UI which is LUXed/BeControlled on the backend with a pure frontend components.
 *
 * Example:
 * ```kotlin
 * // Shared
 * val binding = SplitComponentBinding<SomeBackendModelId>("myUniquePlaceInIDE", ::SomeBackendModelId)
 *
 * // backend
 * val component = binding.createComponent(project, scope, backendModelId)
 *
 * // frontend
 *
 * // Also, add provider to xml
 * class SplitComponentProviderImpl: SplitComponentProvider<SomeBackendModelId> {
 *   override val binding: SplitComponentBinding<SomeBackendModelId> = binding
 *
 *   // example implementation of createComponent. Real implementation depends on design mockups (e.g. how to handle loading)
 *   override fun createComponent(project: Project, scope: CoroutineScope, modelId: SomeBackendModelId): JComponent {
 *     val loadingPanel = JBLoadingPanel()
 *     scope.launch(Dispatchers.UI) {
 *       loadingPanel.startLoading()
 *       try {
 *         val data = callSomeRpc(modelId)
 *         loadingPanel.setContent(createUi(data))
 *       } finally {
 *         loadingPanel.stopLoading()
 *       }
 *     }
 *     return loadingPanel
 *   }
 * }
 * ```
 *
 * This binding implementation provides a place in the backend's UI where the frontend-based component should be used.
 * For the frontend side the binding provides type [T] of the backend's model id.
 * So, the frontend may request some data through RPC using an id of the type [T]
 *
 * Typically, binding is a global object shared between frontend and backend.
 *
 * @see SplitComponentBinding.createComponent
 * @see SplitComponentProvider
 */
@ApiStatus.Internal
@ApiStatus.NonExtendable
interface SplitComponentBinding<T : Id> {
  val placeId: String

  fun deserializeModelId(uid: UID): T
}

/**
 * Returns a new instance of [SplitComponentBinding].
 *
 * @param placeId should be unique through all other bindings.
 * @param modelIdFactory provides a factory for the backend model id. Typically, use just `::SomeBackendModelId`
 */
@ApiStatus.Internal
fun <T : Id> SplitComponentBinding(
  placeId: String,
  modelIdFactory: (UID) -> T,
): SplitComponentBinding<T> = SplitComponentBindingImpl(placeId, modelIdFactory)

/**
 * In a monolith case this will create the UI element created by [SplitComponentProvider.createComponent].
 * [SplitComponentProvider] with the equal binding will be used for it.
 *
 * In a remote development case, the placeholder component is created on the backend side and then
 * the frontend will replace it with the pure frontend component using [SplitComponentProvider] with the equal binding.
 *
 * @param scope the scope of the model.
 * The scope passed in [SplitComponentProvider.createComponent] will be canceled when this [scope] is canceled.
 */
@RequiresEdt
@ApiStatus.Internal
fun <T : Id> SplitComponentBinding<T>.createComponent(project: Project, scope: CoroutineScope, modelId: T): JComponent {
  val id = SplitComponentId(placeId, modelId.uid)
  logger.debug { "Registered model with id=$id : $modelId" }
  if (AppMode.isRemoteDevHost()) {
    logger.debug("Creating component placeholder")
    return SplitComponentPlaceholder(project, scope, id)
  }
  else {
    logger.debug("Creating component in-place")
    return SplitComponentProvider.createComponent(project, scope, this, modelId)
  }
}

private class SplitComponentBindingImpl<T : Id>(override val placeId: String, private val modelIdFactory: (UID) -> T) : SplitComponentBinding<T> {
  override fun deserializeModelId(uid: UID): T {
    return modelIdFactory(uid)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SplitComponentBindingImpl<*>) return false

    if (placeId != other.placeId) return false

    return true
  }

  override fun hashCode(): Int {
    return placeId.hashCode()
  }
}

private val logger = fileLogger()