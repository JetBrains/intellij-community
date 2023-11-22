// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.openapi.components.Service.Level
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class IncorrectServiceRetrievingInspection : ServiceRetrievingInspectionBase() {

  override val additionalComponentManagerMethodNames
    get() = arrayOf("getServiceIfCreated")

  override val additionalServiceKtFileMethodNames
    get() = arrayOf("serviceOrNull", "serviceIfCreated")

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {

      override fun visitCallExpression(node: UCallExpression): Boolean {
        val (howServiceRetrieved, serviceClass) = getServiceRetrievingInfo(node) ?: return true
        val serviceLevel = getLevelType(holder.project, serviceClass)
        if (serviceLevel == LevelType.MODULE) return true
        if (serviceLevel == LevelType.NOT_REGISTERED) {
          serviceClass.qualifiedName?.let { className ->
            val message = DevKitBundle.message("inspection.incorrect.service.retrieving.not.registered", className)
            holder.registerUProblem(node, message)
          }
        }
        else if (!isServiceRetrievedCorrectly(serviceLevel, howServiceRetrieved)) {
          val message = when (howServiceRetrieved) {
            Level.APP -> DevKitBundle.message("inspection.incorrect.service.retrieving.mismatch.for.project.level")
            Level.PROJECT -> DevKitBundle.message("inspection.incorrect.service.retrieving.mismatch.for.app.level")
          }
          holder.registerUProblem(node, message)
        }
        return true
      }
    }, arrayOf(UCallExpression::class.java))
  }

  private fun isServiceRetrievedCorrectly(serviceLevel: LevelType, howServiceRetrieved: Level): Boolean {
    return serviceLevel == LevelType.NOT_SPECIFIED ||
           when (howServiceRetrieved) {
             Level.APP -> serviceLevel.isApp()
             Level.PROJECT -> serviceLevel.isProject()
           }
  }
}
