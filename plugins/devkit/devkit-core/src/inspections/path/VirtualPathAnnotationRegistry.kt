// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.psi.PsiMethod

/**
 * Registry of methods that effectively return or accept path annotated strings.
 * This registry is used to virtually associate path annotations with methods without modifying the methods themselves.
 */
internal object VirtualPathAnnotationRegistry {
  /**
   * Map of fully qualified method names to their effective path annotation type.
   * The key is in the format "fully.qualified.ClassName.methodName".
   */
  private val methodsReturningLocalPath = setOf(
    "com.intellij.openapi.application.PathManager.getHomePath",
    "com.intellij.openapi.application.PathManager.getBinPath",
    "com.intellij.openapi.application.PathManager.getConfigPath",
    "com.intellij.openapi.application.PathManager.getSystemPath",
    "com.intellij.util.SystemProperties.getUserHome",
    "com.intellij.util.SystemProperties.getJavaHome"
  )

  /**
   * Map of fully qualified method names to their effective path annotation type for parameters.
   * The key is in the format "fully.qualified.ClassName.methodName".
   */
  private val methodsAcceptingLocalPath = setOf(
    "com.intellij.openapi.application.PathManager.isUnderHomeDirectory"
  )

  /**
   * Set of Kotlin extension method names that effectively return a `@Filename` annotated string.
   */
  private val kotlinExtensionMethodsReturningFilename = setOf(
    "name",
    "nameWithoutExtension",
    "extension"
  )

  /**
   * Set of Kotlin extension method names that effectively return a `@MultiRoutingFileSystemPath` annotated string.
   */
  private val kotlinExtensionMethodsReturningMultiRoutingPath = setOf(
    "pathString",
    "absolutePathString"
  )

  /**
   * Checks if the method effectively returns a `@LocalPath` annotated string.
   */
  fun isMethodReturningLocalPath(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    val qualifiedName = containingClass.qualifiedName ?: return false
    val methodName = method.name
    return methodsReturningLocalPath.contains("$qualifiedName.$methodName")
  }

  /**
   * Checks if the method effectively accepts a `@LocalPath` annotated string as a parameter.
   */
  fun isMethodAcceptingLocalPath(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    val qualifiedName = containingClass.qualifiedName ?: return false
    val methodName = method.name
    return methodsAcceptingLocalPath.contains("$qualifiedName.$methodName")
  }

  /**
   * Checks if the method name is a Kotlin extension method that effectively returns a `@Filename` annotated string.
   */
  fun isKotlinExtensionMethodReturningFilename(methodName: String): Boolean {
    return kotlinExtensionMethodsReturningFilename.contains(methodName)
  }

  /**
   * Checks if the method name is a Kotlin extension method that effectively returns a `@MultiRoutingFileSystemPath` annotated string.
   */
  fun isKotlinExtensionMethodReturningMultiRoutingPath(methodName: String): Boolean {
    return kotlinExtensionMethodsReturningMultiRoutingPath.contains(methodName)
  }

  /**
   * Returns all Kotlin extension method names that effectively return a `@Filename` annotated string.
   */
  fun getKotlinExtensionMethodsReturningFilename(): Set<String> {
    return kotlinExtensionMethodsReturningFilename
  }

  /**
   * Returns all Kotlin extension method names that effectively return a `@MultiRoutingFileSystemPath` annotated string.
   */
  fun getKotlinExtensionMethodsReturningMultiRoutingPath(): Set<String> {
    return kotlinExtensionMethodsReturningMultiRoutingPath
  }
}