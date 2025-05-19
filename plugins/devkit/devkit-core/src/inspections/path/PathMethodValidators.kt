// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner

internal fun isPathConstructorOrFactory(method: PsiElement): Boolean {
  val methods = mapOf(
    "java.nio.file.Path" to setOf("of", "get"),
    "java.nio.file.Paths" to setOf("get"),
  )
  // Check if the method is a Path constructor or factory method like Path.of()
  if (method is PsiModifierListOwner) {
    val containingClass = (method as? com.intellij.psi.PsiMember)?.containingClass
    if (containingClass != null) {
      return methods[containingClass.qualifiedName]?.contains(method.name) == true
    }
  }
  return false
}

internal fun isPathResolveMethod(method: PsiElement): Boolean {
  // Check if the method is Path.resolve() or other similar methods
  if (method is com.intellij.psi.PsiMethod) {
    val containingClass = method.containingClass
    if (containingClass != null && containingClass.qualifiedName == "java.nio.file.Path") {
      // List of Path methods that can take either String or Path parameters
      val pathMethods = setOf("resolve", "resolveSibling", "startsWith", "endsWith")
      return pathMethods.contains(method.name)
    }
  }
  return false
}

internal fun isFileSystemGetPathMethod(method: PsiElement): Boolean {
  // Check if the method is FileSystem.getPath()
  if (method is com.intellij.psi.PsiMethod) {
    val containingClass = method.containingClass
    if (containingClass != null && containingClass.qualifiedName == "java.nio.file.FileSystem") {
      return method.name == "getPath"
    }
  }
  return false
}