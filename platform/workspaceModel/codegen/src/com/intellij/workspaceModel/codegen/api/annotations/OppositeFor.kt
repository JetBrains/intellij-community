package org.jetbrains.deft.annotations

/**
 * ```kotlin
 * interface A {
 *  val b: B
 * }
 * interface B {
 *  @OppositeFor("b") val a: List<A>
 * }
 * ```
 *
 * ```deft
 * A {
 *  def b: B
 * }
 *
 * B {
 *  opposite-for(A/b) def a: ...A
 * }
 * ```
 *
 * ## Parent and children
 *
 * `parent` and `@Child`/`Children` actually are basic predefined opposites.
 */
annotation class OppositeFor(val propertyName: String)