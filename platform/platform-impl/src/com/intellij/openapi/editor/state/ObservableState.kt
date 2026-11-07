// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Ref
import com.intellij.util.EventDispatcher
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A set of named properties that any thread can read and change. A subclass declares each property with
 * `by property(...)`.
 *
 * Each property has a default, which its [SyncDefaultValueCalculator] gives. A setter stores an override, and the
 * property then holds the override until [clearOverriding]. A [CustomOutValueModifier] can change the value in both
 * cases. The state does not watch the sources of a default, so its owner calls [refresh] or [refreshAll] after a change
 * of a source.
 *
 * Features:
 * - [getSnapshot] gives all values and overrides at one moment, as an immutable [StateSnapshot]. A read takes no lock.
 * - A [batch] changes several properties at once. Another thread sees all the changes of a batch, or none of them.
 * - Changes on different threads run one after another, so no change gets lost.
 * - A listener of [addPropertyChangeListener] gets each change of a value and each change of an override. The rules
 *   for the events are in [ObservableStateListener].
 * - [findProperty] finds a property by its name.
 *
 * Rules for a subclass and its owner:
 * - The owner calls [refreshAll] after the construction, on the thread that built the state. An initial value is only a
 *   placeholder, and the first refresh also freezes the set of properties.
 * - The subclass must not read a property while it is built. A property that registers after the first read fails.
 * - The subclass must not give `this` to another thread while it is built, because the registration takes no lock.
 * - Each value must be immutable, because a snapshot keeps only the reference.
 * - A calculator and a modifier follow the rules of [SyncDefaultValueCalculator].
 */
// Inspired by com.intellij.openapi.components.BaseState.
abstract class ObservableState {
  /**
   * The properties in the declaration order, while the subclass is built. The first snapshot freezes them in a
   * [StateLayout], and all later reads use the layout.
   */
  private val properties: MutableList<StateProperty<Any>> = ArrayList()

  /**
   * The current values, or `null` before the first read.
   */
  @Volatile private var snapshot: StateSnapshot? = null

  /**
   * Makes the batches on different threads run one after another.
   */
  private val writeLock: ReentrantLock = ReentrantLock()

  /**
   * The outermost open [batch], or `null`. Only the thread that holds [writeLock] uses it.
   */
  private var openBatch: StateBatch? = null

  /**
   * The listeners of [addPropertyChangeListener].
   */
  private val dispatcher: EventDispatcher<ObservableStateListener> = EventDispatcher.create(ObservableStateListener::class.java)

  /**
   * A property whose initial value also comes from [defaultValueCalculator].
   *
   * So the calculator runs while the state is built, and it must not read a property of the state. Use the overload
   * with an initial value then.
   */
  inline fun <reified T> property(noinline defaultValueCalculator: () -> T): StateProperty<T> {
    return property(defaultValueCalculator(), SyncDefaultValueCalculator(defaultValueCalculator))
  }

  /**
   * A property with the placeholder [initialValue]. [refreshAll] replaces it with the result of [defaultValueCalculator].
   * Use this overload when the calculator reads another property of the state.
   */
  inline fun <reified T> property(initialValue: T, noinline defaultValueCalculator: () -> T): StateProperty<T> {
    return property(initialValue, SyncDefaultValueCalculator(defaultValueCalculator))
  }

  /**
   * A property with all options. Without [defaultValueCalculator], the default is [initialValue].
   *
   * [customPropertySerializer], or a kotlinx serializer for [T], makes the property transferable to the Remote
   * Development frontend. [alwaysTransfer] makes the backend send the value also without an override.
   */
  inline fun <reified T> property(
    initialValue: T,
    defaultValueCalculator: SyncDefaultValueCalculator<T>? = null,
    outValueModifier: CustomOutValueModifier<T>? = null,
    customPropertySerializer: CustomPropertySerializer<T>? = null,
    alwaysTransfer: Boolean = false,
  ): StateProperty<T> {
    return property(
      type = typeOf<T>(),
      initialValue = initialValue,
      defaultValueCalculator = defaultValueCalculator,
      outValueModifier = outValueModifier,
      customPropertySerializer = customPropertySerializer,
      alwaysTransfer = alwaysTransfer,
    )
  }

