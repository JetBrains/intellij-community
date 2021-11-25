// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import org.jetbrains.plugins.gradle.config.GradleFileType
import org.jetbrains.plugins.groovy.codeInspection.FileTypeInspectionDisabler
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection

private val DISABLEABLE_INSPECTIONS : Map<out String, Set<Class<out LocalInspectionTool>>> = mapOf(
  GradleFileType.name to setOf(
    GrUnresolvedAccessInspection::class.java,
    GroovyAssignabilityCheckInspection::class.java,
  )
)

class GradleFileTypeInspectionDisabler : FileTypeInspectionDisabler {
  override fun getDisableableInspections(): Map<out FileType, Set<Class<out LocalInspectionTool>>> = DISABLEABLE_INSPECTIONS.mapKeys {
    FileTypeRegistry.getInstance().findFileTypeByName(it.key)
  }
}