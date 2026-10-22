// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Ref
import com.intellij.util.EventDispatcher
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Calls [ObservableState.refreshAll], which each new state needs, and returns the state.
 */
fun <T : ObservableState> T.init(): T {
  refreshAll()
  return this
}

/**
 * A set of observable properties. Each property has a default, and it can have an override.
 *
 * A setter stores an override, and the value then stays fixed until [clearOverriding]. Without an override, the
 * property holds the result of its [SyncDefaultValueCalculator], and [refresh] or [refreshAll] runs the calculator
 * again. A [CustomOutValueModifier] can change the value in both cases. Each change of a value notifies the listeners
 * of [addPropertyChangeListener].
 *
 * A subclass declares each property with `by property(...)`. An initial value is only a placeholder, so the owner of
 * the state must call [refreshAll] after the construction.
 */
// Inspired by com.intellij.openapi.components.BaseState.
abstract class ObservableState {
  // Not a SmartList, because most states have more than one property.
  private val properties: MutableList<StateProperty<Any>> = ArrayList()

  /**
   * The properties by name. [StateProperty.provideDelegate] fills it, because a property gets its name only there.
   */
  private val propertiesByName: HashMap<String, StateProperty<Any>> = HashMap()

  private var dispatcher: EventDispatcher<ObservableStateListener>? = null

  /**
   * A property whose initial value comes from [defaultValueCalculator].
   *
   * The calculator runs while the state is built, so it must not read another property of the state. That property
   * may not be initialized yet. Use the overload with an initial value then.
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
  fun <T> property(
    type: KType,
    initialValue: T,
    defaultValueCalculator: SyncDefaultValueCalculator<T>? = null,
    outValueModifier: CustomOutValueModifier<T>? = null,
    customPropertySerializer: CustomPropertySerializer<T>? = null,
    alwaysTransfer: Boolean = false,
  ): StateProperty<T> {
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
   * Runs the calculator of each property without an override. Call it after the construction, and after a change of a
   * source that no listener reports.
   */
  fun refreshAll() {
    for (property in properties) {
      property.recalculate(this)
    }
  }

  /**
   * Runs the calculator of [property] again, unless the property has an override.
   */
  fun refresh(property: KProperty<*>) {
    propertiesByName[property.name]?.recalculate(this)
  }

  /**
   * Removes the override of [property], so that the property holds its default again.
   */
  fun clearOverriding(property: KProperty<*>) {
    propertiesByName[property.name]?.clearOverriding(this)
  }

  fun isOverridden(property: KProperty<*>): Boolean {
    val stateProperty = propertiesByName[property.name]
    return stateProperty is ObjectStateProperty<*> && stateProperty.isValueOverridden
  }

  /**
   * The property with [name], or `null` when the state has none.
   */
  fun findProperty(name: String): StateProperty<Any>? {
    return propertiesByName[name]
  }

  fun addPropertyChangeListener(listener: ObservableStateListener, parentDisposable: Disposable? = null) {
    var dispatcher = this.dispatcher
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(ObservableStateListener::class.java)
      this.dispatcher = dispatcher
    }
    if (parentDisposable != null) {
      dispatcher.addListener(listener, parentDisposable)
    } else {
      dispatcher.addListener(listener)
    }
  }

  /**
   * Notifies the listeners that the value of [propertyName] changed. A property of this state calls it.
   */
  fun propertyChanged(propertyName: String?, oldValueRef: Ref<Any?>?, newValue: Any?) {
    if (propertyName != null) {
      val event = ObservableStateListener.PropertyChangeEvent(this, propertyName, oldValueRef, newValue)
      dispatcher?.multicaster?.propertyChanged(event)
    }
  }

  /**
   * Tells the listeners that the property with [propertyName] got an override, or that it lost its override.
   */
  fun overridingChanged(propertyName: String?) {
    if (propertyName != null) {
      dispatcher?.multicaster?.overridingChanged(this, propertyName)
    }
  }

  /**
   * All properties, in the declaration order. The Remote Development code sends them between the backend and the
   * frontend.
   */
  @Suppress("FunctionName")
  @Internal
  fun __getProperties(): List<StateProperty<Any>> {
    return properties
  }

  /**
   * Adds [property] to the lookup by name. The first property with a name keeps it, as a search of the list in the
   * declaration order does.
   */
  internal fun registerName(property: StateProperty<*>, name: String) {
    @Suppress("UNCHECKED_CAST")
    propertiesByName.putIfAbsent(name, property as StateProperty<Any>)
  }

  private fun <T> createProperty(
    type: KType,
    initialValue: T,
    defaultValueCalculator: SyncDefaultValueCalculator<T>,
    outValueModifier: CustomOutValueModifier<T>?,
    customPropertySerializer: CustomPropertySerializer<T>?,
    alwaysTransfer: Boolean,
  ): ObjectStateProperty<T> {
    if (customPropertySerializer == null && !hasDefaultSerializer(type)) {
      return ObjectStateProperty(initialValue, defaultValueCalculator, outValueModifier)
    }
    return TransferableObjectStateProperty(
      type = type,
      initialValue = initialValue,
      defaultValueCalculator = defaultValueCalculator,
      customOutValueModifier = outValueModifier,
      customPropertySerializer = customPropertySerializer,
      alwaysTransfer = alwaysTransfer,
    )
  }

  /**
   * Whether kotlinx can serialize a value of [type] without a custom serializer.
   */
  private fun hasDefaultSerializer(type: KType): Boolean {
    if (isNotRecommendedForSerialization(type)) {
      return false
    }
    try {
      serializer(type)
      return true
    } catch (e: Exception) {
      rethrowControlFlowException(e)
      return false
    }
  }

  private fun isNotRecommendedForSerialization(type: KType): Boolean {
    val classifier = type.classifier
    if (classifier !is KClass<*>) {
      return true
    }

    // The plain java.lang.Class API is used on purpose. kotlin-reflect members like `isSubclassOf` and `isAbstract`
    // deserialize the whole class metadata, and that can freeze the EDT (IJPL-251839).
    val javaClass = classifier.java

    // A list works, and a CharSequence does not.
    if (JAVA_LIST_CLASS.isAssignableFrom(javaClass)) {
      return false
    }

    // The JVM marks primitives, arrays, and enums with constant-specific bodies as abstract, but they are final in Kotlin.
    if (javaClass.isPrimitive || javaClass.isArray || javaClass.isEnum) {
      return false
    }

    return Modifier.isAbstract(javaClass.modifiers)
  }
}

/**
 * `kotlin.collections.List` has no runtime class of its own: it is a mapped type erased to [java.util.List],
 * so the raw Java interface is referenced here explicitly.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private val JAVA_LIST_CLASS: Class<*> = java.util.List::class.java
