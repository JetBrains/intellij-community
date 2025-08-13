// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.icons.*
import com.intellij.ui.icons.RowIcon
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil.scale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBScalableIcon
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Graphics
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon
import javax.swing.JTree
import kotlin.coroutines.resume

private val repaintScheduler = DeferredIconRepaintScheduler()

class DeferredIconImpl<T> : JBScalableIcon, DeferredIcon, RetrievableIcon, IconWithToolTip, CopyableIcon {
  companion object {
    internal val EMPTY_ICON: Icon by lazy { EmptyIcon.create(16).withIconPreScaled(false) }

    private val nextDeferredIconId = AtomicLong(1)

    fun <T> withoutReadAction(baseIcon: Icon?, param: T, evaluator: (T) -> Icon?): DeferredIcon {
      return DeferredIconImpl(baseIcon = baseIcon, param = param, needReadAction = false, evaluator = evaluator, listener = null)
    }
  }

  private val delegateIcon: Icon

  @Volatile
  private var scaledDelegateIcon: Icon
  private var cachedScaledIcon: DeferredIconImpl<T>?
  private var evaluator: ((T) -> Icon?)?
  private var asyncEvaluator: (suspend (T) -> Icon)? = null
  private var scheduledRepaints: Set<RepaintRequest>? = null

  private val isScheduled = AtomicBoolean(false)

  @JvmField
  internal val param: T

  @ApiStatus.Internal
  val uniqueId: Long = nextDeferredIconId.getAndIncrement()

  val isNeedReadAction: Boolean

  @get:RequiresEdt
  var isDone: Boolean = false
    private set

  private var modificationCount = AtomicLong(0)
  private val evaluatedListener: ((DeferredIconImpl<T>, Icon) -> Unit)?

  private constructor(icon: DeferredIconImpl<T>) : super(icon) {
    delegateIcon = icon.delegateIcon
    scaledDelegateIcon = icon.delegateIcon
    cachedScaledIcon = null
    evaluator = icon.evaluator
    asyncEvaluator = icon.asyncEvaluator
    isScheduled.set(icon.isScheduled.get())
    param = icon.param
    isNeedReadAction = icon.isNeedReadAction
    isDone = icon.isDone
    evaluatedListener = icon.evaluatedListener
    modificationCount = icon.modificationCount
  }

  internal constructor(baseIcon: Icon?,
                       param: T,
                       needReadAction: Boolean,
                       evaluator: (T) -> Icon?,
                       listener: ((DeferredIconImpl<T>, Icon) -> Unit)?) {
    this.param = param
    delegateIcon = baseIcon ?: EMPTY_ICON
    scaledDelegateIcon = delegateIcon
    cachedScaledIcon = null
    this.evaluator = evaluator
    isNeedReadAction = needReadAction
    evaluatedListener = listener
    checkDelegationDepth()
  }

  constructor(baseIcon: Icon?, param: T, needReadAction: Boolean, evaluator: com.intellij.util.Function<in T, out Icon>) :
    this(baseIcon = baseIcon, param = param, needReadAction = needReadAction, evaluator = evaluator::`fun`, listener = null)


  @ApiStatus.Internal
  constructor(baseIcon: Icon?, param: T, asyncEvaluator: suspend (T) -> Icon, listener: ((DeferredIconImpl<T>, Icon) -> Unit)?) {
    this.param = param
    delegateIcon = baseIcon ?: EMPTY_ICON
    scaledDelegateIcon = delegateIcon
    cachedScaledIcon = null
    this.evaluator = null
    this.asyncEvaluator = asyncEvaluator
    isNeedReadAction = false
    evaluatedListener = listener
    checkDelegationDepth()
  }

  override fun getModificationCount(): Long = modificationCount.get()

  override fun replaceBy(replacer: IconReplacer): Icon = DeferredIconAfterReplace(original = this, replacer = replacer)

  override fun copy(): DeferredIconImpl<T> = DeferredIconImpl(this)

  override fun scale(scale: Float): DeferredIconImpl<T> {
    if (getScale() == scale) {
      return this
    }

    var icon = cachedScaledIcon
    if (icon == null || icon.scale != scale) {
      icon = DeferredIconImpl(this)
      icon.setScale(ScaleType.OBJ_SCALE.of(scale))
      cachedScaledIcon = icon
    }
    icon.scaledDelegateIcon = scale(icon.delegateIcon, null, scale)
    return icon
  }

  override fun getBaseIcon(): Icon = delegateIcon

