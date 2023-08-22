// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.CachedSingletonsRegistry
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.idea.devkit.inspections.AppServiceAsStaticFinalFieldOrPropertyVisitorProvider
import org.jetbrains.idea.devkit.inspections.getLevelType
import org.jetbrains.idea.devkit.inspections.quickfix.WrapInSupplierQuickFix
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSamConstructorSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.intentions.ConvertPropertyToFunctionIntention
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlinePropertyProcessor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.uast.UClass
import org.jetbrains.uast.toUElementOfType
import java.util.function.Supplier


private class KtAppServiceAsStaticFinalFieldOrPropertyVisitorProvider : AppServiceAsStaticFinalFieldOrPropertyVisitorProvider {

  override fun getVisitor(holder: ProblemsHolder): PsiElementVisitor {
    return object : KtVisitorVoid() {
      override fun visitProperty(property: KtProperty) {
        if (property.isVar || !property.isStatic()) return

        @OptIn(KtAllowAnalysisOnEdt::class)
        val typeClassElement = allowAnalysisOnEdt {
          analyze(property) {
            // return if it's an explicit constructor call
            val resolveSymbol = property.initializer?.resolveCall()?.singleFunctionCallOrNull()?.symbol
            val isConstructorCall = resolveSymbol is KtSamConstructorSymbol || resolveSymbol is KtConstructorSymbol
            if (isConstructorCall) return

            // can be KtClass or PsiClass
            property.getReturnKtType().withNullability(KtTypeNullability.UNKNOWN).expandedClassSymbol?.psi ?: return
          }
        }

        val serviceLevel = getLevelType(holder.project, typeClassElement.toUElementOfType<UClass>()!!)
        if (serviceLevel == null || !serviceLevel.isApp()) return

        val anchor = property.nameIdentifier ?: property

        // a property is used by service implementation to retrieve a service instance
        // if it's inside a companion object and returns and instance of containing class
        val isInstance = property.parentOfType<KtObjectDeclaration>()?.isCompanion() == true &&
                         property.containingClass() == typeClassElement

        if (isInstance) {
          holder.registerProblem(
            anchor,
            DevKitKotlinBundle.message("inspections.an.explicit.method.should.be.used.to.retrieve.an.application.service.message"),
            ProblemHighlightType.WARNING,
            IntentionWrapper(ConvertPropertyToFunctionIntention()),
            KtWrapInSupplierQuickFix(property),
          )
          return
        }

        if (property.hasBackingField()) {
          holder.registerProblem(
            anchor,
            DevKitKotlinBundle.message("inspections.application.service.as.static.immutable.property.with.backing.field.message"),
            ProblemHighlightType.WARNING,
            IntentionWrapper(ConvertPropertyToFunctionIntention()),
            KtWrapInSupplierQuickFix(property),
          )
        }
      }
    }
  }

  /**
   * Top-level or being a member of any object.
   */
  private fun KtProperty.isStatic(): Boolean {
    return isTopLevel || isMember && containingClassOrObject is KtObjectDeclaration
  }


  @OptIn(KtAllowAnalysisOnEdt::class)
  private fun KtProperty.hasBackingField(): Boolean {
    allowAnalysisOnEdt {
      val property = this

      analyze(property) {
        val propertySymbol = property.getVariableSymbol() as? KtPropertySymbol ?: return false
        return propertySymbol.hasBackingField
      }
    }
  }
}


private class KtWrapInSupplierQuickFix(ktProperty: KtProperty) : WrapInSupplierQuickFix<KtProperty>(ktProperty) {

  override fun findElement(startElement: PsiElement): KtProperty {
    return startElement.parentOfType<KtProperty>(withSelf = true)!!
  }

  override fun createSupplierElement(project: Project, element: KtProperty): KtProperty {
    val supplierPropertyName = suggestSupplierPropertyName(element)

    // can be called both from EDT and from the preview
    @OptIn(KtAllowAnalysisOnEdt::class)
    val ktPropertyType = allowAnalysisOnEdt {
      @OptIn(KtAllowAnalysisFromWriteAction::class)
      allowAnalysisFromWriteAction {
        analyze(element) {
          val returnType = element.getReturnKtType().lowerBoundIfFlexible()
          (returnType as? KtNonErrorClassType)?.classId
        }
      }
    }

    val supplierProperty = KtPsiFactory(project).createProperty(
      modifiers = element.modifierList?.text,
      name = supplierPropertyName,
      type = "${Supplier::class.java.canonicalName}<${ktPropertyType!!.asSingleFqName()}>",
      isVar = false,
      initializer = "${CachedSingletonsRegistry::class.java.canonicalName}.lazy { ${element.initializer!!.text} }"
    )

    return (element.parent.addBefore(supplierProperty, element) as KtProperty).also {
      copyComments(element, it, element.initializer)
      ShortenReferencesFacility.getInstance().shorten(it)
    }
  }

  override fun changeElementInitializerToSupplierCall(project: Project, element: KtProperty, supplierElement: KtProperty) {
    val receiverName = when (val container = supplierElement.containingClassOrObject) {
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
    return KotlinNameSuggester.suggestNameByName(
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
      declaration = element,
      reference = null,
      inlineThisOnly = false,
      deleteAfter = true,
      isWhenSubjectVariable = false,
      editor = PsiEditorUtil.findEditor(element),
      statementToDelete = null,
      project = project,
    ).run()
  }
}
