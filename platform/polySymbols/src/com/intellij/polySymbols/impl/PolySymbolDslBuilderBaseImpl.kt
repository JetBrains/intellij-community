// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.polySymbols.DependencyHandle
import com.intellij.polySymbols.PolySymbolDslBuilderBase
import com.intellij.psi.PsiElement
import javax.swing.Icon

internal abstract class PolySymbolDslBuilderBaseImpl : PolySymbolDslBuilderBase {

  internal abstract val builderContext: String

  internal val depSpecs: MutableList<DepSpec<*>> = mutableListOf()

  internal var priorityValue: PolySymbol.Priority? = null
  internal var priorityGetter: (() -> PolySymbol.Priority?)? = null

  internal var apiStatusValue: PolySymbolApiStatus? = null
  internal var apiStatusGetter: (() -> PolySymbolApiStatus)? = null

  internal var modifiersValue: Set<PolySymbolModifier>? = null
  internal var modifiersGetter: (() -> Set<PolySymbolModifier>)? = null

  internal var iconValue: Icon? = null
  internal var iconGetter: (() -> Icon?)? = null

  internal val propertyValues: MutableMap<PolySymbolProperty<*>, Any?> = mutableMapOf()
  internal val propertyGetters: MutableMap<PolySymbolProperty<*>, () -> Any?> = mutableMapOf()

  override fun priority(value: PolySymbol.Priority?) {
    priorityValue = value
    priorityGetter = null
  }

  override fun priority(provider: () -> PolySymbol.Priority?) {
    checkNoPsiCapture(provider, "polySymbol.priority")
    priorityGetter = provider
  }

  override fun apiStatus(value: PolySymbolApiStatus) {
    apiStatusValue = value
    apiStatusGetter = null
  }

  override fun apiStatus(provider: () -> PolySymbolApiStatus) {
    checkNoPsiCapture(provider, "polySymbol.apiStatus")
    apiStatusGetter = provider
  }

  override fun modifiers(value: Set<PolySymbolModifier>) {
    modifiersValue = value
    modifiersGetter = null
  }

  override fun modifiers(provider: () -> Set<PolySymbolModifier>) {
    checkNoPsiCapture(provider, "polySymbol.modifiers")
    modifiersGetter = provider
  }

  override fun icon(value: Icon?) {
    iconValue = value
    iconGetter = null
  }

  override fun icon(provider: () -> Icon?) {
    checkNoPsiCapture(provider, "polySymbol.icon")
    iconGetter = provider
  }

  override fun <T : Any> property(property: PolySymbolProperty<T>, value: T?) {
    propertyValues[property] = value
    propertyGetters -= property
  }

  override fun <T : Any> property(property: PolySymbolProperty<T>, provider: () -> T?) {
    checkNoPsiCapture(provider, "polySymbol.property[${property.name}]")
    propertyValues -= property
    propertyGetters[property] = provider
  }

  override fun <T : PsiElement> dependency(element: T): DependencyHandle<T> {
    val idx = depSpecs.size
    depSpecs += DepSpec.FromPsiElement(element)
    return DependencyHandleImpl(idx)
  }

  override fun <T : Any> dependency(`object`: T, pointerProvider: (T) -> Pointer<out T>): DependencyHandle<T> {
    val idx = depSpecs.size
    depSpecs += DepSpec.FromGenericObject(`object`, pointerProvider)
    return DependencyHandleImpl(idx)
  }
}
