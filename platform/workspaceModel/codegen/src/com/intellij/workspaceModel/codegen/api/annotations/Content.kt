package org.jetbrains.deft.annotations

/**
 * Enables syntax sugar for implicit adding object to current parent.
 * Causes @Child to be applied also.
 *
 * Example:
 * ```kotlin
 * interface Module {
 *   @Content val submodules: List<Module>
 * }
 *
 * Module {
 *   Module { ... }
 *   Module { ... }
 * }
 * ```
 *
 * Same as `content` [modifier](https://deft.jb.gg/docs/templates.html#field-modifiers) in Deft.
 *
 * ```deft
 * content def submodules: ...Module
 * ```
 *
 * ## Implementation
 *
 * Achieved by adding function to generated Builder interface:
 *
 * ```kotlin
 * fun Module(init: Module.() -> Unit) {
 *  submodules.add(Module.Companion.invoke(init))
 * }
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Content