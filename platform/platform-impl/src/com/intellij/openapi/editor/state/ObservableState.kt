// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Ref
import com.intellij.util.EventDispatcher
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.typeOf

fun <T: ObservableState> T.init() : T {
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

  /**
   * You MUST NOT refer to other properties while calculating [initialValue] here, because normally this method
   * is called during construction of the state and some properties are not initialized yet.
   * Pass any stub value into [initialValue] parameter here and do not forget to call [refreshAll] after the state creation.
   * This will replace all properties filled by [initialValue] with values calculated by [defaultValueCalculator].
   */
  inline fun <reified T> property(initialValue: T, noinline defaultValueCalculator: () -> T): StateProperty<T> = property(
    initialValue, SyncDefaultValueCalculator(defaultValueCalculator))

  inline fun <reified T> property(initialValue: T,
                                  defaultValueCalculator: SyncDefaultValueCalculator<T>,
                                  outValueModifier: CustomOutValueModifier<T>? = null): StateProperty<T> = property(
    typeOf<T>(), initialValue, defaultValueCalculator, outValueModifier)

  inline fun <reified T> property(defaultValue: T): StateProperty<T> = property(
    typeOf<T>(), defaultValue, FixedDefaultValue(defaultValue))

  fun <T> property(clazz: KType,
                   initialValue: T,
                   defaultValueCalculator: SyncDefaultValueCalculator<T>,
                   outValueModifier: CustomOutValueModifier<T>? = null): StateProperty<T> {
    val property = createProperty(clazz, initialValue, defaultValueCalculator, outValueModifier)
    return addProperty(property)
  }

  private fun <T> createProperty(clazz: KType,
                                        initialValue: T,
                                        defaultValueCalculator: SyncDefaultValueCalculator<T>,
                                        outValueModifier: CustomOutValueModifier<T>?): ObjectStateProperty<T> {
    val serializer = try {
      serializer(clazz)
    }
    catch (e: Exception) {
      null
    }

    return if (serializer != null)
      TransferableObjectStateProperty(clazz, initialValue, defaultValueCalculator, outValueModifier)
    else
      ObjectStateProperty(initialValue, defaultValueCalculator, outValueModifier)
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