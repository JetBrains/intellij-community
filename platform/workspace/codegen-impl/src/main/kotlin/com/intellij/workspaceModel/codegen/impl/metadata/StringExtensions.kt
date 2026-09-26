// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.impl.metadata

import com.intellij.workspaceModel.codegen.deft.meta.ObjClass
import com.intellij.workspaceModel.codegen.deft.meta.ValueType
import com.intellij.workspaceModel.codegen.impl.dsl.GeneratorContext

internal fun String.withDoubleQuotes(): String = "\"$this\""

internal fun String.escapeDollar(): String = replace("$", "\\$")

internal fun GeneratorContext.allWithDoubleQuotesAndEscapedDollar(strings: List<String>): List<String> =
  strings.map { processPackageName(it).escapeDollar().withDoubleQuotes() }.toList()

internal fun GeneratorContext.getJavaFullName(className: String, moduleName: String): String =
  "${processPackageName(moduleName)}.$className".escapeDollar().withDoubleQuotes()

internal fun GeneratorContext.getFullName(objClass: ObjClass<*>): String = getJavaFullName(objClass.name, objClass.module.name)

internal val ValueType<*>.javaPrimitiveType: String
  get() = this.javaClass.typeName.substringAfter('$')

internal fun GeneratorContext.getName(valueTypeJvmClass: ValueType.JvmClass<*>): String = processPackageName(valueTypeJvmClass.javaClassName).escapeDollar().withDoubleQuotes()

internal fun GeneratorContext.getSuperClasses(valueTypeJvmClass: ValueType.JvmClass<*>): List<String> = allWithDoubleQuotesAndEscapedDollar(valueTypeJvmClass.javaSuperClasses)


/**
 * [replaceCacheVersionToCurrentVersion] is needed for the test [com.intellij.platform.workspace.storage.tests.metadata.serialization.MetadataSerializationTest]
 *
 * During deserialization we are comparing classes from the package [com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion]
 * with classes from the package [com.intellij.platform.workspace.storage.testEntities.entities.currentVersion]
 * But during the comparison we need to ignore the package name.
 *
 * So, [processPackageName] replaces [OLD_ENTITIES_VERSION_PACKAGE_NAME] with [NEW_ENTITIES_VERSION_PACKAGE_NAME] in the classes package name.
 */
private fun GeneratorContext.getReplaceCacheVersionToCurrentVersion(): Boolean = testModeEnabled && hashIsComputing

private const val OLD_ENTITIES_VERSION_PACKAGE_NAME = "cacheVersion"

private const val NEW_ENTITIES_VERSION_PACKAGE_NAME = "currentVersion"

private fun GeneratorContext.processPackageName(packageName: String): String =
  if (getReplaceCacheVersionToCurrentVersion()) packageName.replace(OLD_ENTITIES_VERSION_PACKAGE_NAME,
                                                                    NEW_ENTITIES_VERSION_PACKAGE_NAME)
  else packageName


private var hashIsComputing = false

internal fun startHashComputing() {
  hashIsComputing = true
}

internal fun endHashComputing() {
  hashIsComputing = false
}