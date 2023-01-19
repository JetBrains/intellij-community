// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInspection.toml

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.codeInspection.GradleInspectionBundle
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlVisitor
import org.toml.lang.psi.ext.name

class UnusedVersionCatalogEntryInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : TomlVisitor() {

      override fun visitKeySegment(element: TomlKeySegment) {
        val containingFile = element.containingFile ?: return
        if (containingFile.virtualFile !in getVersionCatalogFiles(element.project).values) {
          return
        }

        val headerName = element.parentOfType<TomlKeyValue>()?.parent.asSafely<TomlTable>()?.header?.key?.name
          ?.let { name -> VersionCatalogHeader.values().find { it.repr == name } } ?: return

        val usage = ReferencesSearch.search(element).findFirst()

        if (usage != null) {
          return
        }
        val message = when (headerName) {
          VersionCatalogHeader.LIBRARIES -> GradleInspectionBundle.message("inspection.message.unused.dependency.descriptor", element.name)
          VersionCatalogHeader.PLUGINS -> GradleInspectionBundle.message("inspection.message.unused.plugin.descriptor", element.name)
          VersionCatalogHeader.VERSIONS -> GradleInspectionBundle.message("inspection.message.unused.version.reference.descriptor",
                                                                          element.name)
        }
        holder.registerProblem(element, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
      }

    }
  }
}

private enum class VersionCatalogHeader(val repr : @NonNls String) {
  LIBRARIES("libraries"),
  PLUGINS("plugins"),
  VERSIONS("versions")
}