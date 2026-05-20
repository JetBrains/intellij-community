// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

private const val EXTEND_WITH_FQN = "org.junit.jupiter.api.extension.ExtendWith"

private val DEBUG_ONLY_EXTENSIONS = mapOf(
  "com.intellij.ide.starter.junit5.RemoteDevRun" to "RemoteDevRun",
  "com.intellij.ide.starter.extended.engine.junit5.UseInstaller" to "UseInstaller",
)

@VisibleForTesting
@ApiStatus.Internal
class DebugOnlyTestExtensionInspection : DevKitUastInspectionBase(UClass::class.java, UMethod::class.java) {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowedIncludingTestSources(holder.file)
  }

  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    return check(aClass.uAnnotations, manager, isOnTheFly)
  }

  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    return check(method.uAnnotations, manager, isOnTheFly)
  }

  private fun check(annotations: List<UAnnotation>, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    var holder: ProblemsHolder? = null
    for (annotation in annotations) {
      if (annotation.qualifiedName != EXTEND_WITH_FQN) continue
      val valueExpression = annotation.findDeclaredAttributeValue("value") ?: continue
      for (classLiteral in collectClassLiterals(valueExpression)) {
        val typeFqn = classLiteral.type?.canonicalText ?: continue
        val simpleName = DEBUG_ONLY_EXTENSIONS[typeFqn] ?: continue
        val targetPsi = classLiteral.sourcePsi ?: continue
        val problemsHolder = holder ?: ProblemsHolder(manager, targetPsi.containingFile, isOnTheFly).also { holder = it }
        problemsHolder.registerProblem(
          targetPsi,
          DevKitBundle.message("inspections.debug.only.test.extension.message", simpleName),
          ProblemHighlightType.ERROR,
        )
      }
    }
    return holder?.resultsArray
  }

  private fun collectClassLiterals(expression: UExpression): List<UClassLiteralExpression> {
    val result = mutableListOf<UClassLiteralExpression>()
    expression.accept(object : AbstractUastVisitor() {
      override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
        result += node
        return false
      }
    })
    return result
  }
}
