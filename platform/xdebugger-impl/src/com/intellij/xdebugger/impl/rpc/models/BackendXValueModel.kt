// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.deleteValueById
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.XValueSerializedPresentation
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

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
) {
  val id: XValueId = storeValueGlobally(cs, this, type = BackendXValueIdType)

  private val _marker = MutableStateFlow<XValueMarkerDto?>(null)
  val marker: StateFlow<XValueMarkerDto?> = _marker.asStateFlow()

  private val _fullValueEvaluator = MutableStateFlow<XFullValueEvaluator?>(null)
  val fullValueEvaluator: StateFlow<XFullValueEvaluator?> = _fullValueEvaluator.asStateFlow()

  private val _presentation = MutableSharedFlow<XValueSerializedPresentation>(replay = 1)
  val presentation: Flow<XValueSerializedPresentation> = _presentation.asSharedFlow()

  init {
    xValue.computePresentation(
      cs,
      XValuePlace.TREE,
      presentationHandler = {
        _presentation.tryEmit(it)
      },
      fullValueEvaluatorHandler = {
        _fullValueEvaluator.value = it
      }
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
        }
      )
    }.buffer(1)
  }

  fun setMarker(marker: ValueMarkup?) {
    if (marker == null) {
      _marker.value = null
      return
    }
    _marker.update { XValueMarkerDto(marker.text, marker.color, marker.toolTipText) }
  }

  fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator?) {
    _fullValueEvaluator.value = fullValueEvaluator
  }

  fun setPresentation(presentation: XValueSerializedPresentation) {
    _presentation.tryEmit(presentation)
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
@Service(Service.Level.PROJECT)
class BackendXValueModelsManager {
  private val xValueModels = mutableMapOf<XDebugSessionImpl, MutableSet<BackendXValueModel>>()
  private val lock = Any()

  fun getXValueModelsForSession(session: XDebugSessionImpl): Set<BackendXValueModel> = synchronized(lock) {
    return xValueModels[session]?.toImmutableSet() ?: emptySet()
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  fun createXValueModel(cs: CoroutineScope, session: XDebugSessionImpl, xValue: XValue): BackendXValueModel = synchronized(lock) {
    val model = BackendXValueModel(cs, session, xValue)

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
