package org.jetbrains.deft.annotations

/**
 * Enables syntax sugar to allow write
 *
 * ```kotlin
 * Module {
 *     dependencies {
 *          add(c) // where c is another module
 *
 *          // instead of:
 *          add(Dependency { target = c })
 *     }
 * }
 * ```
 *
 * Example:
 * ```kotlin
 * interface Dependency {
 *  @Relation.Target val target: Module
 * }
 *
 * interface Module {
 *  @Relation val dependencies: List<Dependency>
 * }
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class RelationDSL(val field: String)