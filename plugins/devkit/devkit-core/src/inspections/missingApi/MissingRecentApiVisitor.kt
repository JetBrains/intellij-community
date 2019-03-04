// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.ExternalAnnotationsManager
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.apiUsage.ApiUsageVisitorBase
import com.intellij.openapi.util.BuildNumber
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.getUastParentOfType

/**
 * PSI visitor containing implementation of [MissingRecentApiInspection],
 * which reports usages of APIs that are not available in old IDE builds matching the
 * "since" constraint of the plugin.
 */
class MissingRecentApiVisitor(
  private val holder: ProblemsHolder,
  private val highlightType: ProblemHighlightType,
  private val targetedSinceUntilRanges: List<SinceUntilRange>
) : ApiUsageVisitorBase() {

  companion object {
    val AVAILABLE_SINCE_ANNOTATION: String = ApiStatus.AvailableSince::class.qualifiedName!!
  }

  override fun shouldProcessReferences(element: PsiElement) = !element.isInsideImportStatement()

  private fun PsiElement.isInsideImportStatement() = getUastParentOfType<UImportStatement>() != null

  override fun processReference(reference: PsiReference) {
    if (reference is ResolvingHint && !(reference as ResolvingHint).canResolveTo(PsiModifierListOwner::class.java)) {
      return
    }
    val resolved = reference.resolve()
    if (resolved != null) {
      val elementToHighlight = getElementToHighlight(reference)
      checkMissingApi(resolved, elementToHighlight)
    }
  }

  private fun getElementToHighlight(reference: PsiReference): PsiElement {
    if (reference is PsiJavaCodeReferenceElement) {
      val referenceNameElement = reference.referenceNameElement
      if (referenceNameElement != null) {
        return referenceNameElement
      }
    }
    return reference.element
  }

  override fun processConstructorInvocation(instantiatedClass: PsiJavaCodeReferenceElement, constructor: PsiMethod) {
    checkMissingApi(constructor, instantiatedClass)
  }

  override fun processDefaultConstructorInvocation(instantiatedClass: PsiJavaCodeReferenceElement) {
    val createdClass = instantiatedClass.resolve() as? PsiClass ?: return
    checkClassDefaultConstructorApi(createdClass, instantiatedClass)
  }

  private fun checkClassDefaultConstructorApi(psiClass: PsiClass, elementToHighlight: PsiElement) {
    val availableSince = findEmptyConstructorAnnotations(psiClass)?.getAvailableSinceBuildNumber() ?: return
    val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
    if (brokenRanges.isNotEmpty()) {
      registerDefaultConstructorProblem(psiClass, elementToHighlight, availableSince, brokenRanges)
    }
  }

  override fun processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassDeclaration(
    subclass: PsiClass,
    superClass: PsiClass
  ) {
    val availableSince = findEmptyConstructorAnnotations(superClass)?.getAvailableSinceBuildNumber() ?: return
    val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
    if (brokenRanges.isNotEmpty()) {
      val asAnonymous = subclass as? PsiAnonymousClass
      if (asAnonymous != null) {
        val argumentList = asAnonymous.argumentList
        if (argumentList != null && !argumentList.isEmpty) return
      }
      val elementToHighlight = asAnonymous?.baseClassReference ?: subclass.nameIdentifier
      if (elementToHighlight != null) {
        registerDefaultConstructorProblem(superClass, elementToHighlight, availableSince, brokenRanges)
      }
    }
  }

  override fun processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassConstructor(
    superClass: PsiClass,
    subclassConstructor: PsiMethod
  ) {
    val nameIdentifier = subclassConstructor.nameIdentifier ?: return
    checkClassDefaultConstructorApi(superClass, nameIdentifier)
  }

  override fun processMethodOverriding(method: PsiMethod, overriddenMethod: PsiMethod) {
    val availableSince = overriddenMethod.getApiSinceBuildNumber() ?: return
    val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
    if (brokenRanges.isNotEmpty()) {
      val aClass = overriddenMethod.containingClass ?: return
      val nameIdentifier = method.nameIdentifier ?: return
      val description = DevKitBundle.message(
        "inspections.api.overrides.method.available.only.since",
        aClass.getPresentableName(),
        availableSince.asString(),
        brokenRanges.joinToString { it.asString() }
      )
      holder.registerProblem(nameIdentifier, description, highlightType)
    }
  }

  private fun registerDefaultConstructorProblem(
    constructorOwner: PsiClass,
    elementToHighlight: PsiElement,
    apiSinceBuildNumber: BuildNumber,
    brokenRanges: List<SinceUntilRange>
  ) {
    val description = DevKitBundle.message(
      "inspections.api.constructor.only.since",
      constructorOwner.qualifiedName,
      apiSinceBuildNumber.asString(),
      brokenRanges.joinToString { it.asString() }
    )
    holder.registerProblem(elementToHighlight, description, highlightType)
  }

  private fun findEmptyConstructorAnnotations(psiClass: PsiClass): List<PsiAnnotation>? {
    val constructors = psiClass.constructors
    if (constructors.isEmpty()) {
      /*
       Default constructor of a class, which is not present in source code,
       can be externally annotated (IDEA-200832).
      */
      return ExternalAnnotationsManager.getInstance(psiClass.project)
        .findDefaultConstructorExternalAnnotations(psiClass, AVAILABLE_SINCE_ANNOTATION)
    } else {
      val emptyConstructor = constructors.find { it.parameterList.isEmpty }
      if (emptyConstructor != null) {
        return AnnotationUtil.findAllAnnotations(emptyConstructor, listOf(AVAILABLE_SINCE_ANNOTATION), false)
      }
      return null
    }
  }

  /**
   * Checks if the API element [refElement] is annotated
   * with [org.jetbrains.annotations.ApiStatus.AvailableSince].
   * If so, it checks plugin's [since, until] compatibility range
   * and registers a problem if the API was first introduced later
   * than plugin's `since` build.
   */
  private fun checkMissingApi(refElement: PsiElement, elementToHighlight: PsiElement) {
    if (refElement !is PsiModifierListOwner) return

    val availableSince = refElement.getApiSinceBuildNumber() ?: return
    val brokenRanges = targetedSinceUntilRanges.filter { it.someBuildsAreNotCovered(availableSince) }
    if (brokenRanges.isNotEmpty()) {
      val description = DevKitBundle.message(
        "inspections.api.available.only.since",
        refElement.getPresentableName(),
        availableSince.asString(),
        brokenRanges.joinToString { it.asString() }
      )
      holder.registerProblem(elementToHighlight, description, highlightType)
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
    } else HighlightMessageUtil.getSymbolName(this)
  }

}