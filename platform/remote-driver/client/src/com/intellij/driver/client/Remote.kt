package com.intellij.driver.client

import org.intellij.lang.annotations.Language

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
  @Language("jvm-class-name")
  val value: String,

  /**
   * Fully qualified name of service class in the IDE under test, use only if differs from target class.
   */
  val serviceInterface: String = "",

  /**
   * Identifier of a plugin where the class is located, e.g. `com.intellij.spring`.
   * If the class is declared in a module of a plugin (not main), use the following format: `some.plugin.id/some.plugin.id.submodule`.
   */
  val plugin: String = ""
)