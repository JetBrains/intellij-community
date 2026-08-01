// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Ref
import com.intellij.util.EventDispatcher
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * `kotlin.collections.List` has no runtime class of its own: it is a mapped type erased to [java.util.List],
 * so the raw Java interface is referenced here explicitly.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
private val JAVA_LIST_CLASS: Class<*> = java.util.List::class.java

fun <T : ObservableState> T.init(): T {
  refreshAll()
  return this
}

/**
 * Do not forget to call [refreshAll] manually after the constructor invocation
 * to rewrite properties initialized by "initial" values with "default" values.
 * You can use [init] extension method for convenience.
 */
// inspired by com.intellij.openapi.components.BaseState
@Experimental
@Internal
abstract class ObservableState {

  // do not use SmartList because most objects have more than 1 property
  private val properties: MutableList<StateProperty<Any>> = ArrayList()

  private var myDispatcher: EventDispatcher<ObservableStateListener>? = null

  private fun <T> addProperty(p: StateProperty<T>): StateProperty<T> {
    @Suppress("UNCHECKED_CAST")
    properties.add(p as StateProperty<Any>)
    return p
  }

  /**
   * You MUST NOT refer to other properties of the same state inside [defaultValueCalculator] here, because this
   * lambda will be called during construction of the state and some properties are not initialized yet.
   * You can use `property(initialValue, noinline defaultValueCalculator)` in your case.
   */
  inline fun <reified T> property(noinline defaultValueCalculator: () -> T): StateProperty<T> = property(
    defaultValueCalculator(), SyncDefaultValueCalculator(defaultValueCalculator))

  inline fun <reified T> property(alwaysTransfer: Boolean, noinline defaultValueCalculator: () -> T): StateProperty<T> = property(
    defaultValueCalculator(), SyncDefaultValueCalculator(defaultValueCalculator), alwaysTransfer = alwaysTransfer)

  /**
   * You MUST NOT refer to other properties while calculating [initialValue] here, because normally this method
   * is called during construction of the state and some properties are not initialized yet.
   * Pass any stub value into [initialValue] parameter here and do not forget to call [refreshAll] after the state creation.
   * This will replace all properties filled by [initialValue] with values calculated by [defaultValueCalculator].
   */
  inline fun <reified T> property(initialValue: T, noinline defaultValueCalculator: () -> T): StateProperty<T> = property(
    initialValue, SyncDefaultValueCalculator(defaultValueCalculator))

  inline fun <reified T> property(initialValue: T,
                                  defaultValueCalculator: SyncDefaultValueCalculator<T>? = null,
                                  outValueModifier: CustomOutValueModifier<T>? = null,
                                  customPropertySerializer: CustomPropertySerializer<T>? = null,
                                  alwaysTransfer: Boolean = false): StateProperty<T> = property(
    typeOf<T>(), initialValue, defaultValueCalculator, outValueModifier, customPropertySerializer, alwaysTransfer)

  fun <T> property(clazz: KType,
                   initialValue: T,
                   defaultValueCalculator: SyncDefaultValueCalculator<T>? = null,
                   outValueModifier: CustomOutValueModifier<T>? = null,
                   customPropertySerializer: CustomPropertySerializer<T>? = null,
                   alwaysTransfer: Boolean = false): StateProperty<T> {
    val property = createProperty(clazz, initialValue,
                                  defaultValueCalculator ?: FixedDefaultValue(initialValue),
                                  outValueModifier, customPropertySerializer, alwaysTransfer)
    return addProperty(property)
  }

  private fun <T> createProperty(clazz: KType,
                                 initialValue: T,
                                 defaultValueCalculator: SyncDefaultValueCalculator<T>,
                                 outValueModifier: CustomOutValueModifier<T>?,
                                 customPropertySerializer: CustomPropertySerializer<T>?,
                                 alwaysTransfer: Boolean = false): ObjectStateProperty<T> {
    if (customPropertySerializer != null)
      return TransferableObjectStateProperty(clazz, initialValue, defaultValueCalculator, outValueModifier, customPropertySerializer,
                                             alwaysTransfer)

    val defaultSerializer = if (isNotRecommendedForSerialization(clazz)) null
    else try {
      serializer(clazz)
    }
    catch (e: Exception) {
      null
    }

    return if (defaultSerializer != null)
      TransferableObjectStateProperty(clazz, initialValue, defaultValueCalculator, outValueModifier, null, alwaysTransfer)
    else
      ObjectStateProperty(initialValue, defaultValueCalculator, outValueModifier)
  }

  private fun isNotRecommendedForSerialization(clazz: KType): Boolean {
    val classifier = clazz.classifier
    if (classifier !is KClass<*>) return true

    // plain java.lang.Class API is used here on purpose: kotlin-reflect members like `isSubclassOf`/`isAbstract`
    // deserialize the whole class metadata and may freeze the EDT (IJPL-251839)
    val javaClass = classifier.java

    // Lists work fine, CharSequences - no
    if (JAVA_LIST_CLASS.isAssignableFrom(javaClass)) return false

    // the JVM marks primitives, arrays and enums with constant-specific bodies as abstract, while they are final in Kotlin terms
    if (javaClass.isPrimitive || javaClass.isArray || javaClass.isEnum) return false

    return Modifier.isAbstract(javaClass.modifiers)
  }

  fun refreshAll() {
    for (property in properties) {
      property.recalculate(this)
    }
  }

  fun addPropertyChangeListener(listener: ObservableStateListener, parentDisposable: Disposable? = null) {
    var dispatcher = myDispatcher
    if (dispatcher == null) {
      dispatcher = EventDispatcher.create(ObservableStateListener::class.java)
      myDispatcher = dispatcher
    }

    if (parentDisposable != null) dispatcher.addListener(listener, parentDisposable)
    else dispatcher.addListener(listener)
  }

  fun propertyChanged(propertyName: String?, oldValueRef: Ref<Any?>?, newValue: Any?) {
    if (propertyName != null)
      myDispatcher?.multicaster?.propertyChanged(ObservableStateListener.PropertyChangeEvent(this, propertyName, oldValueRef, newValue))
  }

  fun clearOverriding(property: KProperty<*>) {
    // fixme O(n)
    properties.find { it.name == property.name }?.clearOverriding(this)
  }

  fun refresh(property: KProperty<*>) {
    // fixme O(n)
    properties.find { it.name == property.name }?.recalculate(this)
  }

  // internal usage only
  @Suppress("FunctionName")
  @Internal
  fun __getProperties(): MutableList<StateProperty<Any>> = properties
}