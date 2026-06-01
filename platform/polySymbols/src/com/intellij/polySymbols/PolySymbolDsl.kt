// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.documentation.PolySymbolDocumentationBuilder
import com.intellij.polySymbols.impl.PolySymbolBuilderImpl
import com.intellij.polySymbols.patterns.PolySymbolPatternBuilder
import com.intellij.polySymbols.patterns.polySymbolPattern
import com.intellij.polySymbols.query.PolySymbolWithPattern
import com.intellij.polySymbols.search.PsiLinkedPolySymbol
import com.intellij.polySymbols.utils.PolySymbolDeclaredInPsi
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import kotlin.reflect.KProperty
import kotlin.reflect.safeCast

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class PolySymbolDsl

/**
 * Builds a [PolySymbol] with a declarative, read-action-safe DSL.
 *
 * The produced instance additionally implements one of
 * [PolySymbolWithPattern], [PsiLinkedPolySymbol], or [PolySymbolDeclaredInPsi]
 * when [PolySymbolBuilder.pattern], [PolySymbolBuilder.linkWithPsiElement], or
 * [PolySymbolBuilder.declaredInPsi] is called inside the [body]. Calling any
 * two of those mode methods throws [IllegalStateException].
 */
fun polySymbol(
  kind: PolySymbolKind,
  name: String,
  body: PolySymbolBuilder.() -> Unit,
): BuiltPolySymbol =
  PolySymbolBuilderImpl(kind, name).apply(body).build()

@ApiStatus.NonExtendable
interface BuiltPolySymbol: PolySymbol {

  operator fun <T : Any> get(handle: DependencyHandle<T>): T

}

/**
 * Base class shared by every PolySymbol-building DSL. Collects raw
 * dependency references (PsiElement / PolySymbol) without eagerly creating
 * pointers — pointers are only allocated when the materialized symbol's
 * [PolySymbol.createPointer] is invoked.
 */
@PolySymbolDsl
@ApiStatus.NonExtendable
interface PolySymbolDslBuilderBase {

  /**
   * @see [PolySymbol.priority]
   */
  fun priority(value: PolySymbol.Priority?)

  /**
   * @see [PolySymbol.priority]
   */
  fun priority(provider: () -> PolySymbol.Priority?)

  /**
   * @see [PolySymbol.apiStatus]
   */
  fun apiStatus(value: PolySymbolApiStatus)

  /**
   * @see [PolySymbol.apiStatus]
   */
  fun apiStatus(provider: () -> PolySymbolApiStatus)

  /**
   * @see [PolySymbol.modifiers]
   */
  fun modifiers(value: Set<PolySymbolModifier>)

  /**
   * @see [PolySymbol.modifiers]
   */
  fun modifiers(provider: () -> Set<PolySymbolModifier>)

  /**
   * @see [PolySymbol.icon]
   */
  fun icon(value: Icon?)

  /**
   * @see [PolySymbol.icon]
   */
  fun icon(provider: () -> Icon?)

  /**
   * @see [PolySymbol.get]
   */
  fun <T : Any> property(property: PolySymbolProperty<T>, value: T?)

  /**
   * @see [PolySymbol.get]
   */
  fun <T : Any> property(property: PolySymbolProperty<T>, provider: () -> T?)

  /**
   * Declare a [PsiElement] dependency.
   *
   * Using this method allows for automatic management of pointers.
   *
   * Example usage:
   * ```kotlin
   *
   * val variable: JSVariable // JSVariable is a PsiElement
   *
   * polySymbol(JS_SYMBOLS, "variable") {
   *   val variable by dependency(variable)
   *   property(JSTypeProperty) {
   *     variable.jsType
   *   }
   * }
   * ```
   * @see [DependencyHandle]
   */
  fun <T : PsiElement> dependency(element: T): DependencyHandle<T>

  /**
   * Declare a generic dependency by providing the current value and a pointer provider.
   *
   * Using this method allows for automatic management of pointers.
   *
   * @see [DependencyHandle]
   */
  fun <T : Any> dependency(`object`: T, pointerProvider: (T) -> Pointer<out T>): DependencyHandle<T>
}

/**
 * Declare a [PolySymbol] dependency. The [Pointer] created from the provided [symbol]
 * must dereference to the [T] class. If that's not
 * true, use the overload with the custom pointer provider.
 *
 * Using this method allows for automatic management of pointers.
 *
 * Example usage:
 * ```kotlin
 * val source: PolySymbol
 *
 * polySymbol(JS_SYMBOLS, "variable") {
 *   val source by dependency(source)
 *   property(JSTypeProperty) {
 *     source[JSTypeProperty]
 *   }
 * }
 * ```
 * @see [DependencyHandle]
 */
inline fun <reified T : PolySymbol> PolySymbolDslBuilderBase.dependency(symbol: T): DependencyHandle<T> =
  dependency(symbol) {
    val symbolPointer = it.createPointer()
    val symbolClass = T::class
    Pointer {
      symbolClass.safeCast(symbolPointer.dereference())
    }
  }

