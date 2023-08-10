package com.intellij.driver.client

/**
 * Specifies name of the span for OpenTelemetry tracer on the method of [Remote] interface.
 *
 * @see Driver
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Timed(
  val value: String
)