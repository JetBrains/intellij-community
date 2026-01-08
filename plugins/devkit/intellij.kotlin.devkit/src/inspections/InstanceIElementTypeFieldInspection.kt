// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiField
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class InstanceIElementTypeFieldInspection : DevKitUastInspectionBase() {

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitField(node: UField): Boolean {
          checkField(node, holder)
          return true
        }
      },
      arrayOf(UField::class.java)
    )
  }

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return DevKitInspectionUtil.isClassAvailable(holder, "com.intellij.psi.tree.IElementType")
  }

  private fun checkField(field: UField, holder: ProblemsHolder) {
    // 1. Must be instance field (not static)
    if (field.isStatic) return

    // 2. Skip enum fields - enum constants are singletons
    val containingClass = field.getContainingUClass() ?: return
    if (containingClass.isEnum) {
      return  // Enum fields are singletons, safe to use
    }

    // 3. Skip Kotlin companion object and object declarations
    // Companion objects and object declarations appear as static fields in UAST,
    // so we already filtered them out above
    if (containingClass.javaPsi.name == "Companion") return

    // 4. Must have constructor call initializer
    val initializer = field.uastInitializer ?: return

    // Handle both direct constructor calls and qualified constructor calls (e.g., com.foo.Bar(...))
    val callExpression: UCallExpression = when {
      initializer is UCallExpression && initializer.hasKind(UastCallKind.CONSTRUCTOR_CALL) -> initializer
      // For qualified names like com.foo.Bar(...), the call might be wrapped in a qualified expression
      initializer is UQualifiedReferenceExpression -> {
        (initializer.selector as? UCallExpression)?.takeIf { it.hasKind(UastCallKind.CONSTRUCTOR_CALL) }
      }
      else -> null
    } ?: return

    // 5. Check if constructed type is IElementType or subtype
    val constructedType = callExpression.returnType ?: return
    val constructedClass = PsiTypesUtil.getPsiClass(constructedType) ?: return
    if (!InheritanceUtil.isInheritor(constructedClass, "com.intellij.psi.tree.IElementType")) return

    // 6. Register problem with quick fix
    val sourcePsi = field.sourcePsi ?: return
    val message = DevKitBundle.message("inspection.instance.ielement.type.field.message")

    when (sourcePsi) {
      is PsiField -> {
        holder.registerUProblem(field, message, MakeStaticFinalJavaQuickFix())
      }
      is KtProperty -> {
        // move to companion is already provided as a separate intention, so don't need to bother
        holder.registerUProblem(field, message)
      }
    }
  }

  private class MakeStaticFinalJavaQuickFix : ModCommandQuickFix() {
    override fun getFamilyName(): String = DevKitBundle.message("inspection.instance.ielement.type.field.fix.java")
    override fun getName(): @IntentionName String = DevKitBundle.message("inspection.instance.ielement.type.field.fix.java")

    override fun perform(
      project: Project,
      descriptor: ProblemDescriptor,
    ): ModCommand {
      val identifier = descriptor.psiElement as? PsiIdentifier ?: return ModCommand.nop()
      val field = (identifier.parent as? PsiField)?.takeIf { it.nameIdentifier === identifier } ?: return ModCommand.nop()
      val modifierList = field.modifierList ?: return ModCommand.nop()

      return ModCommand.psiUpdate(modifierList) {
        it.setModifierProperty(PsiModifier.STATIC, true)
        it.setModifierProperty(PsiModifier.FINAL, true)
      }
    }
  }
}