  /**
   * The part of [property] that needs no reified type. [type] is the type of the value, for its serializer.
   */
  @PublishedApi
  internal fun <T> property(
    type: KType,
    initialValue: T,
    defaultValueCalculator: SyncDefaultValueCalculator<T>? = null,
    outValueModifier: CustomOutValueModifier<T>? = null,
    customPropertySerializer: CustomPropertySerializer<T>? = null,
    alwaysTransfer: Boolean = false,
  ): StateProperty<T> {
    check(snapshot == null) {
      "A property registers after the first read of the state"
    }
    val property = createProperty(
      type = type,
      initialValue = initialValue,
      defaultValueCalculator = defaultValueCalculator ?: FixedDefaultValue(initialValue),
      outValueModifier = outValueModifier,
      customPropertySerializer = customPropertySerializer,
      alwaysTransfer = alwaysTransfer,
    )
    @Suppress("UNCHECKED_CAST")
    properties.add(property as StateProperty<Any>)
    return property
  }

  /**
   * Runs the calculator of each property without an override, in one [batch]. Call it after the construction, and after
   * a change of a source that no listener reports.
   */
  fun refreshAll() {
    batch {
      for (property in currentSnapshot().layout().properties()) {
        property.recalculate(this)
      }
    }
  }

  /**
   * Runs [action] as one change of the state.
   *
   * - This thread sees each change of [action] at once.
   * - Another thread sees all the changes of [action] when the batch ends, or none of them before.
   * - A change on another thread waits until the batch ends.
   * - The listeners get the events after the batch ends, in their order.
   * - Then the listeners get all value changes of the batch in one call.
   * - A nested batch is a part of the outermost batch.
   * - When [action] fails, the changes before the failure stay, and their events come.
   */
  fun batch(action: () -> Unit) {
    writeLock.lock()
    if (openBatch != null) {
      try {
        action()
      } finally {
        writeLock.unlock()
      }
      return
    }
    val base = currentSnapshot()
    val batch = StateBatch(base)
    openBatch = batch
    try {
      action()
    } finally {
      openBatch = null
      try {
        publish(base, batch)
      } finally {
        writeLock.unlock()
      }
      for (event in batch.events()) {
        event()
      }
      val valueEvents = batch.valueEvents()
      if (valueEvents.isNotEmpty()) {
        dispatcher.multicaster.propertiesChanged(valueEvents)
      }
    }
  }

  /**
   * The current values and overrides. Inside a [batch], the snapshot does not show the changes of the batch yet.
   */
  open fun getSnapshot(): StateSnapshot {
    return currentSnapshot()
  }

  /**
   * Runs the calculator of [property] again, unless the property has an override. [property] must be a property of
   * this state.
   */
  fun refresh(property: KProperty<*>) {
    requireProperty(property).recalculate(this)
  }

  /**
   * Removes the override of [property], so that the property holds its default again. [property] must be a property of
   * this state.
   */
  fun clearOverriding(property: KProperty<*>) {
    requireProperty(property).clearOverriding(this)
  }

  /**
   * Whether [property] has an override.
   */
  fun isOverridden(property: KProperty<*>): Boolean {
    val stateProperty = findProperty(property.name) ?: return false
    return isOverriddenAt(stateProperty.index)
  }

  /**
   * The property with [name], or `null` when the state has none.
   */
  fun findProperty(name: String): StateProperty<Any>? {
    return currentSnapshot().layout().findProperty(name)
  }

  /**
   * Adds [listener]. With [parentDisposable], the listener goes away when [parentDisposable] is disposed.
   */
  fun addPropertyChangeListener(listener: ObservableStateListener, parentDisposable: Disposable? = null) {
    if (parentDisposable != null) {
      dispatcher.addListener(listener, parentDisposable)
    } else {
      dispatcher.addListener(listener)
    }
  }

  /**
   * Notifies the listeners that the value of [propertyName] changed. A property of this state calls it inside a [batch],
   * and the event comes after the batch ends.
   */
  internal fun propertyChanged(propertyName: String?, oldValueRef: Ref<Any?>?, newValue: Any?) {
    if (propertyName != null) {
      val event = ObservableStateListener.PropertyChangeEvent(this, propertyName, oldValueRef, newValue)
      val batch = requireBatchOfThisThread()
      batch.addEvent { dispatcher.multicaster.propertyChanged(event) }
      batch.addValueEvent(event)
    }
  }

  /**
   * Tells the listeners that the property with [propertyName] got an override, or that it lost its override. A property
   * of this state calls it inside a [batch], and the event comes after the batch ends.
   */
  internal fun overridingChanged(propertyName: String?) {
    if (propertyName != null) {
      requireBatchOfThisThread().addEvent { dispatcher.multicaster.overridingChanged(this, propertyName) }
    }
  }

  /**
   * All properties, in the declaration order. The Remote Development code sends them between the backend and the
   * frontend.
   */
  @Suppress("FunctionName")
  fun __getProperties(): List<StateProperty<Any>> {
    return currentSnapshot().layout().properties()
  }

