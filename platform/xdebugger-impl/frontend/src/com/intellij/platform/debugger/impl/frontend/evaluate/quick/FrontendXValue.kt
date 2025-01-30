// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ConcurrencyUtil
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.rhizome.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asCompletableFuture
import org.jetbrains.concurrency.asPromise

internal class FrontendXValue(
  val project: Project,
  evaluatorCoroutineScope: CoroutineScope,
  val xValueDto: XValueDto,
  val parentXValue: FrontendXValue?,
) : XValue() {
  // TODO[IJPL-160146]: Is it ok to dispose only when evaluator is changed?
  //   So, XValues will live more than popups where they appeared
  //   But it is needed for Mark object functionality at least.
  //   Since we cannot dispose XValue when evaluation popup is closed
  //   because it getting closed when Mark Object dialog is shown,
  //   so we cannot refer to the backend's xValue
  private val cs = evaluatorCoroutineScope.childScope("FrontendXValue")

  @Volatile
  private var modifier: XValueModifier? = null

  var markerDto: XValueMarkerDto? = null

  @Volatile
  private var canNavigateToTypeSource = false

  init {
    cs.launch {
      val canBeModified = xValueDto.canBeModified.await()
      if (canBeModified) {
        modifier = FrontendXValueModifier(project, xValueDto)
      }
    }

    cs.launch {
      xValueDto.valueMark.toFlow().collectLatest {
        markerDto = it
      }
    }

    // request to dispose root XValue, children will be disposed automatically
    if (parentXValue == null) {
      cs.launch {
        try {
          awaitCancellation()
        }
        finally {
          withContext(NonCancellable) {
            project.service<FrontendXValueDisposer>().dispose(xValueDto)
          }
        }
      }
    }

    cs.launch {
      canNavigateToTypeSource = xValueDto.canNavigateToTypeSource.await()
    }
  }

  override fun canNavigateToSource(): Boolean {
    return xValueDto.canNavigateToSource
  }

  override fun canNavigateToTypeSource(): Boolean {
    return canNavigateToTypeSource
  }

  override fun canNavigateToTypeSourceAsync(): Promise<Boolean?>? {
    return canNavigateToTypeSourceAsync()?.asCompletableFuture()?.asPromise()
  }

  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    node.childCoroutineScope(parentScope = cs, "FrontendXValue#computePresentation").launch(Dispatchers.EDT) {
      XValueApi.getInstance().computePresentation(xValueDto.id, place)?.collect { presentationEvent ->
        when (presentationEvent) {
          is XValuePresentationEvent.SetSimplePresentation -> {
            node.setPresentation(presentationEvent.icon?.icon(), presentationEvent.presentationType, presentationEvent.value, presentationEvent.hasChildren)
          }
          is XValuePresentationEvent.SetAdvancedPresentation -> {
            node.setPresentation(presentationEvent.icon?.icon(), FrontendXValuePresentation(presentationEvent), presentationEvent.hasChildren)
          }
          is XValuePresentationEvent.SetFullValueEvaluator -> {
            node.setFullValueEvaluator(FrontendXFullValueEvaluator(cs, presentationEvent.fullValueEvaluatorDto))
          }
          XValuePresentationEvent.ClearFullValueEvaluator -> {
            if (node is XValueNodeEx) {
              node.clearFullValueEvaluator()
            }
          }
        }
      }
    }
  }

  override fun computeChildren(node: XCompositeNode) {
    node.childCoroutineScope(parentScope = cs, "FrontendXValue#computeChildren").launch(Dispatchers.EDT) {
      XValueApi.getInstance().computeChildren(xValueDto.id)?.collect { computeChildrenEvent ->
        when (computeChildrenEvent) {
          is XValueComputeChildrenEvent.AddChildren -> {
            val childrenList = XValueChildrenList()
            for (i in computeChildrenEvent.children.indices) {
              childrenList.add(computeChildrenEvent.names[i], FrontendXValue(project, cs, computeChildrenEvent.children[i], parentXValue = this@FrontendXValue))
            }
            node.addChildren(childrenList, computeChildrenEvent.isLast)
          }
          is XValueComputeChildrenEvent.SetAlreadySorted -> {
            node.setAlreadySorted(computeChildrenEvent.value)
          }
          is XValueComputeChildrenEvent.SetErrorMessage -> {
            node.setErrorMessage(computeChildrenEvent.message, computeChildrenEvent.link)
          }
          is XValueComputeChildrenEvent.SetMessage -> {
            // TODO[IJPL-160146]: support SimpleTextAttributes serialization -- don't pass SimpleTextAttributes.REGULAR_ATTRIBUTES
            node.setMessage(
              computeChildrenEvent.message,
              computeChildrenEvent.icon?.icon(),
              computeChildrenEvent.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES,
              computeChildrenEvent.link
            )
          }
          is XValueComputeChildrenEvent.TooManyChildren -> {
            val addNextChildren = computeChildrenEvent.addNextChildren
            if (addNextChildren != null) {
              node.tooManyChildren(computeChildrenEvent.remaining, Runnable { addNextChildren.trySend(Unit) })
            }
            else {
              @Suppress("DEPRECATION")
              node.tooManyChildren(computeChildrenEvent.remaining)
            }
          }
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getModifier(): XValueModifier? {
    return modifier
  }

  private class FrontendXValuePresentation(private val advancedPresentation: XValuePresentationEvent.SetAdvancedPresentation) : XValuePresentation() {
    override fun renderValue(renderer: XValueTextRenderer) {
      for (part in advancedPresentation.parts) {
        when (part) {
          is XValueAdvancedPresentationPart.Comment -> {
            renderer.renderComment(part.comment)
          }
          is XValueAdvancedPresentationPart.Error -> {
            renderer.renderError(part.error)
          }
          is XValueAdvancedPresentationPart.KeywordValue -> {
            renderer.renderKeywordValue(part.value)
          }
          is XValueAdvancedPresentationPart.NumericValue -> {
            renderer.renderNumericValue(part.value)
          }
          is XValueAdvancedPresentationPart.SpecialSymbol -> {
            renderer.renderSpecialSymbol(part.symbol)
          }
          is XValueAdvancedPresentationPart.StringValue -> {
            renderer.renderStringValue(part.value)
          }
          is XValueAdvancedPresentationPart.StringValueWithHighlighting -> {
            renderer.renderStringValue(part.value, part.additionalSpecialCharsToHighlight, part.maxLength)
          }
          is XValueAdvancedPresentationPart.Value -> {
            renderer.renderValue(part.value)
          }
          is XValueAdvancedPresentationPart.ValueWithAttributes -> {
            // TODO[IJPL-160146]: support [TextAttributesKey] serialization
            val attributesKey = part.key
            if (attributesKey != null) {
              renderer.renderValue(part.value, attributesKey)
            }
            else {
              renderer.renderValue(part.value)
            }
          }
        }
      }
    }

    override fun getSeparator(): @NlsSafe String {
      return advancedPresentation.separator
    }

    override fun isShowName(): Boolean {
      return advancedPresentation.isShownName
    }

    override fun getType(): @NlsSafe String? {
      return advancedPresentation.presentationType
    }

    override fun isAsync(): Boolean {
      return advancedPresentation.isAsync
    }
  }
}

internal fun Obsolescent.childCoroutineScope(parentScope: CoroutineScope, name: String): CoroutineScope {
  val obsolescent = this
  val scope = parentScope.childScope(name)
  parentScope.launch(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
    while (!obsolescent.isObsolete) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    scope.cancel()
  }
  return scope
}

@Service(Service.Level.PROJECT)
private class FrontendXValueDisposer(project: Project, val cs: CoroutineScope) {
  fun dispose(xValueDto: XValueDto) {
    cs.launch(Dispatchers.IO) {
      XValueApi.getInstance().disposeXValue(xValueDto.id)
    }
  }
}