  private fun checkDelegationDepth() {
    var depth = 0
    var each: DeferredIconImpl<*> = this
    while (each.scaledDelegateIcon is DeferredIconImpl<*> && depth < 50) {
      depth++
      each = each.scaledDelegateIcon as DeferredIconImpl<*>
    }
    if (depth >= 50) {
      logger<DeferredIconImpl<*>>().error("Too deep deferred icon nesting")
    }
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val scaledDelegateIcon = scaledDelegateIcon
    if (!(scaledDelegateIcon is DeferredIconImpl<*> && scaledDelegateIcon.containsDeferredIconsRecursively(2))) {
      //SOE protection
      scaledDelegateIcon.paintIcon(c, g, x, y)
    }
    else {
      logger<DeferredIconImpl<*>>().warn("Not painted, too many deferrals")
    }
    if (needScheduleEvaluation()) {
      scheduleEvaluation(c, x, y)
    }
  }

  private fun containsDeferredIconsRecursively(atLeastTimes: Int): Boolean =
    atLeastTimes <= 0 ||
    scaledDelegateIcon.let { it is DeferredIconImpl<*> && it.containsDeferredIconsRecursively(atLeastTimes - 1) }

  fun currentlyPaintedIcon(): Icon {
    var result = scaledDelegateIcon
    if (result is DeferredIconImpl<*>) {
      result = result.scaledDelegateIcon
    }
    if (result is DeferredIconImpl<*>) {
      result = EMPTY_ICON // SOE protection, matches logic in 'paintIcon'
    }
    return result
  }

  override fun notifyPaint(c: Component, x: Int, y: Int) {
    if (needScheduleEvaluation()) {
      scheduleEvaluation(c, x, y)
    }
  }

  private fun needScheduleEvaluation(): Boolean = !isDone && !PowerSaveMode.isEnabled()

  @VisibleForTesting
  @RequiresEdt
  fun scheduleEvaluation(component: Component?, x: Int, y: Int): Job? {
    // It is important to extract the repaint target here:
    // the component may be a temporary component used by some list or tree to paint elements
    val repaintRequest = repaintScheduler.createRepaintRequest(component, x, y)

    if (!isDone) {
      var scheduledRepaints = scheduledRepaints
      if (scheduledRepaints == null) {
        this@DeferredIconImpl.scheduledRepaints = setOf(repaintRequest)
      }
      else {
        if (!scheduledRepaints.contains(repaintRequest)) {
          if (scheduledRepaints.size == 1) {
            scheduledRepaints = HashSet(scheduledRepaints)
          }
          (scheduledRepaints as MutableSet<RepaintRequest>).add(repaintRequest)
        }
      }
    }

    if (isScheduled.get()) {
      return null
    }
    return scheduleCalculationIfNeeded()
  }

  private fun scheduleCalculationIfNeeded(): Job? {
    if (isScheduled.getAndSet(true)) {
      return null
    }

    @Suppress("OPT_IN_USAGE")
    val coroutineScope = serviceOrNull<IconCalculatingService>()?.coroutineScope ?: GlobalScope
    return coroutineScope.launch {
      val oldWidth = scaledDelegateIcon.iconWidth
      val result = IconDeferrerImpl.evaluateDeferred {
        if (isNeedReadAction) {
          readAction { evaluate() }
        }
        else {
          evaluateAsync()
        }
      }

      scaledDelegateIcon = result
      modificationCount.incrementAndGet()
      checkDelegationDepth()

      evaluatedListener?.invoke(this@DeferredIconImpl, result)

      processRepaints(oldWidth = oldWidth, result = result)
      setDone(result)
    }
  }

  @ApiStatus.Internal
  fun triggerEvaluation() {
    scheduleCalculationIfNeeded()
  }

