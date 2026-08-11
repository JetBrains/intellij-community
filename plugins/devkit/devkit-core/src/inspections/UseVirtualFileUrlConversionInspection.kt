// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.resolveToUElementOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal class UseVirtualFileToVirtualFileUrlConversionInspection : VirtualFileUrlConversionInspection(
  targetClassName = VIRTUAL_FILE_URL_MANAGER_FQN,
  targetMethodName = "getOrCreateFromUrl",
  urlSourceClassName = VIRTUAL_FILE_FQN,
  messageKey = "inspection.use.virtual.file.to.virtual.file.url.conversion.message",
  javaMessageKey = "inspection.use.virtual.file.to.virtual.file.url.conversion.java.message",
)

internal class UseVirtualFileUrlToVirtualFileConversionInspection : VirtualFileUrlConversionInspection(
  targetClassName = VIRTUAL_FILE_MANAGER_FQN,
  targetMethodName = "findFileByUrl",
  urlSourceClassName = VIRTUAL_FILE_URL_FQN,
  messageKey = "inspection.use.virtual.file.url.to.virtual.file.conversion.message",
)

internal abstract class VirtualFileUrlConversionInspection(
  private val targetClassName: String,
  private val targetMethodName: String,
  private val urlSourceClassName: String,
  private val messageKey: String,
  private val javaMessageKey: String? = null,
) : DevKitUastInspectionBase() {

  override fun isAllowed(holder: ProblemsHolder): Boolean {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, targetClassName) &&
           DevKitInspectionUtil.isClassAvailable(holder, urlSourceClassName)
  }

  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(holder.file.language, object : AbstractUastNonRecursiveVisitor() {
      override fun visitCallExpression(node: UCallExpression): Boolean {
        if (!node.isTargetCall(targetClassName, targetMethodName)) return super.visitCallExpression(node)
        val argument = node.valueArguments.singleOrNull() ?: return super.visitCallExpression(node)
        if (findUrlSourceReceiver(argument, urlSourceClassName, HashSet()) == null) return super.visitCallExpression(node)

        val element = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return super.visitCallExpression(node)
        val problemMessageKey = javaMessageKey?.takeIf { holder.file.language.`is`(JavaLanguage.INSTANCE) } ?: messageKey
        holder.registerProblem(element, DevKitBundle.message(problemMessageKey))
        return super.visitCallExpression(node)
      }
    }, HINTS)
  }
}

private fun UCallExpression.isTargetCall(className: String, methodName: String): Boolean {
  if (this.methodName != methodName) return false
  val method = resolve() ?: return false
  if (method.parameterList.parametersCount != 1 ||
      method.parameterList.parameters.single().type.canonicalText != CommonClassNames.JAVA_LANG_STRING) {
    return false
  }
  val containingClass = method.containingClass ?: return false
  return containingClass.qualifiedName == className || InheritanceUtil.isInheritor(containingClass, className)
}

private fun findUrlSourceReceiver(
  expression: UExpression,
  expectedReceiverClassName: String,
  visitedVariables: MutableSet<PsiElement>,
): UExpression? {
  val unwrapped = expression.skipParenthesizedExprDown()
  val directReceiver = unwrapped.findUrlGetterReceiver()
  if (directReceiver?.hasType(expectedReceiverClassName) == true) return directReceiver

  val reference = unwrapped as? USimpleNameReferenceExpression ?: return null
  val variable = reference.resolveToUElementOfType<ULocalVariable>() ?: return null
  if (!variable.isImmutable()) return null
  val sourcePsi = variable.sourcePsi ?: return null
  if (!visitedVariables.add(sourcePsi)) return null
  val initializer = variable.uastInitializer ?: return null
  return findUrlSourceReceiver(initializer, expectedReceiverClassName, visitedVariables)
}

private fun ULocalVariable.isImmutable(): Boolean {
  if (isFinal) return true
  val psi = javaPsi ?: return false
  // Kotlin UAST light local variables don't expose the distinction between `val` and `var` through the `final` modifier.
  return psi.language.id == KOTLIN_LANGUAGE_ID && psi.text.trimStart().startsWith("val ")
}

private fun UExpression.findUrlGetterReceiver(): UExpression? {
  if (this is UQualifiedReferenceExpression && (resolvedName == "url" || selector.resolvesToUrlGetter())) return receiver
  if (this is UCallExpression && resolvesToUrlGetter()) return receiver
  return null
}

private fun UElement.resolvesToUrlGetter(): Boolean {
  val method = (this as? UResolvable)?.resolve() as? PsiMethod ?: return false
  return method.name == "getUrl" && method.parameterList.isEmpty
}

private fun UExpression.hasType(className: String): Boolean {
  val type = getExpressionType() ?: return false
  return PsiTypesUtil.classNameEquals(type, className) || InheritanceUtil.isInheritor(type, className)
}

private const val VIRTUAL_FILE_FQN = "com.intellij.openapi.vfs.VirtualFile"
private const val VIRTUAL_FILE_MANAGER_FQN = "com.intellij.openapi.vfs.VirtualFileManager"
private const val VIRTUAL_FILE_URL_FQN = "com.intellij.platform.workspace.storage.url.VirtualFileUrl"
private const val VIRTUAL_FILE_URL_MANAGER_FQN = "com.intellij.platform.workspace.storage.url.VirtualFileUrlManager"
private const val KOTLIN_LANGUAGE_ID = "kotlin"

private val HINTS: Array<Class<out UElement>> = arrayOf(UCallExpression::class.java)
