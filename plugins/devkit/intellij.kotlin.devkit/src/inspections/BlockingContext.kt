// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.REPLACE_WITH_ANNOTATION
import org.jetbrains.idea.devkit.util.REQUIRES_BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.util.isImported
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal val RequiresBlockingContextAnnotation: FqName = FqName(REQUIRES_BLOCKING_CONTEXT_ANNOTATION)
internal val RequiresBlockingContextAnnotationId: ClassId = ClassId.topLevel(RequiresBlockingContextAnnotation)

internal val ReplaceWithAnnotation: FqName = FqName(REPLACE_WITH_ANNOTATION)
internal val ReplaceWithAnnotationId: ClassId = ClassId.topLevel(ReplaceWithAnnotation)

internal abstract class BlockingContextFunctionBodyVisitor : KtTreeVisitorVoid() {
  override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression): Unit = Unit

  override fun visitDeclaration(dcl: KtDeclaration) {
    if (dcl is KtVariableDeclaration) {
      dcl.initializer?.accept(this)
    }
  }

  protected fun checkInlineLambdaArguments(call: KaFunctionCall<*>) {
    for ((psi, descriptor) in call.argumentMapping) {
      if (
        descriptor.returnType is KaFunctionType &&
        !descriptor.symbol.isCrossinline &&
        !descriptor.symbol.isNoinline &&
        psi is KtLambdaExpression
      ) {
        psi.bodyExpression?.accept(this)
      }
    }
  }
}

internal fun extractElementToHighlight(expression: KtCallExpression): KtElement = expression.getCallNameExpression() ?: expression

internal class ReplaceWithSuspendAlternativeQuickFix(
  element: PsiElement,
  private val expression: String,
  private val imports: List<String>,
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
  override fun getFamilyName(): String = DevKitKotlinBundle.message(
    "inspections.forbidden.method.in.suspend.context.replace.with.suspend.alternative.fix.text")

  override fun getText(): String = familyName

  override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean =
    startElement.getParentOfType<KtCallExpression>(false) != null

  override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
    val callExpression = startElement.getParentOfType<KtCallExpression>(false) ?: return
    val factory = KtPsiFactory(project)
    val newExpression = factory.createExpression(expression)
    val qualifiedExpression = callExpression.getQualifiedExpressionForSelector()
    val expressionToReplace = qualifiedExpression ?: callExpression

    val ktFile = callExpression.containingKtFile
    for (import in imports) {
      val fqName = FqName(import)
      if (!isImported(fqName, ktFile)) {
        ktFile.addImport(fqName)
      }
    }

    val resultExpression = expressionToReplace.replace(newExpression)
    ShortenReferencesFacility.getInstance().shorten(resultExpression as KtElement)
  }

  private fun isImported(name: FqName, file: KtFile): Boolean {
    if (name.parent() == file.packageFqName) return true
    return file.importDirectives.mapNotNull { it.importPath }.any { name.isImported(it) }
  }
}