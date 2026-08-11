package com.intellij.driver.client

import com.intellij.driver.model.RdTarget
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
   * Identifier of a module where the class is located.
   *
   * If the class is located in the core classloader (its module is embedded into the platform), the field is not required.
   * If the class is located in a non-embedded platform module, use the following format: `com.intellij/intellij.some.platform.module`.
   *
   * If the class is located in a plugin, use the following format: `some.plugin.id/some.plugin.module`.
   * If the class is located in an embedded module (the main plugin classloader or Plugin Model V1), use the following format: `some.plugin.id`.
   *
   * See [Plugin Model](https://youtrack.jetbrains.com/articles/IJPL-A-31/Plugin-Model) documentation.
   * @See com.intellij.driver.impl.Invoker#getClassLoader
   */
  val plugin: String = "",

  /**
   * Determine the semantics for the remote call in case of Remote IDE.
   */
  val rdTarget: RdTarget = RdTarget.DEFAULT,
)