// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.path

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.tryResolve

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

internal fun getKotlinStdlibPathsExtensionPropertyNameOrNull(expression: UQualifiedReferenceExpression): String? {
  // K2 mode
  // `maybeProperty` is `org.jetbrains.kotlin.psi.KtProperty`
  val maybeProperty = expression.selector.tryResolve()
  // `maybeProperty.containingFile` is `org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile`
  if (maybeProperty?.containingFile?.name == "PathsKt.class") {
    return expression.resolvedName
  }

  // K1 mode
  // If we use here `PsiElement` instead of `UElement` (via `expression.resolve()`),
  // we are unable to get a property name from the result object,
  // and we have only getter names (f.e. we could only get "getName", but not "name")
  val uElement = expression.resolveToUElement()
  if (uElement is UMethod) {
    if (uElement.containingClass?.qualifiedName == "kotlin.io.path.PathsKt__PathUtilsKt") {
      return uElement.nameIdentifier?.text
    }
  }

  return null
}