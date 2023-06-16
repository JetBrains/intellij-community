// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleExecutionSettingsUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.execution.Location
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.util.text.nullize
import org.jetbrains.annotations.VisibleForTesting
import java.util.*


fun createTestWildcardFilter(): String {
  return "--tests *"
}

private fun createTestFilter(filter: String): String {
  val escaped = filter.replace('"', '*')
  return "--tests \"$escaped\""
}

@VisibleForTesting
fun createTestFilter(className: String?, methodName: String?, paramName: String?): String {
  if (className.isNullOrEmpty()) {
    return createTestWildcardFilter()
  }
  if (methodName.isNullOrEmpty()) {
    return createTestFilter(className)
  }
  val escapedMethod = methodName.replace('.', '*')
  if (paramName.isNullOrEmpty()) {
    return createTestFilter("$className.$escapedMethod")
  }
  return createTestFilter("$className.$escapedMethod$paramName")
}

@VisibleForTesting
fun createTestFilterFromPackage(packageName: String): String {
  if (packageName.isEmpty()) {
    return createTestWildcardFilter()
  }
  return createTestFilter("$packageName.*")
}

fun createTestFilterFrom(psiClass: PsiClass): String {
  return createTestFilterFrom(psiClass, null)
}

fun createTestFilterFrom(psiClass: PsiClass, methodName: String?): String {
  val className = psiClass.getRuntimeQualifiedName()
  return createTestFilter(className, methodName, null)
}

fun createTestFilterFrom(psiMethod: PsiMethod): String {
  val psiClass = psiMethod.containingClass!!
  val methodName = psiMethod.name
  return createTestFilterFrom(psiClass, methodName)
}

fun createTestFilterFrom(psiPackage: PsiPackage): String {
  val packageName = psiPackage.qualifiedName
  return createTestFilterFromPackage(packageName)
}

fun createTestFilterFrom(location: Location<*>?, psiClass: PsiClass?, psiMethod: PsiMethod?): String {
  val className = psiClass?.getRuntimeQualifiedName()
  val methodName = psiMethod?.name
  var parameterName: String? = null
  if (location is PsiMemberParameterizedLocation) {
    parameterName = location.paramSetName?.nullize()
    if (parameterName != null && parameterName.removeSurrounding("[", "]") == parameterName) {
      parameterName = "[*$parameterName*]"
    }
  }
  else if (psiClass != null && psiClass.isJunit4ParameterizedClass()) {
    parameterName = "[*]"
  }
  return createTestFilter(className, methodName, parameterName)
}

private fun PsiClass.getRuntimeQualifiedName(): String? {
  val classes = getParents().filterIsInstance<PsiClass>()
  val joiner = StringJoiner("$")
  val rootClass = classes.last()
  val qualifiedName = rootClass.qualifiedName ?: return null
  joiner.add(qualifiedName)
  for (innerClass in classes.dropLast(1).asReversed()) {
    val name = innerClass.name ?: return null
    joiner.add(name)
  }
  return joiner.toString()
}

private fun PsiElement.getParents(): List<PsiElement> {
  val parents = ArrayList<PsiElement>()
  var element: PsiElement? = this
  while (element != null) {
    parents.add(element)
    element = element.parent
  }
  return parents
}

private fun PsiClass.isJunit4ParameterizedClass(): Boolean {
  val annotation = JUnitUtil.getRunWithAnnotation(this)
  return annotation != null && JUnitUtil.isParameterized(annotation)
}