/**
 * Opaque handle returned by [PolySymbolDslBuilderBase.dependency] calls inside a PolySymbol DSL
 * builder block. Accessing a handle outside a builder method throws [IllegalStateException].
 *
 * You may use it with `by` syntax, get value property directly, or invoke it to get the value.
 *
 * Examples:
 * ```kotlin
 * val element: JSVariable // JSVariable is a PsiElement
 *
 * // using `by` syntax
 * polySymbol(JS_SYMBOLS, "variable") {
 *   val element by dependency(element)
 *   property(JSTypeProperty) {
 *     element.jsType
 *   }
 * }
 *
 * // using invoke on handle
 * polySymbol(JS_SYMBOLS, "variable") {
 *   val element = dependency(element)
 *   property(JSTypeProperty) {
 *     element().jsType
 *   }
 * }
 *
 * // using value property on handle
 * polySymbol(JS_SYMBOLS, "variable") {
 *   val element = dependency(element)
 *   property(JSTypeProperty) {
 *     element.value.jsType
 *   }
 * }
 * ```
 */
@PolySymbolDsl
@ApiStatus.NonExtendable
interface DependencyHandle<T : Any> {

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T

  val value: T

  operator fun invoke(): T
}

/** Value returned by the lambda form of [PolySymbolBuilder.declaredInPsi]. */
data class PolySymbolDeclarationSite(
  val sourceElement: PsiElement,
  val textRangeInSourceElement: TextRange,
)

@PolySymbolDsl
@ApiStatus.NonExtendable
interface PolySymbolBuilder : PolySymbolDslBuilderBase {

  /**
   * @see [PolySymbol.extension]
   */
  fun extension(value: Boolean)

  /**
   * @see [PolySymbol.extension]
   */
  fun extension(provider: () -> Boolean)

  /**
   * Override the symbol's [PolySymbol.psiContext].
   *
   * Only meaningful when neither [linkWithPsiElement] nor [declaredInPsi]
   * is used — those modes already wire [PolySymbol.psiContext] to [PsiLinkedPolySymbol.linkedElement]/
   * [PolySymbolDeclaredInPsi.sourceElement] respectively. Calling this setter in either of those
   * modes throws [IllegalStateException] at `build()`.
   *
   * @see [PolySymbol.psiContext]
   */
  fun psiContext(value: PsiElement?)

  /**
   * Override the symbol's [PolySymbol.psiContext].
   *
   * Only meaningful when neither [linkWithPsiElement] nor [declaredInPsi]
   * is used — those modes already wire [PolySymbol.psiContext] to [PsiLinkedPolySymbol.linkedElement]/
   * [PolySymbolDeclaredInPsi.sourceElement] respectively. Calling this setter in either of those
   * modes throws [IllegalStateException] at `build()`.
   *
   * @see [PolySymbol.psiContext]
   */
  fun psiContext(provider: () -> PsiElement?)

  /**
   * @see [PolySymbol.presentation]
   */
  fun presentation(value: TargetPresentation)

  /**
   * @see [PolySymbol.presentation]
   */
  fun presentation(provider: () -> TargetPresentation)

  /**
   * Marks this symbol as search target using generic
   * [com.intellij.polySymbols.search.PolySymbolSearchTarget.create].
   *
   * @see [PolySymbol.searchTarget]
   */
  fun searchTarget()

  /**
   * Marks this symbol as rename target using generic
   * [com.intellij.polySymbols.refactoring.PolySymbolRenameTarget.create].
   *
   * @see [PolySymbol.renameTarget]
   */
  fun renameTarget()

  /**
   * Provide symbol's documentation through a [PolySymbolDocumentationBuilder].
   *
   * To get a value of a handler created with [dependency] method, access it with `get` operator
   * on the provided [BuiltPolySymbol] instance:
   * ```kotlin
   *
   * val variable: JSVariable // JSVariable is a PsiElement
   *
   * polySymbol(JS_SYMBOLS, "variable") {
   *   val variable by dependency(variable)
   *   documentation { symbol, location ->
   *     val type = symbol[variable].jsType
   *   }
   * }
   * ```
   * @see [PolySymbol.getDocumentationTarget]
   */
  fun documentation(
    builder: PolySymbolDocumentationBuilder.(symbol: BuiltPolySymbol, location: PsiElement?) -> Unit,
  )

  /**
   * @see [PolySymbol.getNavigationTargets]
   */
  fun navigationTargets(provider: (project: Project) -> Collection<NavigationTarget>)

  /**
   * @see [PolySymbol.matchContext]
   */
  fun matchContext(provider: (context: PolyContext) -> Boolean)

  /**
   * Provides additional [PolySymbol.isEquivalentTo] implementation.
   *
   * @see [PolySymbol.isEquivalentTo]
   */
  fun isEquivalentTo(provider: (symbol: Symbol) -> Boolean)

  /**
   * Attach a pattern. The lambda runs lazily inside the materialized symbol's
   * dependency scope, so it may reference `by dependency(...)` handles
   * declared on this builder.
   *
   * @see [PolySymbolWithPattern]
   * @see [polySymbolPattern]
   */
  fun pattern(body: PolySymbolPatternBuilder.() -> Unit)

  /**
   * Define a [PsiLinkedPolySymbol].
   *
   * @see [PsiLinkedPolySymbol]
   */
  fun linkWithPsiElement(element: PsiElement)

  /**
   * Define a [PsiLinkedPolySymbol].
   *
   * @see [PsiLinkedPolySymbol]
   */
  fun linkWithPsiElement(provider: () -> PsiElement?)

  /**
   * Define a [PolySymbolDeclaredInPsi].
   *
   * @see [PolySymbolDeclaredInPsi]
   */
  fun declaredInPsi(element: PsiElement, range: TextRange)

  /**
   * Define a [PolySymbolDeclaredInPsi].
   *
   * @see [PolySymbolDeclaredInPsi]
   */
  fun declaredInPsi(provider: () -> PolySymbolDeclarationSite?)
}
