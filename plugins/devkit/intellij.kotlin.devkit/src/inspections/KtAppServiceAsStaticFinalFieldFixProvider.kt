// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.application.CachedSingletonsRegistry
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiEditorUtil
import org.jetbrains.idea.devkit.inspections.quickfix.AppServiceAsStaticFinalFieldFixProvider
import org.jetbrains.idea.devkit.inspections.quickfix.WrapInSupplierQuickFix
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.intentions.ConvertPropertyToFunctionIntention
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlinePropertyProcessor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.util.function.Supplier


private class KtAppServiceAsStaticFinalFieldFixProvider : AppServiceAsStaticFinalFieldFixProvider {

  override fun getFixes(psiElement: PsiElement): List<LocalQuickFix> {
    return if (psiElement is KtProperty) {
      listOf(KtWrapInSupplierQuickFix(psiElement), IntentionWrapper(ConvertPropertyToFunctionIntention()))
    } else emptyList()
  }

}


private class KtWrapInSupplierQuickFix(ktProperty: KtProperty) : WrapInSupplierQuickFix<KtProperty>(ktProperty) {

  override fun findElement(startElement: PsiElement): KtProperty {
    return startElement.parentOfType<KtProperty>(withSelf = true)!!
  }

  override fun createSupplierElement(project: Project, element: KtProperty): KtProperty {
    val supplierPropertyName = suggestSupplierPropertyName(element)

    val ktPropertyType = (element.resolveToDescriptorIfAny() as CallableDescriptor).returnType

    val supplierProperty = KtPsiFactory(project).createProperty(
      modifiers = element.modifierList?.text,
      name = supplierPropertyName,
      type = "${Supplier::class.java.canonicalName}<${ktPropertyType!!.fqName}>",
      isVar = false,
      initializer = "${CachedSingletonsRegistry::class.java.canonicalName}.lazy { ${element.initializer!!.text} }"
    )

    return (element.parent.addBefore(supplierProperty, element) as KtProperty).also {
      copyComments(element, it, element.initializer)
      ShortenReferences.DEFAULT.process(it)
    }
  }

  override fun changeElementInitializerToSupplierCall(project: Project, element: KtProperty, supplierElement: KtProperty) {
    val receiverName = when(val container = supplierElement.containingClassOrObject) {
      is KtClass -> container.name
      is KtObjectDeclaration -> if (container.isCompanion()) container.containingClassOrObject!!.name else container.name
      else -> null
    }
    val supplierGetCall = KtPsiFactory(project).createExpression(
      "${receiverName?.let { "$it." }.orEmpty()}${supplierElement.name}.get()"
    )
    element.initializer = supplierGetCall
  }

  private fun suggestSupplierPropertyName(property: KtProperty): String {
    return Fe10KotlinNameSuggester.suggestNameByName(
      defaultSupplierElementName(property),
      Fe10KotlinNewDeclarationNameValidator(
        property.containingClassOrObject ?: property.containingFile,
        null,
        KotlinNameSuggestionProvider.ValidatorTarget.PROPERTY
      ),
    )
  }

  /**
   * Runs the `Inline Property` refactoring as it's done in [org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlinePropertyHandler]
   * by running the [org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlinePropertyProcessor] directly to avoid showing the dialog.
   * All the necessary requirements (e.g. that the property has a body) are checked before.
   */
  override fun inlineElement(project: Project, element: KtProperty) {
    KotlinInlinePropertyProcessor(
      element,
      null,
      false,
      true,
      false,
      PsiEditorUtil.findEditor(element),
      null,
      project
    ).run()
  }
}
