// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.debugger.impl.rpc.XFullValueEvaluatorDto.FullValueEvaluatorLinkAttributes
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.deleteValueById
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.ui.JBColor
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.pinned.items.PinToTopValue
import com.intellij.xdebugger.impl.rpc.toRpc
import com.intellij.xdebugger.impl.ui.XValueTextProvider
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.asDeferred

/**
 * Model which holds reference to the [XValue].
 * This model allows sending [XValueId] to a client and afterward retrieving [XValue] by this id.
 *
 * Since [XValue] implementation is provided by plugins we cannot easily extend this API to support
 * [XFullValueEvaluator], [ValueMarkup] etc. which are needed for the RPC backend implementation.
 * So, we cannot store just [XValue], and we store [BackendXValueModel] instead.
 *
 * [BackendXValueModel] can be created by [BackendXValueModelsManager.createXValueModel] function.
 *
 * @see [XValueId]
 */
@ApiStatus.Internal
class BackendXValueModel internal constructor(
  val cs: CoroutineScope,
  val session: XDebugSessionImpl,
  val xValue: XValue,
  precomputePresentation: Boolean,
) {
  val id: XValueId = storeValueGlobally(cs, this, type = BackendXValueIdType)

  private val _marker = MutableStateFlow<XValueMarkerDto?>(null)
  val marker: StateFlow<XValueMarkerDto?> = _marker.asStateFlow()

  private val _fullValueEvaluator = MutableStateFlow<XFullValueEvaluator?>(null)
  val fullValueEvaluator: StateFlow<XFullValueEvaluator?> = _fullValueEvaluator.asStateFlow()

  private val _additionalLinkFlow = MutableStateFlow<XDebuggerTreeNodeHyperlink?>(null)
  val additionalLinkFlow: StateFlow<XDebuggerTreeNodeHyperlink?> = _additionalLinkFlow.asStateFlow()

  private val _presentation = MutableSharedFlow<XValueSerializedPresentation>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val presentation: Flow<XValueSerializedPresentation> = _presentation.asSharedFlow()

  init {
    if (precomputePresentation) {
      computeValuePresentation()
    }
  }

  fun computeValuePresentation() {
    xValue.computePresentation(
      cs,
      XValuePlace.TREE,
      presentationHandler = {
        _presentation.tryEmit(it)
      },
      fullValueEvaluatorHandler = {
        _fullValueEvaluator.value = it
      },
      hyperlinkHandler = {
        _additionalLinkFlow.value = it
      },
    )
  }

  fun computeTooltipPresentation(): Flow<XValueSerializedPresentation> {
    return channelFlow {
      val channelCs = this
      xValue.computePresentation(
        channelCs,
        XValuePlace.TOOLTIP,
        presentationHandler = {
          trySend(it)
        },
        fullValueEvaluatorHandler = {
          // ignore, take TREE into account only
        },
        hyperlinkHandler = {
          // ignore, take TREE into account only
        },
      )
    }.buffer(1)
  }

  fun setMarker(marker: ValueMarkup?) {
    if (marker == null) {
      _marker.value = null
      return
    }
    _marker.update { XValueMarkerDto(marker.text, (marker.color ?: JBColor.RED).rpcId(), marker.toolTipText) }
  }

  @ApiStatus.Internal
  fun delete() {
    deleteValueById(id, type = BackendXValueIdType)
  }

  companion object {
    @ApiStatus.Internal
    fun findById(id: XValueId): BackendXValueModel? {
      return findValueById(id, type = BackendXValueIdType)
    }

    private object BackendXValueIdType : BackendValueIdType<XValueId, BackendXValueModel>(::XValueId)
  }
}

@ApiStatus.Internal
suspend fun BackendXValueModel.toXValueDtoWithPresentation(): XValueDtoWithPresentation {
  val value = toXValueDto()
  return XValueDtoWithPresentation(
    value,
    presentation.toRpc(),
    fullValueEvaluator.map { it?.toRpc() }.toRpc(),
    additionalLinkFlow.map { it?.toRpc(cs) }.toRpc(),
  )
}

@ApiStatus.Internal
suspend fun BackendXValueModel.toXValueDto(): XValueDto {
  val xValueModel = this
  val xValue = this.xValue
  val valueMarkupFlow: RpcFlow<XValueMarkerDto?> = xValueModel.marker.toRpc()

  val textProvider = getTextProviderFlow(xValue, xValueModel)
  val canMarkValue = xValue.isReady.thenApply {
    session.getValueMarkers()?.canMarkValue(xValue) ?: false
  }

  return XValueDto(
    xValueModel.id,
    xValue.xValueDescriptorAsync?.asDeferred(),
    canNavigateToSource = xValue.canNavigateToSource(),
    canNavigateToTypeSource = xValue.canNavigateToTypeSourceAsync().asDeferred(),
    canBeModified = xValue.modifierAsync.thenApply { modifier -> modifier != null }.asDeferred(),
    canMarkValue = canMarkValue.asDeferred(),
    valueMarkupFlow,
    (xValue as? XNamedValue)?.name,
    textProvider?.toRpc(),
    (xValue as? PinToTopValue)?.pinToTopDataFuture?.asDeferred(),
  )
}

@ApiStatus.Internal
suspend fun XFullValueEvaluator.toRpc(): XFullValueEvaluatorDto = XFullValueEvaluatorDto(
  linkText,
  isEnabledFlow.toRpc(),
  isShowValuePopup,
  linkAttributes?.let { attributes ->
    FullValueEvaluatorLinkAttributes(attributes.linkIcon?.rpcId(), attributes.linkTooltipText, attributes.shortcutSupplier?.get())
  }
)

private fun getTextProviderFlow(
  xValue: XValue,
  xValueModel: BackendXValueModel,
): Flow<XValueTextProviderDto>? = (xValue as? XValueTextProvider)
  ?.let {
    xValueModel.presentation.map {
      XValueTextProviderDto(
        xValue.shouldShowTextValue(),
        xValue.valueText,
      )
    }.distinctUntilChanged()
  }

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BackendXValueModelsManager {
  private val xValueModels = mutableMapOf<XDebugSessionImpl, MutableSet<BackendXValueModel>>()
  private val lock = Any()

  fun getXValueModelsForSession(session: XDebugSessionImpl): Set<BackendXValueModel> = synchronized(lock) {
    return xValueModels[session]?.toImmutableSet() ?: emptySet()
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  fun createXValueModel(cs: CoroutineScope, session: XDebugSessionImpl, xValue: XValue): BackendXValueModel = synchronized(lock) {
    val model = BackendXValueModel(cs, session, xValue, precomputePresentation = true)

    if (session !in xValueModels) {
      xValueModels[session] = mutableSetOf()
      session.coroutineScope.awaitCancellationAndInvoke {
        synchronized(lock) {
          xValueModels.remove(session)
        }
      }
    }
    xValueModels[session]!!.add(model)

    cs.awaitCancellationAndInvoke {
      synchronized(lock) {
        xValueModels[session]?.remove(model)
      }
    }

    model
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackendXValueModelsManager = project.service<BackendXValueModelsManager>()
  }
}
