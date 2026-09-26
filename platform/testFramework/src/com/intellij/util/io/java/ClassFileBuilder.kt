// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.java

import com.intellij.util.io.DirectoryContentBuilder
import org.jetbrains.jps.model.java.LanguageLevel
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.reflect.KClass

/**
 * Produces class-file with qualified name [name] in a place specified by [content]. If the class is not from the default package,
 * the produced file will be placed in a sub-directory according to its package.
 */
inline fun DirectoryContentBuilder.classFile(name: String, content: ClassFileBuilder.() -> Unit) {
  val builder = MethodHandles.lookup().findConstructor(
    ClassFileBuilder::class.java.classLoader.loadClass("com.intellij.util.io.java.impl.ClassFileBuilderImpl"),
    MethodType.methodType(Void.TYPE, String::class.java),
  ).invoke(name) as ClassFileBuilder

  builder.content()
  builder.generate(this)
}

abstract class ClassFileBuilder {

  var javaVersion: LanguageLevel = LanguageLevel.JDK_1_8
  var superclass: String = "java.lang.Object"
  var interfaces: List<String> = emptyList()
  var access: AccessModifier = AccessModifier.PUBLIC

  abstract fun field(name: String, type: KClass<*>, access: AccessModifier = AccessModifier.PRIVATE)

  /**
   * Adds a field which type is a class with qualified name [type]
   */
  abstract fun field(name: String, type: String, access: AccessModifier = AccessModifier.PRIVATE)

  abstract fun generate(targetRoot: DirectoryContentBuilder)
}

enum class AccessModifier { PRIVATE, PUBLIC, PROTECTED, PACKAGE_LOCAL }