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


/**
 * [replaceCacheVersionToCurrentVersion] is needed for the test [com.intellij.platform.workspace.storage.tests.metadata.serialization.MetadataSerializationTest]
 *
 * During deserialization we are comparing classes from the package [com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion]
 * with classes from the package [com.intellij.platform.workspace.storage.testEntities.entities.currentVersion]
 * But during the comparison we need to ignore the package name.
 *
 * So, [processPackageName] replaces [OLD_ENTITIES_VERSION_PACKAGE_NAME] with [NEW_ENTITIES_VERSION_PACKAGE_NAME] in the classes package name.
 */
private val replaceCacheVersionToCurrentVersion: Boolean
  get() = generatorSettings.testModeEnabled && hashIsComputing

private const val OLD_ENTITIES_VERSION_PACKAGE_NAME = "cacheVersion"

private const val NEW_ENTITIES_VERSION_PACKAGE_NAME = "currentVersion"

private fun String.processPackageName(): String =
  if (replaceCacheVersionToCurrentVersion) replace(OLD_ENTITIES_VERSION_PACKAGE_NAME, NEW_ENTITIES_VERSION_PACKAGE_NAME) else this


private var hashIsComputing = false

internal fun startHashComputing() {
  hashIsComputing = true
}

internal fun endHashComputing() {
  hashIsComputing = false
}