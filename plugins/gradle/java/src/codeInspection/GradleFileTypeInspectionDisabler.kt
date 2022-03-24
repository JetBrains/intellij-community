// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.groovy.codeInspection.FileTypeInspectionDisabler
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

private val DISABLEABLE_INSPECTIONS : Set<Class<out LocalInspectionTool>> = setOf(
    GrUnresolvedAccessInspection::class.java,
    GroovyAssignabilityCheckInspection::class.java,
)

class GradleFileTypeInspectionDisabler : FileTypeInspectionDisabler {
  override fun getDisableableInspections(): Set<Class<out LocalInspectionTool>> = if (shouldDisable) DISABLEABLE_INSPECTIONS else emptySet()
}

private var shouldDisable: Boolean = true

object GradleDisablerTestUtils {
  @TestOnly
  @JvmStatic
  fun enableAllDisableableInspections(disposable: Disposable) {
    val previousValue = shouldDisable
    Disposer.register(disposable) { shouldDisable = previousValue }
    shouldDisable = false
  }
}
