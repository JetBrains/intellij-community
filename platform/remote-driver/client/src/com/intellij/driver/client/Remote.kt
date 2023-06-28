package com.intellij.driver.client

/**
 * Describes a class, service or utility available in the IDE under test via remote JMX connection
 *
 * @see Driver
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Remote(
  /**
   * Fully qualified name of class in the IDE under test.
   */
  val value: String,

  /**
   * Identifier of a plugin where the class is located, e.g. `com.intellij.spring`.
   */
  val plugin: String = ""
)