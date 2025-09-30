// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.fix.GradleWrapperVersionFix
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_WRAPPER_PROPERTIES_FILE_NAME

class GradleLatestMinorVersionInspection : LocalInspectionTool() {
  override fun isAvailableForFile(file: PsiFile): Boolean {
    // TODO not sure if this should be limited to "gradle-wrapper.properties" file
    return FileUtilRt.fileNameEquals(file.name, GRADLE_WRAPPER_PROPERTIES_FILE_NAME)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is Property) return
        if (element.key != "distributionUrl") return

        // extract the current Gradle version from the wrapper properties file
        val regex = "gradle-(.+)-bin\\.zip$".toRegex()
        val group = regex.find(element.text)?.groups[1] ?: return
        val currentVersion = group.value
        val versionTextRange = TextRange(group.range.first, group.range.last + 1)
        val currentGradleVersion = try {
          GradleVersion.version(currentVersion)
        }
        catch (_: IllegalArgumentException) {
          return
        }

        val latestMinorGradleVersion = GradleJvmSupportMatrix.getLatestMinorGradleVersion(currentGradleVersion.majorVersion)
        if (currentGradleVersion >= latestMinorGradleVersion) return

        holder.problem(element, GradleInspectionBundle.message("inspection.message.outdated.gradle.minor.version.descriptor"))
          .range(versionTextRange)
          .fix(GradleWrapperVersionFix(latestMinorGradleVersion))
          .register()
      }
    }
  }
}