package org.jetbrains.deft.annotations

/**
 * Opens specified type or property for subclassing or overriding.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Open

@Target(AnnotationTarget.CLASS)
annotation class Abstract

@Target(AnnotationTarget.CLASS)
annotation class Enum

@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class Check