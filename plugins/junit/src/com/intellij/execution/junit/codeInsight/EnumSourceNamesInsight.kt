// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight

import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.annotation.JvmAnnotationClassValue
import com.intellij.lang.jvm.annotation.JvmAnnotationEnumFieldValue
import com.intellij.psi.*
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls

object EnumSourceNamesInsight {
  fun createEnumSourceNamesElementFilter(): ElementFilter = EnumSourceNamesElementFilter()

  fun createReferencesByElement(literal: PsiLiteral): Array<PsiReference> = arrayOf(EnumSourceReference(literal))

  /**
   * Check that @EnumSource#names contains values from selected Enum
   */
  fun checkEnumSourceNames(enumType: PsiType,
                           enumSource: PsiAnnotation,
                           problemsHolder: ProblemsHolder) {
    fun allEnumConstants(psiClass: PsiClass): Set<String> {
      return psiClass.fields.asSequence()
        .filterIsInstance(PsiEnumConstant::class.java)
        .map { it.name }
        .toSet()
    }

    val checkNamesForEnumConstant = isEnumSourceModeContainingIncludeOrExclude(enumSource)
    if (checkNamesForEnumConstant) {
      val allEnumConstants = allEnumConstants(PsiTypesUtil.getPsiClass(enumType) ?: return)
      processArrayInAnnotationParameter(enumSource.findAttributeValue("names")) { name ->
        if (name is PsiLiteralExpression) {
          if (!allEnumConstants.contains(name.value)) {
            problemsHolder.registerProblem(name, "Can't resolve enum constant reference.")
          }
        }
      }
    }
  }
}

private class EnumSourceNamesElementFilter : ElementFilter {
  override fun isAcceptable(element: Any, context: PsiElement?): Boolean {
    val literal = context as? PsiLiteral ?: return false;
    val enumSourceAnnotation = PsiTreeUtil.getParentOfType(literal, PsiAnnotation::class.java, false) ?: return false
    return isEnumSourceModeContainingIncludeOrExclude(enumSourceAnnotation)
  }

  override fun isClassAcceptable(hintClass: Class<*>): Boolean {
    return PsiLiteral::class.java.isAssignableFrom(hintClass)
  }

  @NonNls
  override fun toString(): String {
    return "junit5 EnumSource names"
  }
}

private class EnumSourceReference(element: PsiLiteral) : PsiReferenceBase<PsiLiteral>(element, false) {
  @Throws(IncorrectOperationException::class)
  override fun bindToElement(element: PsiElement): PsiElement {
    return if (element is PsiEnumConstant) {
      handleElementRename(element.name)
    }
    else {
      super.bindToElement(element)
    }
  }

  override fun resolve(): PsiElement? {
    val enumClass = findReferencedEnumClass(element) ?: return null
    val literalValue = value
    return enumClass.fields.asSequence()
      .filterIsInstance(PsiEnumConstant::class.java)
      .firstOrNull() { it.name == literalValue }
  }

  override fun getVariants(): Array<Any> {
    val enumClass = findReferencedEnumClass(element) ?: return emptyArray()
    return enumClass.fields.asSequence()
      .filterIsInstance(PsiEnumConstant::class.java)
      .map { createLookupBuilder(it) }
      .toList()
      .toTypedArray()
  }

  private fun findReferencedEnumClass(literal: PsiLiteral): JvmClass? {
    val annotationParameters = PsiTreeUtil.getParentOfType(literal, PsiAnnotationParameterList::class.java, false)
                               ?: return null
    val valuePair = annotationParameters.attributes.firstOrNull { it.name == PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME }
    return (valuePair?.attributeValue as? JvmAnnotationClassValue)?.clazz;
  }

  private fun createLookupBuilder(psiEnum: PsiEnumConstant): LookupElement =
    LookupElementBuilder.create(psiEnum).withAutoCompletionPolicy(AutoCompletionPolicy.SETTINGS_DEPENDENT)
}

private fun isEnumSourceModeContainingIncludeOrExclude(enumSourceAnnotation: PsiAnnotation): Boolean {
  /**
   * INCLUDE or EXCLUDE mode is for Enum constants, other modes are for regular expressions matching constant names
   */
  fun isSupportedMode(mode: String) = mode == "INCLUDE" || mode == "EXCLUDE"

  val annotationParameters = PsiTreeUtil.getChildOfType(enumSourceAnnotation, PsiAnnotationParameterList::class.java)
                             ?: return false
  val modePair = annotationParameters.attributes.firstOrNull { it != null && it.name == "mode" }
  if (modePair != null) {
    // 'mode' value is explicitly set, lets check if it is supported mode
    val fieldName = (modePair.attributeValue as? JvmAnnotationEnumFieldValue)?.fieldName ?: return false
    return isSupportedMode(fieldName)
  }
  else {
    // 'mode' value is NOT explicitly specified, check default value from annotation class definition
    val annotationClass = enumSourceAnnotation.nameReferenceElement?.resolve() as? PsiClass ?: return false
    val modeDefaultValue = findModeDefaultValue(annotationClass) ?: return false
    return isSupportedMode(modeDefaultValue)
  }
}

private fun findModeDefaultValue(annotationClass: PsiClass): String? {
  fun resolveModeValue(annotationMethod: PsiAnnotationMethod): String? {
    // junit5 5.0+ has default value for mode specified
    val enumConstant = (annotationMethod.defaultValue as? PsiReferenceExpression)
                         ?.resolve() as? PsiEnumConstant ?: return null
    return enumConstant.name
  }

  return annotationClass
    .methods.asSequence()
    .filterIsInstance(PsiAnnotationMethod::class.java)
    .filter { it.name == "mode" }
    .mapNotNull { resolveModeValue(it) }
    .firstOrNull()
}

private fun processArrayInAnnotationParameter(attributeValue: PsiAnnotationMemberValue?,
                                              checker: (value: PsiAnnotationMemberValue) -> Unit) {
  if (attributeValue is PsiLiteral || attributeValue is PsiClassObjectAccessExpression) {
    checker.invoke(attributeValue)
  }
  else if (attributeValue is PsiArrayInitializerMemberValue) {
    for (memberValue in attributeValue.initializers) {
      processArrayInAnnotationParameter(memberValue, checker)
    }
  }
}
