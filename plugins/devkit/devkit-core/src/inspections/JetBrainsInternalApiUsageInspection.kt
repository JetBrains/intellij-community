// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.StaticAnalysisAnnotationManager
import com.intellij.codeInspection.AnnotatedContainingDeclaration
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.UnstableApiUsageInspectionBase
import com.intellij.codeInspection.UnstableApiUsageMessageProvider
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.idea.devkit.DevKitBundle

/**
 * Reports a usage of a JetBrains internal API in a plugin module.
 *
 * In such a module [JetBrainsInternalApiUsageFilter] hides the internal API branches of the standard `UnstableApiUsage`
 * inspection, so this inspection reports the internal API instead of them.
 *
 * The IntelliJ Platform project declares the internal API itself, so the inspection reports nothing there.
 */
internal class JetBrainsInternalApiUsageInspection : UnstableApiUsageInspectionBase() {

  @JvmField
  var myIgnoreApiDeclaredInThisProject: Boolean = true

  override val ignoreApiDeclaredInThisProject: Boolean
    get() = myIgnoreApiDeclaredInThisProject

  override fun getOptionsPane(): OptPane {
    return pane(
      checkbox("myIgnoreApiDeclaredInThisProject",
               DevKitBundle.message("devkit.internal.api.usage.ignore.declared.inside.this.project"))
    )
  }

  override val annotationsToCheck: List<String>
    get() = StaticAnalysisAnnotationManager.getInstance().knownInternalApiAnnotations.asList()

  override fun getMessageProvider(annotationFqn: String): UnstableApiUsageMessageProvider = JetBrainsInternalApiMessageProvider

  override fun isUsageIgnored(usage: PsiElement, annotationFqn: String): Boolean = !isInPluginModule(usage)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (IntelliJProjectUtil.isIntelliJPlatformProject(holder.project)) return PsiElementVisitor.EMPTY_VISITOR

    return super.buildVisitor(holder, isOnTheFly)
  }
}

private object JetBrainsInternalApiMessageProvider : UnstableApiUsageMessageProvider {

  override val problemHighlightType: ProblemHighlightType
    get() = ProblemHighlightType.GENERIC_ERROR

  override fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        DevKitBundle.message(
          "inspections.jetbrains.internal.api.usage.api.is.marked.internal.itself",
          targetName,
          presentableAnnotationName,
          warning
        )
      }
      else {
        DevKitBundle.message(
          "inspections.jetbrains.internal.api.usage.api.is.declared.in.internal.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          presentableAnnotationName,
          warning
        )
      }
    }

  override fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String =
    with(annotatedContainingDeclaration) {
      if (isOwnAnnotation) {
        DevKitBundle.message(
          "inspections.jetbrains.internal.api.usage.overridden.method.is.marked.internal.itself",
          targetName,
          presentableAnnotationName,
          warning
        )
      }
      else {
        DevKitBundle.message(
          "inspections.jetbrains.internal.api.usage.overridden.method.is.declared.in.internal.api",
          targetName,
          containingDeclarationType,
          containingDeclarationName,
          presentableAnnotationName,
          warning
        )
      }
    }

  override fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration,
  ): String = DevKitBundle.message(
    "inspections.jetbrains.internal.api.usage.internal.type.is.used.in.signature.of.referenced.api",
    DeprecationInspection.getPresentableName(referencedApi),
    annotatedTypeUsedInSignature.targetType,
    annotatedTypeUsedInSignature.targetName,
    annotatedTypeUsedInSignature.presentableAnnotationName,
    warning
  )

  private val warning: String
    get() = DevKitBundle.message("inspections.jetbrains.internal.api.usage.warning")
}
