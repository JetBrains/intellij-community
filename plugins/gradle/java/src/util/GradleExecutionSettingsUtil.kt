// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.execution.Location
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.psi.*

object GradleExecutionSettingsUtil {

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

  @JvmStatic
  fun createTestFilterFromMethod(aClass: String?, method: String?): String {
    return createTestFilterFrom(createLocationName(aClass, method))
  }

  @JvmStatic
  fun createTestFilterFromClass(aClass: String?): String {
    if (aClass == null) return ""
    return createTestFilterFrom(aClass)
  }

  @JvmStatic
  fun createTestFilterFromPackage(aPackage: String): String {
    if (aPackage.isEmpty()) return createTestFilter("*")
    val packageFilter = String.format("%s.*", aPackage)
    return createTestFilterFrom(packageFilter)
  }

  @JvmStatic
  fun createTestFilterFrom(psiClass: PsiClass): String {
    return createTestFilterFromClass(psiClass.getRuntimeQualifiedName())
  }

  @JvmStatic
  fun createTestFilterFrom(aClass: PsiClass, psiMethod: PsiMethod): String {
    return createTestFilterFromMethod(aClass.getRuntimeQualifiedName(), psiMethod.name)
  }

  @JvmStatic
  fun createTestFilterFrom(psiPackage: PsiPackage): String {
    return createTestFilterFromPackage(psiPackage.qualifiedName)
  }

  @JvmStatic
  fun createTestFilterFrom(location: Location<*>?, aClass: PsiClass, method: PsiMethod): String {
    var locationName = createLocationName(aClass.getRuntimeQualifiedName(), method.name)
    if (location is PsiMemberParameterizedLocation) {
      val wrappedParamSetName = location.paramSetName
      if (wrappedParamSetName.isNotEmpty()) {
        val paramSetName = wrappedParamSetName
          .removeSurrounding("[", "]")
        locationName += "[*$paramSetName*]"
      }
    }
    else if (aClass.isParameterized()) {
      locationName += "[*]"
    }
    return createTestFilterFrom(locationName)
  }

  private fun PsiClass.getRuntimeQualifiedName(): String? {
    val parent = parent
    return when (parent) {
      is PsiClass -> parent.getRuntimeQualifiedName() + "$" + name
      else -> qualifiedName
    }
  }

  private fun PsiClass.isParameterized(): Boolean {
    val annotation = JUnitUtil.getRunWithAnnotation(this)
    return annotation != null && JUnitUtil.isParameterized(annotation)
  }
}
