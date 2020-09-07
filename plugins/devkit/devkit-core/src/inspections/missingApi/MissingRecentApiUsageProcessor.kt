// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.util.BuildNumber
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.*

/**
 * PSI visitor containing implementation of [MissingRecentApiInspection],
 * which reports usages of APIs that are not available in old IDE builds matching the
 * "since" constraint of the plugin.
 */
class MissingRecentApiUsageProcessor(
  private val holder: ProblemsHolder,
  private val highlightType: ProblemHighlightType,
  private val targetedSinceUntilRanges: List<SinceUntilRange>
) : ApiUsageProcessor {

  companion object {
    val AVAILABLE_SINCE_ANNOTATION: String = ApiStatus.AvailableSince::class.java.canonicalName
  }

  override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
    val elementToHighlight = sourceNode.sourcePsi ?: return
    checkApiIsRecent(target, elementToHighlight)
  }

  override fun processConstructorInvocation(
    sourceNode: UElement,
    instantiatedClass: PsiClass,
    constructor: PsiMethod?,
    subclassDeclaration: UClass?
  ) {
    val elementToHighlight = sourceNode.sourcePsi ?: return
    if (constructor != null) {
      checkApiIsRecent(constructor, elementToHighlight)
    }
    else {
      val availableSince = findEmptyConstructorAnnotations(instantiatedClass)?.getAvailableSinceBuildNumber() ?: return
      val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
      if (brokenRanges.isNotEmpty()) {
        registerDefaultConstructorProblem(instantiatedClass, elementToHighlight, availableSince, brokenRanges)
      }
    }
  }

  override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
    val availableSince = overriddenMethod.getApiSinceBuildNumber() ?: return
    val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
    if (brokenRanges.isNotEmpty()) {
      val aClass = overriddenMethod.containingClass ?: return
      val methodNameElement = method.uastAnchor.sourcePsiElement ?: return
      val description = DevKitBundle.message(
        "inspections.missing.recent.api.overriding",
        aClass.getPresentableName(),
        availableSince.asString(),
        brokenRanges.joinToString { it.asString() }
      )
      holder.registerProblem(methodNameElement, description, highlightType)
    }
  }

  private fun registerDefaultConstructorProblem(
    constructorOwner: PsiClass,
    elementToHighlight: PsiElement,
    apiSinceBuildNumber: BuildNumber,
    brokenRanges: List<SinceUntilRange>
  ) {
    val description = DevKitBundle.message(
      "inspections.missing.recent.api.default.constructor.message",
      constructorOwner.qualifiedName,
      apiSinceBuildNumber.asString(),
      brokenRanges.joinToString { it.asString() }
    )
    holder.registerProblem(elementToHighlight, description, highlightType)
  }

  private fun findEmptyConstructorAnnotations(psiClass: PsiClass): List<PsiAnnotation>? {
    // Default constructor of a class, which is not represented in PSI, can be externally annotated (IDEA-200832).
    return ExternalAnnotationsManager.getInstance(psiClass.project)
      .findDefaultConstructorExternalAnnotations(psiClass, AVAILABLE_SINCE_ANNOTATION)
  }

  private fun checkApiIsRecent(modifierListOwner: PsiModifierListOwner, elementToHighlight: PsiElement) {
    val availableSince = modifierListOwner.getApiSinceBuildNumber() ?: return
    val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
    if (brokenRanges.isNotEmpty()) {
      val description = buildDescription(modifierListOwner, availableSince, brokenRanges)
      holder.registerProblem(elementToHighlight, description, highlightType)
    }
  }

  @InspectionMessage
  private fun buildDescription(
    apiElement: PsiModifierListOwner,
    availableSince: BuildNumber,
    brokenRanges: List<SinceUntilRange>
  ): String {
    val presentableName = apiElement.getPresentableName()
    val sinceString = availableSince.asString()
    val rangesString = brokenRanges.joinToString { it.asString() }
    return when (apiElement) {
      is PsiClass -> DevKitBundle.message("inspections.missing.recent.api.class.message", presentableName, sinceString, rangesString)
      is PsiMethod -> DevKitBundle.message("inspections.missing.recent.api.method.message", presentableName, sinceString, rangesString)
      is PsiField -> DevKitBundle.message("inspections.missing.recent.api.field.message", presentableName, sinceString, rangesString)
      else -> DevKitBundle.message("inspections.missing.recent.api.common.message", presentableName, sinceString, rangesString)
    }
  }

  /**
   * Returns the first build number when `this` API element was added.
   */
  private fun PsiModifierListOwner.getApiSinceBuildNumber(): BuildNumber? {
    val externalAnnotations = AnnotationUtil.findAllAnnotations(this, listOf(AVAILABLE_SINCE_ANNOTATION), false)
    if (externalAnnotations.isEmpty()) return null
    return externalAnnotations.getAvailableSinceBuildNumber()
  }

  private fun List<PsiAnnotation>.getAvailableSinceBuildNumber(): BuildNumber? =
    asSequence()
      .mapNotNull { annotation ->
        AnnotationUtil.getDeclaredStringAttributeValue(annotation, "value")?.let {
          BuildNumber.fromStringOrNull(it)
        }
      }
      .min()

  private fun SinceUntilRange.someBuildsAreNotCovered(apiSinceBuildNumber: BuildNumber) =
    sinceBuild == null || sinceBuild < apiSinceBuildNumber

  private fun PsiElement.getPresentableName(): String? {
    //Annotation attribute methods don't have parameters.
    return if (this is PsiMethod && PsiUtil.isAnnotationMethod(this)) {
      name
    }
    else {
      HighlightMessageUtil.getSymbolName(this)
    }
  }

}