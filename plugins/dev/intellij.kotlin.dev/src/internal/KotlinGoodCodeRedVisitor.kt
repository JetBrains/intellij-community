// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.dev.kotlin.internal

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.dev.codeInsight.internal.GoodCodeRedVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtDiagnosticCheckerFilter
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitor

internal class KotlinGoodCodeRedVisitor : GoodCodeRedVisitor {

  override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor {

    return object : KtVisitor<Unit, Unit>() {
      override fun visitFile(file: PsiFile) {
        super.visitFile(file)
        analyze(file as KtFile) {
          val diagnostics = file.collectDiagnosticsForFile(KtDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
          for (diagnostic in diagnostics) {
            if (diagnostic.severity == Severity.ERROR) {
              holder.registerProblem(diagnostic.psi, diagnostic.defaultMessage)
            }
          }
        }
      }
    }
  }
}