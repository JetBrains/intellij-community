// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.dev.kotlin.internal

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.dev.codeInsight.internal.GoodCodeRedVisitor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitor

internal class KotlinGoodCodeRedVisitor : GoodCodeRedVisitor {

  override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor {

    return object : KtVisitor<Unit, Unit>() {
      override fun visitFile(file: PsiFile) {
        super.visitFile(file)
        try {
          analyze(file as KtFile) {
            val diagnostics = file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            for (diagnostic in diagnostics) {
              if (diagnostic.severity == KaSeverity.ERROR) {
                holder.registerProblem(diagnostic.psi, diagnostic.defaultMessage)
              }
            }
          }
        }
        catch (e: Exception) {
          if (Logger.shouldRethrow(e)) throw e
          holder.registerProblem(file, KotlinDevBundle.message("inspection.message.analysis.failed.with.exception", e.message))
        }
      }
    }
  }
}