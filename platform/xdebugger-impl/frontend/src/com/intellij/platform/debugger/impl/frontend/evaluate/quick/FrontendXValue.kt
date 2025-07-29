// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ThreeState
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.ui.tree.XValueExtendedPresentation
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import com.intellij.xdebugger.impl.util.MonolithUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asCompletableFuture
import org.jetbrains.concurrency.asPromise
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class FrontendXValue private constructor(
  val project: Project,
  private val cs: CoroutineScope,
  val xValueDto: XValueDto,
  hasParentValue: Boolean,
  private val presentation: StateFlow<XValueSerializedPresentation>,
) : XValue() {

  @Volatile
  private var modifier: XValueModifier? = null

  var markerDto: XValueMarkerDto? = null

  @Volatile
  private var canNavigateToTypeSource = false

  private val xValueContainer = FrontendXValueContainer(project, cs, hasParentValue) {
    XValueApi.getInstance().computeChildren(xValueDto.id)
  }

  private val fullValueEvaluator = xValueDto.fullValueEvaluator.toFlow().map { evaluatorDto ->
    if (evaluatorDto == null) {
      return@map null
    }
    // TODO: should we strict the coroutine scope?
    FrontendXFullValueEvaluator(cs, xValueDto.id, evaluatorDto)
  }.stateIn(cs, SharingStarted.Eagerly, null)

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
    if (!hasParentValue) {
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

  override fun getXValueDescriptorAsync(): CompletableFuture<XValueDescriptor?>? {
    return xValueDto.descriptor?.asCompletableFuture()
  }

  override fun canNavigateToTypeSource(): Boolean {
    return canNavigateToTypeSource
  }

  override fun canNavigateToTypeSourceAsync(): Promise<Boolean?>? {
    return canNavigateToTypeSourceAsync()?.asCompletableFuture()?.asPromise()
  }

  override fun computeInlineDebuggerData(callback: XInlineDebuggerDataCallback): ThreeState {
    thisLogger().error("#computeInlineDebuggerData should not be called for FrontendXValue, XValueApi.computeInlineData")
    return super.computeInlineDebuggerData(callback)
  }

  override fun computeSourcePosition(navigatable: XNavigatable) {
    cs.launch(Dispatchers.EDT) {
      val sourcePosition: XSourcePositionDto? = XValueApi.getInstance().computeSourcePosition(xValueDto.id)
      navigatable.setSourcePosition(sourcePosition?.sourcePosition())
    }
  }

  override fun computeTypeSourcePosition(navigatable: XNavigatable) {
    cs.launch(Dispatchers.EDT) {
      val sourcePosition: XSourcePositionDto? = XValueApi.getInstance().computeTypeSourcePosition(xValueDto.id)
      navigatable.setSourcePosition(sourcePosition?.sourcePosition())
    }
  }

  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    if (place == XValuePlace.TREE) {
      // for TOOLTIP we are going to calculate it separately
      node.setPresentation(presentation.value)
    }
    val initialFullValueEvaluator = fullValueEvaluator.value
    if (initialFullValueEvaluator != null) {
      node.setFullValueEvaluator(initialFullValueEvaluator)
    }
    node.childCoroutineScope(parentScope = cs, "FrontendXValue#computePresentation").launch(Dispatchers.EDT) {
      launch {
        fullValueEvaluator.collectLatest { evaluator ->
          if (evaluator != null) {
            node.setFullValueEvaluator(evaluator)
          }
          else if (node is XValueNodeEx) {
            node.clearFullValueEvaluator()
          }
        }
      }
      launch {
        val presentationFlow = when (place) {
          XValuePlace.TREE -> {
            presentation
          }
          XValuePlace.TOOLTIP -> {
            XValueApi.getInstance().computeTooltipPresentation(xValueDto.id)
          }
        }
        presentationFlow.collectLatest {
          node.setPresentation(it)
        }
      }
    }
  }

  private fun XValueNode.setPresentation(presentation: XValueSerializedPresentation) {
    when (presentation) {
      is XValueSerializedPresentation.SimplePresentation -> {
        setPresentation(presentation.icon?.icon(), presentation.presentationType, presentation.value, presentation.hasChildren)
      }
      is XValueSerializedPresentation.AdvancedPresentation -> {
        setPresentation(presentation.icon?.icon(), FrontendXValuePresentation(presentation), presentation.hasChildren)
      }
      is XValueSerializedPresentation.ExtendedPresentation -> {
        val advancedPresentation = presentation.presentation
        setPresentation(advancedPresentation.icon?.icon(), FrontendXValueExtendedPresentation(advancedPresentation, presentation.isModified), advancedPresentation.hasChildren)
      }
    }
  }

  override fun computeChildren(node: XCompositeNode) {
    xValueContainer.computeChildren(node)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getModifier(): XValueModifier? {
    return modifier
  }

  override fun calculateEvaluationExpression(): Promise<XExpression?> {
    val deferred = cs.async {
      XValueApi.getInstance().computeExpression(xValueDto.id)?.xExpression()
    }
    return deferred.asCompletableFuture().asPromise()
  }

  override fun getReferrersProvider(): XReferrersProvider? {
    // TODO referrersProvider is only supported in monolith
    return MonolithUtils.findXValueById(xValueDto.id)?.referrersProvider
  }

  override fun toString(): String {
    return "FrontendXValue(id=${xValueDto.id}, value=${presentation.value.rawText()})"
  }

  private class FrontendXValuePresentation(private val advancedPresentation: XValueSerializedPresentation.AdvancedPresentation) : XValuePresentation() {
    override fun renderValue(renderer: XValueTextRenderer) {
      renderAdvancedPresentation(renderer, advancedPresentation)
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

  private class FrontendXValueExtendedPresentation(
    private val advancedPresentation: XValueSerializedPresentation.AdvancedPresentation,
    private val isModified: Boolean,
  ) : XValueExtendedPresentation() {
    override fun renderValue(renderer: XValueTextRenderer) {
      renderAdvancedPresentation(renderer, advancedPresentation)
    }

    override fun isModified(): Boolean {
      return isModified
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

  companion object {

    fun extract(value: XValue): FrontendXValue? {
      return value as? FrontendXValue ?: (value as? FrontendXNamedValue)?.delegate
    }

    @JvmStatic
    suspend fun create(project: Project, evaluatorCoroutineScope: CoroutineScope, xValueDto: XValueDto, hasParentValue: Boolean): XValue {
      // TODO[IJPL-160146]: Is it ok to dispose only when evaluator is changed?
      //   So, XValues will live more than popups where they appeared
      //   But it is needed for Mark object functionality at least.
      //   Since we cannot dispose XValue when evaluation popup is closed
      //   because it getting closed when Mark Object dialog is shown,
      //   so we cannot refer to the backend's xValue
      val cs = evaluatorCoroutineScope.childScope("FrontendXValue")
      val presentation = xValueDto.presentation.toFlow().stateIn(cs)
      val frontendXValue = FrontendXValue(project, cs, xValueDto, hasParentValue, presentation)
      val name = xValueDto.name
      return if (name != null) FrontendXNamedValue(frontendXValue, name) else frontendXValue
    }
  }
}

private fun renderAdvancedPresentation(renderer: XValuePresentation.XValueTextRenderer, advancedPresentation: XValueSerializedPresentation.AdvancedPresentation) {
  for (part in advancedPresentation.parts) {
    when (part) {
      is XValueAdvancedPresentationPart.Comment -> {
        renderer.renderComment(part.text)
      }
      is XValueAdvancedPresentationPart.Error -> {
        renderer.renderError(part.text)
      }
      is XValueAdvancedPresentationPart.KeywordValue -> {
        renderer.renderKeywordValue(part.text)
      }
      is XValueAdvancedPresentationPart.NumericValue -> {
        renderer.renderNumericValue(part.text)
      }
      is XValueAdvancedPresentationPart.SpecialSymbol -> {
        renderer.renderSpecialSymbol(part.text)
      }
      is XValueAdvancedPresentationPart.StringValue -> {
        renderer.renderStringValue(part.text)
      }
      is XValueAdvancedPresentationPart.StringValueWithHighlighting -> {
        renderer.renderStringValue(part.text, part.additionalSpecialCharsToHighlight, part.maxLength)
      }
      is XValueAdvancedPresentationPart.Value -> {
        renderer.renderValue(part.text)
      }
      is XValueAdvancedPresentationPart.ValueWithAttributes -> {
        // TODO[IJPL-160146]: support [TextAttributesKey] serialization
        val attributesKey = part.key
        if (attributesKey != null) {
          renderer.renderValue(part.text, attributesKey)
        }
        else {
          renderer.renderValue(part.text)
        }
      }
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

private fun XValueSerializedPresentation.rawText(): String = when (this) {
  is XValueSerializedPresentation.AdvancedPresentation -> parts.joinToString("")
  is XValueSerializedPresentation.ExtendedPresentation -> presentation.rawText()
  is XValueSerializedPresentation.SimplePresentation -> value
}

private class FrontendXNamedValue(
  val delegate: FrontendXValue,
  name: String,
) : XNamedValue(name) {
  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    delegate.computePresentation(node, place)
  }

  override fun canNavigateToSource(): Boolean {
    return delegate.canNavigateToSource()
  }

  override fun getXValueDescriptorAsync(): CompletableFuture<XValueDescriptor?>? {
    return delegate.xValueDescriptorAsync
  }

  override fun canNavigateToTypeSource(): Boolean {
    return delegate.canNavigateToTypeSource()
  }

  override fun canNavigateToTypeSourceAsync(): Promise<Boolean?>? {
    return delegate.canNavigateToTypeSourceAsync()
  }

  override fun computeInlineDebuggerData(callback: XInlineDebuggerDataCallback): ThreeState {
    return delegate.computeInlineDebuggerData(callback)
  }

  override fun computeSourcePosition(navigatable: XNavigatable) {
    delegate.computeSourcePosition(navigatable)
  }

  override fun computeTypeSourcePosition(navigatable: XNavigatable) {
    delegate.computeTypeSourcePosition(navigatable)
  }

  override fun computeChildren(node: XCompositeNode) {
    delegate.computeChildren(node)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun getModifier(): XValueModifier? {
    return delegate.modifier
  }

  override fun calculateEvaluationExpression(): Promise<XExpression?> {
    return delegate.calculateEvaluationExpression()
  }

  override fun getReferrersProvider(): XReferrersProvider? {
    return delegate.referrersProvider
  }

  override fun toString(): String {
    return "FrontendXNamedValue(name=$name, delegate=$delegate)"
  }
}
