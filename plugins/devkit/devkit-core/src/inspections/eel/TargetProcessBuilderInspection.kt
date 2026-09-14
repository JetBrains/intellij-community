// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class TargetProcessBuilderInspection : DevKitUastInspectionBase() {
  override fun buildInternalVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return createFilteredUastVisitor(
      holder.file.language,
      object : AbstractUastNonRecursiveVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (node.kind != UastCallKind.CONSTRUCTOR_CALL ||
              node.classReference?.sourcePsi?.text?.substringAfterLast('.') != PROCESS_BUILDER_NAME ||
              node.resolve()?.containingClass?.qualifiedName != PROCESS_BUILDER_CLASS) {
            return true
          }

          reportTargetArguments(node, holder)
          return true
        }
      },
      arrayOf(UCallExpression::class.java),
    ) { element -> PROCESS_BUILDER_NAME in element.text }
  }

  private fun reportTargetArguments(node: UCallExpression, holder: ProblemsHolder) {
    val classifications = node.valueArguments.map(EelEnvironmentClassifier::classify)
    val hasHostArgument = EelEnvironment.HOST in classifications
    classifications.forEachIndexed { index, classification ->
      if (classification == EelEnvironment.TARGET || classification == EelEnvironment.MIXED) {
        if (hasLocalDescriptorFailFastGuard(node, node.valueArguments[index])) return@forEachIndexed

        val argument = node.valueArguments[index].sourcePsi ?: return@forEachIndexed
        val message = if (hasHostArgument || classification == EelEnvironment.MIXED) {
          DevKitBundle.message("inspection.target.process.builder.mixed.argument")
        }
        else {
          DevKitBundle.message("inspection.target.process.builder.target.argument")
        }
        holder.registerProblem(argument, message)
      }
    }
  }

  private fun hasLocalDescriptorFailFastGuard(call: UCallExpression, argument: UExpression): Boolean {
    val targetName = argument.sourcePsi?.text?.substringBefore('.')?.takeIf { it.all(Char::isJavaIdentifierPart) } ?: return false
    val callOffset = call.sourcePsi?.textRange?.startOffset ?: return false
    val block = generateSequence(call.uastParent) { it.uastParent }
      .filterIsInstance<UBlockExpression>()
      .firstOrNull() ?: return false
    val blockPsi = block.sourcePsi ?: return false
    val prefixLength = callOffset - blockPsi.textRange.startOffset
    if (prefixLength !in 0..blockPsi.textLength) return false

    val localDescriptor = "(?:com\\.intellij\\.platform\\.eel\\.provider\\.)?LocalEelDescriptor"
    val targetDescriptor = "${Regex.escape(targetName)}\\.descriptor"
    val failFast = "\\s*\\)\\s*\\{?\\s*throw\\b"
    val guard = Regex("if\\s*\\(\\s*(?:$targetDescriptor\\s*!=\\s*$localDescriptor|$localDescriptor\\s*!=\\s*$targetDescriptor)$failFast")
    return guard.containsMatchIn(blockPsi.text.take(prefixLength))
  }

  private companion object {
    const val PROCESS_BUILDER_NAME = "ProcessBuilder"
    const val PROCESS_BUILDER_CLASS = "java.lang.ProcessBuilder"
  }
}
