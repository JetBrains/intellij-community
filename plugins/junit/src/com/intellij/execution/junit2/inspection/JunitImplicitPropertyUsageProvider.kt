// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit2.inspection

import com.intellij.execution.junit.JUnitUtil
import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NonNls

class JunitImplicitPropertyUsageProvider: ImplicitPropertyUsageProvider {
  private val JUNIT_PROPERTIES: @NonNls Set<String> = ContainerUtil.immutableSet("junit.jupiter.execution.parallel.enabled",
                                                                                        "junit.jupiter.execution.parallel.mode.default",
                                                                                        "junit.jupiter.execution.parallel.config.dynamic.factor")

  override fun isUsed(property: Property): Boolean {
    val isJunitProperty = ContainerUtil.exists(JUNIT_PROPERTIES,
                         Condition {prop -> StringUtil.equalsIgnoreCase(property.key, prop)})
    if (!isJunitProperty) return false

    val project = property.project
    return JUnitUtil.isJUnit5(GlobalSearchScope.allScope(project), project)
  }
}
