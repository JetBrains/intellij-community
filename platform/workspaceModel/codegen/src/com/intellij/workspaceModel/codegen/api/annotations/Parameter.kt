package org.jetbrains.deft.annotations

/**
 * Enables syntax sugar for defining value of that property with `Something(value)`
 * instead of `Something { field = value }`.
 *
 * Example:
 * ```kotlin
 * interface Dependency {
 *    @Primary val target: Module
 * }
 *
 * Dependency { target = m }
 * Dependency(m) // same as previous, works only for @Primary fields
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
annotation class Parameter
