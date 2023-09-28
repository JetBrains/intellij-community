// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.kotlin.util.getContext
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

internal class UsePlatformProcessAwaitExitInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!isAllowed(holder)) return PsiElementVisitor.EMPTY_VISITOR

    return object : KtVisitorVoid() {

      override fun visitCallExpression(expression: KtCallExpression) {
        if (isJavaLangProcessForbiddenMethodCall(expression)) {
          holder.registerProblem(expression,
                                 DevKitKotlinBundle.message("inspection.use.platform.process.await.exit.display.name")
          )
        }
      }

      private fun isJavaLangProcessForbiddenMethodCall(expression: KtCallExpression): Boolean {
        if (getContext(expression).isSuspending()) {
          analyze(expression) {
            val callNameExpression = expression.getCallNameExpression()?.text ?: return false
            if (isNotForbidden(callNameExpression)) return false // optimization to avoid resolving
            val calledSymbol = expression.resolveCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol
            if (calledSymbol !is KtNamedSymbol) return false
            val calledMethodName = calledSymbol.name.identifier
            if (isNotForbidden(calledMethodName)) return false
            if (calledSymbol.valueParameters.isNotEmpty()) return false
            val className = (calledSymbol.getContainingSymbol()?.psi as? PsiClass)?.qualifiedName ?: return false
            return className == "java.lang.Process"
          }
        }
        return false
      }
    }
  }

  private fun isNotForbidden(methodName: String): Boolean {
    return methodName != "waitFor" && methodName != "onExit"
  }

  private fun isAllowed(holder: ProblemsHolder): Boolean {
    val project = holder.project
    return DevKitInspectionUtil.isAllowed(holder.file) &&
           KotlinTopLevelFunctionFqnNameIndex["com.intellij.util.io.awaitExit", project, holder.file.resolveScope]
             .isNotEmpty()
  }
}
