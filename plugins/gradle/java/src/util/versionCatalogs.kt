// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PropertyUtilBase

internal fun getCapitalizedAccessorName(method: PsiMethod): String? {
  val propertyName = PropertyUtilBase.getPropertyName(method) ?: return null
  val methodFinalPart = StringUtil.capitalize(propertyName)
  val methodParts = method.containingClass?.takeUnless { it.name?.startsWith(LIBRARIES_FOR_PREFIX) == true }?.name?.trimAccessorName()
  return (methodParts ?: "") + methodFinalPart
}

private fun String.trimAccessorName(): String {
  for (suffix in listOf(BUNDLE_ACCESSORS_SUFFIX, LIBRARY_ACCESSORS_SUFFIX, PLUGIN_ACCESSORS_SUFFIX, VERSION_ACCESSORS_SUFFIX)) {
    if (endsWith(suffix)) return substringBeforeLast(suffix)
  }
  return this
}

internal const val BUNDLE_ACCESSORS_SUFFIX = "BundleAccessors"
internal const val LIBRARY_ACCESSORS_SUFFIX = "LibraryAccessors"
internal const val PLUGIN_ACCESSORS_SUFFIX = "PluginAccessors"
internal const val VERSION_ACCESSORS_SUFFIX = "VersionAccessors"

internal const val LIBRARIES_FOR_PREFIX = "LibrariesFor"

