// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.kernel.backend.newValueEntity
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation.XValueTextRenderer
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.XFullValueEvaluatorDto.FullValueEvaluatorLinkAttributes
import com.intellij.xdebugger.impl.ui.CustomComponentEvaluator
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal class BackendXValueApi : XValueApi {
  override suspend fun computePresentation(xValueId: XValueId, xValuePlace: XValuePlace): Flow<XValuePresentationEvent>? {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return emptyFlow()
    val xValue = xValueEntity.xValue
    val presentations = Channel<XValuePresentationEvent>(capacity = Int.MAX_VALUE)
    return channelFlow {
      val channelCs = this@channelFlow as CoroutineScope
      var isObsolete = false

      val valueNode = object : XValueNodeEx {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun setPresentation(icon: Icon?, type: @NonNls String?, value: @NonNls String, hasChildren: Boolean) {
          presentations.trySend(XValuePresentationEvent.SetSimplePresentation(icon?.rpcId(), type, value, hasChildren))
        }

        override fun setPresentation(icon: Icon?, presentation: XValuePresentation, hasChildren: Boolean) {
          val partsCollector = XValueTextRendererPartsCollector()
          presentation.renderValue(partsCollector)

          presentations.trySend(XValuePresentationEvent.SetAdvancedPresentation(
            icon?.rpcId(), hasChildren,
            presentation.separator, presentation.isShowName, presentation.type, presentation.isAsync,
            partsCollector.parts
          ))
        }

        override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
          if (fullValueEvaluator is CustomComponentEvaluator) {
            // TODO[IJPL-160146]: support CustomComponentEvaluator
            return
          }

          channelCs.launch {
            // TODO[IJPL-160146]: dispose full value evaluator
            val fullValueEvaluatorEntity = newValueEntity(fullValueEvaluator)

            presentations.trySend(
              XValuePresentationEvent.SetFullValueEvaluator(
                XFullValueEvaluatorDto(
                  XFullValueEvaluatorId(fullValueEvaluatorEntity.id),
                  fullValueEvaluator.linkText,
                  fullValueEvaluator.isEnabled,
                  fullValueEvaluator.isShowValuePopup,
                  fullValueEvaluator.linkAttributes?.let {
                    FullValueEvaluatorLinkAttributes(it.linkIcon?.rpcId(), it.linkTooltipText, it.shortcutSupplier?.get())
                  }
                ))
            )
          }
        }

        override fun getXValue(): XValue {
          return xValue
        }

        override fun clearFullValueEvaluator() {
          // TODO[IJPL-160146]: data race with setFullValueEvaluator
          presentations.trySend(XValuePresentationEvent.ClearFullValueEvaluator)
        }
      }
      xValue.computePresentation(valueNode, xValuePlace)

      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      for (presentation in presentations) {
        send(presentation)
      }
    }
  }

  override suspend fun computeChildren(xValueId: XValueId): Flow<XValueComputeChildrenEvent>? {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return emptyFlow()
    val xValue = xValueEntity.xValue
    val rawEvents = Channel<RawComputeChildrenEvent>(capacity = Int.MAX_VALUE)

    return channelFlow {
      val addNextChildrenCallbackHandler = AddNextChildrenCallbackHandler(this@channelFlow)

      var isObsolete = false
      val xCompositeBridgeNode = object : XCompositeNode {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun addChildren(children: XValueChildrenList, last: Boolean) {
          rawEvents.trySend(RawComputeChildrenEvent.AddChildren(children, last))
        }

        override fun tooManyChildren(remaining: Int) {
          rawEvents.trySend(RawComputeChildrenEvent.TooManyChildren(remaining, null, addNextChildrenCallbackHandler))
        }

        override fun tooManyChildren(remaining: Int, addNextChildren: Runnable) {
          rawEvents.trySend(RawComputeChildrenEvent.TooManyChildren(remaining, addNextChildren, addNextChildrenCallbackHandler))
        }

        override fun setAlreadySorted(alreadySorted: Boolean) {
          rawEvents.trySend(RawComputeChildrenEvent.SetAlreadySorted(alreadySorted))
        }

        override fun setErrorMessage(errorMessage: String) {
          rawEvents.trySend(RawComputeChildrenEvent.SetErrorMessage(errorMessage, null))
        }

        override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
          rawEvents.trySend(RawComputeChildrenEvent.SetErrorMessage(errorMessage, link))
        }

        override fun setMessage(message: String, icon: Icon?, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink?) {
          rawEvents.trySend(RawComputeChildrenEvent.SetMessage(message, icon, attributes, link))
        }
      }

      xValue.computeChildren(xCompositeBridgeNode)

      // mark xCompositeBridgeNode as obsolete when the channel collection is canceled
      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      for (event in rawEvents) {
        send(event.convertToRpcEvent(xValueEntity))
      }
    }
  }


  override suspend fun disposeXValue(xValueId: XValueId) {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    withContext(NonCancellable) {
      change {
        xValueEntity.delete()
      }
    }
  }


  private class XValueTextRendererPartsCollector : XValueTextRenderer {
    private val _parts = mutableListOf<XValueAdvancedPresentationPart>()

    val parts: List<XValueAdvancedPresentationPart>
      get() = _parts

    override fun renderValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.Value(value))
    }

    override fun renderStringValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.StringValue(value))
    }

    override fun renderNumericValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.NumericValue(value))
    }

    override fun renderKeywordValue(value: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.KeywordValue(value))
    }

    override fun renderValue(value: @NlsSafe String, key: TextAttributesKey) {
      _parts.add(XValueAdvancedPresentationPart.ValueWithAttributes(value, key))
    }

    override fun renderStringValue(value: @NlsSafe String, additionalSpecialCharsToHighlight: @NlsSafe String?, maxLength: Int) {
      _parts.add(XValueAdvancedPresentationPart.StringValueWithHighlighting(value, additionalSpecialCharsToHighlight, maxLength))
    }

    override fun renderComment(comment: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.Comment(comment))
    }

    override fun renderSpecialSymbol(symbol: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.SpecialSymbol(symbol))
    }

    override fun renderError(error: @NlsSafe String) {
      _parts.add(XValueAdvancedPresentationPart.Error(error))
    }
  }


  private class AddNextChildrenCallbackHandler(cs: CoroutineScope) {
    private val cs = cs.childScope("AddNextChildrenCallbackHandler")
    private var currentChannelSubscription: Job? = null

    /**
     * Returns Channel which may be passed to frontend, so later on frontend may trigger backend's callbacks
     *
     * This method should be called sequentially
     */
    fun setAddNextChildrenCallback(callback: Runnable?): SendChannel<Unit>? {
      if (callback == null) {
        currentChannelSubscription?.cancel()
        currentChannelSubscription = null
        return null
      }

      val newChannel = Channel<Unit>(capacity = 1)

      val newChannelSubscription = cs.launch {
        launch {
          try {
            awaitCancellation()
          }
          finally {
            newChannel.close()
          }
        }

        for (addNextChildrenRequest in newChannel) {
          callback.run()
        }
      }

      currentChannelSubscription?.cancel()
      currentChannelSubscription = newChannelSubscription

      return newChannel
    }
  }

  private sealed interface RawComputeChildrenEvent {
    suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent

    data class AddChildren(val children: XValueChildrenList, val last: Boolean) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        val names = (0 until children.size()).map { children.getName(it) }
        val childrenXValues = (0 until children.size()).map { children.getValue(it) }
        val childrenXValueEntities = childrenXValues.map { childXValue ->
          newChildXValueEntity(childXValue, parentXValueEntity)
        }
        val childrenXValueDtos = childrenXValueEntities.map { childXValueEntity ->
          childXValueEntity.toXValueDto()
        }
        return XValueComputeChildrenEvent.AddChildren(names, childrenXValueDtos, last)
      }
    }

    data class SetAlreadySorted(val value: Boolean) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        return XValueComputeChildrenEvent.SetAlreadySorted(value)
      }
    }

    data class SetErrorMessage(val message: String, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
        return XValueComputeChildrenEvent.SetErrorMessage(message, link)
      }
    }

    data class SetMessage(val message: String, val icon: Icon?, val attributes: SimpleTextAttributes?, val link: XDebuggerTreeNodeHyperlink?) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        // TODO[IJPL-160146]: support SimpleTextAttributes serialization
        // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
        return XValueComputeChildrenEvent.SetMessage(message, icon?.rpcId(), attributes, link)
      }
    }

    data class TooManyChildren(val remaining: Int, val addNextChildren: Runnable?, val addNextChildrenCallbackHandler: AddNextChildrenCallbackHandler) : RawComputeChildrenEvent {
      override suspend fun convertToRpcEvent(parentXValueEntity: XValueEntity): XValueComputeChildrenEvent {
        return XValueComputeChildrenEvent.TooManyChildren(remaining, addNextChildrenCallbackHandler.setAddNextChildrenCallback(addNextChildren))
      }
    }
  }
}