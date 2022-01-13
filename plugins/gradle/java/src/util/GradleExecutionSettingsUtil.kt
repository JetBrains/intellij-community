// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleExecutionSettingsUtil")

package org.jetbrains.plugins.gradle.util

import com.intellij.execution.Location
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage

private fun createTestFilter(filter: String): String {
  return String.format("--tests %s", filter)
}

private fun createTestFilterFrom(filter: String): String {
  val escaped = filter.replace('"', '*')
  val wrapped = String.format("\"%s\"", escaped)
  return createTestFilter(wrapped)
}

private fun createLocationName(aClass: String?, method: String?): String {
  if (aClass == null) return ""
  val escapedMethod = method?.replace('.', '*')
  return aClass + if (escapedMethod == null) "" else ".$escapedMethod"
}

fun createTestFilterFromMethod(aClass: String?, method: String?): String {
  return createTestFilterFrom(createLocationName(aClass, method))
}

fun createTestFilterFromClass(aClass: String?): String {
  if (aClass == null) return ""
  return createTestFilterFrom(aClass)
}

fun createTestWildcardFilter(): String {
  return createTestFilter("*")
}

fun createTestFilterFromPackage(aPackage: String): String {
  if (aPackage.isEmpty()) return createTestWildcardFilter()
  val packageFilter = String.format("%s.*", aPackage)
  return createTestFilterFrom(packageFilter)
}

fun createTestFilterFrom(psiClass: PsiClass): String {
  return createTestFilterFromClass(psiClass.getRuntimeQualifiedName())
}

fun createTestFilterFrom(psiClass: PsiClass, methodName: String?): String {
  return createTestFilterFromMethod(psiClass.getRuntimeQualifiedName(), methodName)
}

fun createTestFilterFrom(psiClass: PsiClass, psiMethod: PsiMethod): String {
  return createTestFilterFrom(psiClass, psiMethod.name)
}

fun createTestFilterFrom(psiPackage: PsiPackage): String {
  return createTestFilterFromPackage(psiPackage.qualifiedName)
}

fun createTestFilterFrom(location: Location<*>?, psiClass: PsiClass?, psiMethod: PsiMethod?): String {
  val className = psiClass?.getRuntimeQualifiedName()
  val methodName = psiMethod?.name
  var locationName = createLocationName(className, methodName)
  if (location is PsiMemberParameterizedLocation) {
    val wrappedParamSetName = location.paramSetName
    if (wrappedParamSetName.isNotEmpty()) {
      val paramSetName = wrappedParamSetName
        .removeSurrounding("[", "]")
      locationName += "[*$paramSetName*]"
    }
  }
  else if (psiClass != null && psiClass.isParameterized()) {
    locationName += "[*]"
  }
  return createTestFilterFrom(locationName)
}

private fun PsiClass.getRuntimeQualifiedName(): String? {
  return when (val parent = parent) {
    is PsiClass -> parent.getRuntimeQualifiedName() + "$" + name
    else -> qualifiedName
  }
}

private fun PsiClass.isParameterized(): Boolean {
  val annotation = JUnitUtil.getRunWithAnnotation(this)
  return annotation != null && JUnitUtil.isParameterized(annotation)
}
