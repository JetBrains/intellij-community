package org.jetbrains.deft.annotations

/**
 * Disallow references as values for this field.
 * It is useful to enforce tree structure.
 *
 * In other words, this modifier restricts reference as:
 * - `@Child val x: Any` - `1:1`
 * - `@Child val x: Any?` - `1:0..1`
 * - `val x: Children<Any>` - `1:*`
 *
 * Values assigned to `@Child` or `Children` will cause updating `parent` of new value.
 * So, it should be removed from previous parent before adding to new one.
 *
 * Example:
 * ```kotlin
 * interface Module {
 *   @Child val kotlin: Kotlin
 * }
 *
 * val m0 = Module {
 *   kotlin = Kotlin { ... } // ok
 * }
 *
 * val anotherKotlin = Kotlin { ... }
 * val m1 = Module {
 *      kotlin = anotherKotlin // ok
 * }
 * val m2 = Module {
 *      kotlin = anotherKotlin // error: `anotherKotlin` already added as @Child `kotlin` in `m1`
 * }
 * m1.kotlin = null
 * m2.kotlin = anotherKotlin // now ok
 * ```
 *
 * Same as `child` [modifier](https://deft.jb.gg/docs/templates.html#field-modifiers) in Deft.
 *
 * ```deft
 * child def submodules: ...module
 * ```
 *
 * ## Opposite
 *
 * `parent` and `@Child`/`Children` actually are basic predefined opposites (see [OppositeFor]).
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class Child