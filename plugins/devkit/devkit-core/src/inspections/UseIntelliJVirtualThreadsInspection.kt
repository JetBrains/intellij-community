// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.generate.getUastElementFactory
import org.jetbrains.uast.generate.replace
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Suggest using IntelliJVirtualThreads.ofVirtual instead of Thread.ofVirtual
 */
class UseIntelliJVirtualThreadsInspection : DevKitUastInspectionBase() {
  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isAllowed(holder.file) &&
           DevKitInspectionUtil.isClassAvailable(holder, INTELLIJ_VIRTUAL_THREADS_FQN)
  }

  public override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.getLanguage(), object : AbstractUastNonRecursiveVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        val method = node.takeIf {
          it.methodName == "ofVirtual"
        }?.resolve() ?: return super.visitCallExpression(node)
        val clazz = method.containingClass ?: return super.visitCallExpression(node)
        if (clazz.qualifiedName != THREAD_FQN) {
          return super.visitCallExpression(node)
        }

        val psi = node.sourcePsi
        if (psi != null) {
          val fixes: Array<LocalQuickFix> = if (psi.getLanguage().`is`(JavaLanguage.INSTANCE) || psi.language.id == "kotlin") {
            arrayOf(ReplaceWithIntelliJVirtualThreadsFix())
          }
          else {
            LocalQuickFix.EMPTY_ARRAY
          }
          holder.registerProblem(psi, DevKitBundle.message("inspection.use.intellij.virtual.threads.message"), *fixes)
        }
        return super.visitCallExpression(node)
      }
    }, HINTS)
  }

  private class ReplaceWithIntelliJVirtualThreadsFix : LocalQuickFix {
    @IntentionFamilyName
    override fun getFamilyName(): @IntentionFamilyName String {
      return DevKitBundle.message("inspection.use.intellij.virtual.threads.fix.family.name")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
      val element = descriptor.psiElement
      val uCall = element.toUElement() ?: return
      val factory = uCall.getUastElementFactory(project) ?: return
      val newReceiver = factory.createQualifiedReference(INTELLIJ_VIRTUAL_THREADS_FQN, uCall.sourcePsi) ?: return
      val newCall = factory.createCallExpression(
        receiver = newReceiver,
        methodName = "ofVirtual",
        parameters = emptyList(),
        expectedReturnType = null,
        kind = UastCallKind.METHOD_CALL,
        context = uCall.sourcePsi
      ) ?: return
      val parent = uCall.uastParent
      // we check for parent in case of `Thread.ofVirtual()` calls in Kotlin
      val replacementUastElement = parent as? UQualifiedReferenceExpression ?: uCall
      replacementUastElement.replace(newCall)
    }
  }
}

private const val THREAD_FQN = "java.lang.Thread"
private const val INTELLIJ_VIRTUAL_THREADS_FQN = "com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads"

private val HINTS: Array<Class<out UElement>> = arrayOf(UCallExpression::class.java)

