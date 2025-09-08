// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.split

import com.intellij.idea.AppMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import com.intellij.ui.RemoteTransferUIManager
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

/**
 * Allows to use 'split' components (i.e. ones with functionality separated into frontend and backend parts) in a UI hierarchy
 * which is not yet reworked in this way, is created on the backend side, and is mapped to frontend using fallback technologies
 * (Lux, BeControl).
 *
 * Each 'split' component is implemented using a 'model' ([SplitComponentModel]) passed to [createComponent] method, and a UI provider
 * ([SplitComponentProvider]), registered in XML. Communication between model and UI is organized by themselves, but to 'find' each other
 * they can use `componentId` passed to [SplitComponentProvider.createComponent]. It can be used on the backend side to get the model
 * instance via [getModel].
 */
@ApiStatus.Experimental
@Service
class SplitComponentFactory private constructor() {
  companion object {
    private val logger = fileLogger()

    @JvmStatic
    fun getInstance() : SplitComponentFactory = service<SplitComponentFactory>()
  }

  private object SplitComponentModelIdType : BackendValueIdType<SplitComponentId, SplitComponentModel>(::SplitComponentId)

  /**
   * In monolith case this will create the associated UI component in place. On a remote development backend, a placeholder component
   * is created instead with the real component created on the frontend side.
   *
   * @param scope the scope of the model.
   * The scope passed in [SplitComponentProvider.createComponent] will be canceled when this [scope] is canceled.
   */
  @RequiresEdt
  fun createComponent(project: Project, scope: CoroutineScope, model: SplitComponentModel): ComponentContainer {
    val providerId = model.providerId
    val componentId = storeValueGlobally(scope, model, SplitComponentModelIdType)
    val id = SplitComponentIdWithProvider(providerId, componentId)
    logger.debug { "Registered model with id=$id : $model" }
    if (AppMode.isRemoteDevHost()) {
      logger.debug("Creating component placeholder")
      val placeholder = SplitComponentPlaceholder(project, scope, id)
      return object : ComponentContainer {
        override fun getComponent() = placeholder
        override fun getPreferredFocusableComponent() = null
        override fun dispose() {}
      }
    }
    else {
      logger.debug("Creating component in-place")
      return SplitComponentProvider.createComponent(project, scope, id)
    }
  }

  @JvmName("getRegisteredModel")
  fun getModel(id: SplitComponentId): SplitComponentModel? {
    val modelIsLocal = id.modelIsOnClient == PlatformUtils.isJetBrainsClient()
    return if (modelIsLocal) findValueById(id, SplitComponentModelIdType) else null
  }

  inline fun <reified T : SplitComponentModel> getModel(id: SplitComponentId): T? {
    return getModel(id) as? T
  }
}

@ApiStatus.Experimental
@Serializable
data class SplitComponentId @ApiStatus.Internal constructor(
  override val uid: UID,
  @ApiStatus.Internal val modelIsOnClient: Boolean = PlatformUtils.isJetBrainsClient(),
) : Id

@ApiStatus.Internal
data class SplitComponentIdWithProvider(val providerId: String, val componentId: SplitComponentId) {
  override fun toString(): String {
    return "${componentId.uid}/$providerId"
  }
}

@ApiStatus.Internal
class SplitComponentPlaceholder(
  val project: Project,
  val scope: CoroutineScope,
  val id: SplitComponentIdWithProvider,
) : JPanel() {
  init {
    RemoteTransferUIManager.setWellBeControlizableAndPaintedQuickly(this)
  }
}