// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType

internal fun String.withDoubleQuotes(): String = "\"$this\""

internal fun String.escapeDollar(): String = replace("$", "\\$")

internal fun List<String>.allWithDoubleQuotesAndEscapedDollar(): List<String> =
  map { it.escapeDollar().withDoubleQuotes() }.toList()

internal fun getJavaFullName(className: String, moduleName: String): String =
  "$moduleName.$className".escapeDollar().withDoubleQuotes()

internal val ObjClass<*>.fullName: String
  get() = getJavaFullName(name, module.name)

internal val ValueType<*>.javaPrimitiveType: String
  get() = this.javaClass.typeName.substringAfter('$')

internal val ValueType.JvmClass<*>.name: String
  get() = javaClassName.escapeDollar().withDoubleQuotes()

internal val ValueType.JvmClass<*>.superClasses: List<String>
  get() = javaSuperClasses.allWithDoubleQuotesAndEscapedDollar()