  @ApiStatus.Internal
  suspend fun awaitEvaluation() {
    // fast-path: note that modificationCount is incremented before setDone(),
    // but after setting scaledDelegateIcon to the evaluated value,
    // so it pretty much guarantees that the icon is ready to be painted
    if (modificationCount.get() > 0) return
    triggerEvaluation()
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      // using isDone instead of modificationCount now to avoid races, as it's EDT-only
      if (isDone) return@withContext
      val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
      try {
        suspendCancellableCoroutine { continuation ->
          val listener = object : DeferredIconListener {
            override fun evaluated(deferred: DeferredIcon, result: Icon) {
              if (deferred === this@DeferredIconImpl) {
                continuation.resume(Unit)
              }
            }
          }
          connection.subscribe(DeferredIconListener.TOPIC, listener)
        }
      }
      finally {
        connection.disconnect()
      }
    }
  }

  private suspend fun processRepaints(oldWidth: Int, result: Icon) {
    val shouldRevalidate = Registry.`is`("ide.tree.deferred.icon.invalidates.cache", true) && scaledDelegateIcon.iconWidth != oldWidth
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val repaints = scheduledRepaints
      if (result == delegateIcon || repaints == null) {
        return@withContext
      }

      for (repaintRequest in repaints) {
        val actualTarget = repaintRequest.getActualTarget() ?: continue
        // revalidate will not work: JTree caches size of nodes
        if (shouldRevalidate && actualTarget is JTree) {
          TreeUtil.invalidateCacheAndRepaint(actualTarget.ui)
        }

        //System.err.println("Repaint rectangle " + repaintRequest.getPaintingParentRec());
        repaintScheduler.scheduleRepaint(request = repaintRequest, iconWidth = iconWidth, iconHeight = iconHeight, alwaysSchedule = false)
      }
    }
  }

  private suspend fun setDone(result: Icon) {
    val deferredIconListener = ApplicationManager.getApplication().messageBus.syncPublisher(DeferredIconListener.TOPIC)
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      isDone = true
      evaluator = null
      asyncEvaluator = null
      scheduledRepaints = null
      deferredIconListener.evaluated(this@DeferredIconImpl, result)
    }
  }

  override fun retrieveIcon(): Icon {
    if (isDone || EDT.isCurrentThreadEdt()) {
      return scaledDelegateIcon
    }
    return evaluate()
  }

  override fun evaluate(): Icon = runEvaluator {
    evaluator?.invoke(param) ?: EMPTY_ICON
  }

  /**
   * Computes and returns the computed icon immediately.
   *
   * Unlike [evaluate], supports suspending evaluators, falling back to the regular one if no suspending was specified.
   */
  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun evaluateAsync(): Icon = runEvaluator {
    asyncEvaluator?.invoke(param) ?: evaluator?.invoke(param) ?: EMPTY_ICON
  }

  private inline fun runEvaluator(evaluator: () -> Icon): Icon {
    val result = try {
      evaluator()
    }
    catch (_: IndexNotReadyException) {
      EMPTY_ICON
    }

    val app = ApplicationManager.getApplication()
    if (app != null && app.isUnitTestMode) {
      @Suppress("TestOnlyProblems")
      checkDoesntReferenceThis(result)
    }
    return adjustResultWithScale(result)
  }

  private fun adjustResultWithScale(result: Icon) = if (scale != 1f && result is ScalableIcon) result.scale(scale) else result

  @TestOnly
  private fun checkDoesntReferenceThis(icon: Icon?) {
    check(icon !== this) { "Loop in icons delegation" }
    when (icon) {
      is DeferredIconImpl<*> -> {
        checkDoesntReferenceThis(icon.scaledDelegateIcon)
      }
      is LayeredIcon -> {
        for (layer in icon.allLayers) {
          checkDoesntReferenceThis(layer)
        }
      }
      is RowIcon -> {
        val count = icon.iconCount
        for (i in 0 until count) {
          checkDoesntReferenceThis(icon.getIcon(i))
        }
      }
    }
  }

  override fun getIconWidth(): Int = scaledDelegateIcon.iconWidth

  override fun getIconHeight(): Int = scaledDelegateIcon.iconHeight

  override fun getToolTip(composite: Boolean): String? = (scaledDelegateIcon as? IconWithToolTip)?.getToolTip(composite)

  override fun equals(other: Any?): Boolean {
    return when {
      this === other -> true
      other !is DeferredIconImpl<*> -> false
      else -> param == other.param
    }
  }

  override fun hashCode(): Int = param.hashCode()

  override fun toString(): String = "Deferred. Base=$scaledDelegateIcon"

  /**
   * Later, it may be needed to implement more interfaces here. Ideally, the same as in the DeferredIconImpl itself.
   */
  private class DeferredIconAfterReplace<T>(private val original: DeferredIconImpl<T>,
                                            private val replacer: IconReplacer) : ReplaceableIcon, UpdatableIcon {
    private var originalEvaluatedIcon: Icon
    private var resultIcon: Icon

    init {
      originalEvaluatedIcon = original.scaledDelegateIcon
      resultIcon = replacer.replaceIcon(originalEvaluatedIcon)
    }

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      if (original.needScheduleEvaluation()) {
        original.scheduleEvaluation(c, x, y)
      }
      else if (originalEvaluatedIcon !== original.scaledDelegateIcon) {
        originalEvaluatedIcon = original.scaledDelegateIcon
        resultIcon = replacer.replaceIcon(originalEvaluatedIcon)
      }
      resultIcon.paintIcon(c, g, x, y)
    }

    override fun getIconWidth(): Int = resultIcon.iconWidth

    override fun getIconHeight(): Int = resultIcon.iconHeight

    override fun replaceBy(replacer: IconReplacer): Icon = replacer.replaceIcon(original)

    override fun getModificationCount(): Long = original.getModificationCount()

    override fun notifyPaint(c: Component, x: Int, y: Int) {
      original.notifyPaint(c, x, y)
    }

    override fun equals(other: Any?): Boolean = when {
      this === other -> true
      other === null -> false
      else -> (other as? DeferredIconAfterReplace<*>)?.let {
        original == it.original && replacer == it.replacer
      } == true
    }

    override fun hashCode(): Int = Objects.hash(original, replacer)
  }
}

@Service(Service.Level.APP)
internal class IconCalculatingService(@JvmField val coroutineScope: CoroutineScope)
