// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.gradle.codeInspection.suppression.GradleSuspendTypecheckingService
import org.jetbrains.plugins.gradle.service.resolve.getLinkedGradleProjectPath
import org.jetbrains.plugins.groovy.codeInspection.FileTypeInspectionDisabler

private val DISABLEABLE_INSPECTIONS: Set<Class<out LocalInspectionTool>> = setOf(
  // Gradle inspections are enabled back, since most of the yellow code was resolved
  //GrUnresolvedAccessInspection::class.java,
  //GroovyAssignabilityCheckInspection::class.java,
)

class GradleFileTypeInspectionDisabler : FileTypeInspectionDisabler {

  override fun getDisableableInspections(): Set<Class<out LocalInspectionTool>> = if (shouldDisable) DISABLEABLE_INSPECTIONS else emptySet()

  override fun isTypecheckingDisabled(file: PsiFile): Boolean {
    return lastImportHasErrors(file)
  }
}

private fun lastImportHasErrors(file: PsiFile): Boolean {
  val linkedProjectPath = file.getLinkedGradleProjectPath() ?: return false
  return file.project.service<GradleSuspendTypecheckingService>().isSuspended(linkedProjectPath)
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