  /**
   * The value at [index] for this thread: the value in the open batch of this thread, or else in the snapshot.
   */
  internal fun valueAt(index: Int): Any? {
    val batch = batchOfThisThread()
    if (batch != null) {
      return batch.valueAt(index)
    }
    return currentSnapshot().valueAt(index)
  }

  /**
   * Whether the property at [index] has an override, for this thread. See [valueAt].
   */
  internal fun isOverriddenAt(index: Int): Boolean {
    return overrideAt(index) !== NoOverride
  }

  /**
   * The override at [index] for this thread, or [NoOverride]. See [valueAt].
   */
  internal fun overrideAt(index: Int): Any? {
    val batch = batchOfThisThread()
    if (batch != null) {
      return batch.overrideAt(index)
    }
    return currentSnapshot().overrideAt(index)
  }

  /**
   * Stores [value] at [index] in the open batch of this thread.
   */
  internal fun storeValue(index: Int, value: Any?) {
    requireBatchOfThisThread().storeValue(index, value)
  }

  /**
   * Stores [override] at [index] in the open batch of this thread.
   */
  internal fun storeOverride(index: Int, override: Any?) {
    requireBatchOfThisThread().storeOverride(index, override)
  }

  /**
   * Removes the override at [index] in the open batch of this thread.
   */
  internal fun clearOverrideAt(index: Int) {
    requireBatchOfThisThread().storeOverride(index, NoOverride)
  }

  /**
   * The current snapshot. The state uses it and not [getSnapshot], which a subclass can override.
   */
  private fun currentSnapshot(): StateSnapshot {
    return snapshot ?: createFirstSnapshot()
  }

  /**
   * The open batch, when this thread runs it. Another thread must not read [openBatch], so the lock owner is checked
   * first.
   */
  private fun batchOfThisThread(): StateBatch? {
    if (!writeLock.isHeldByCurrentThread) {
      return null
    }
    return openBatch
  }

  private fun requireProperty(property: KProperty<*>): StateProperty<Any> {
    return requireNotNull(findProperty(property.name)) { "The state has no property ${property.name}" }
  }

  private fun requireBatchOfThisThread(): StateBatch {
    return checkNotNull(batchOfThisThread()) { "A state changes only inside a batch" }
  }

  /**
   * Creates a snapshot of [values] and [overrides]. A subclass can give a snapshot with typed accessors.
   */
  internal open fun createSnapshot(layout: StateLayout, values: Array<Any?>, overrides: Array<Any?>): StateSnapshot {
    return StateSnapshot(layout, values, overrides)
  }

  /**
   * Publishes the changes of [batch], which started from [base], in a new snapshot. The caller holds [writeLock], so no
   * other publication runs at the same time.
   */
  private fun publish(base: StateSnapshot, batch: StateBatch) {
    if (!batch.hasChanges()) {
      return
    }
    snapshot = createSnapshot(base.layout(), batch.values(), batch.overrides())
  }

  /**
   * Freezes the properties, and creates the snapshot with the initial values and without overrides, unless another
   * thread has created it.
   */
  private fun createFirstSnapshot(): StateSnapshot {
    writeLock.lock()
    try {
      val current = snapshot
      if (current != null) {
        return current
      }
      val layout = StateLayout.freeze(this, properties)
      val values = Array<Any?>(properties.size) { properties[it].initialValue }
      val overrides = Array<Any?>(properties.size) { NoOverride }
      val first = createSnapshot(layout, values, overrides)
      snapshot = first
      return first
    } finally {
      writeLock.unlock()
    }
  }

  private fun <T> createProperty(
    type: KType,
    initialValue: T,
    defaultValueCalculator: SyncDefaultValueCalculator<T>,
    outValueModifier: CustomOutValueModifier<T>?,
    customPropertySerializer: CustomPropertySerializer<T>?,
    alwaysTransfer: Boolean,
  ): ObjectStateProperty<T> {
    val index = properties.size
    if (customPropertySerializer == null && !TransferableObjectStateProperty.hasDefaultSerializer(type)) {
      return ObjectStateProperty(
        this,
        index,
        initialValue,
        defaultValueCalculator,
        outValueModifier,
      )
    }
    return TransferableObjectStateProperty(
      state = this,
      index = index,
      type = type,
      initialValue = initialValue,
      defaultValueCalculator = defaultValueCalculator,
      customOutValueModifier = outValueModifier,
      customPropertySerializer = customPropertySerializer,
      alwaysTransfer = alwaysTransfer,
    )
  }
}
