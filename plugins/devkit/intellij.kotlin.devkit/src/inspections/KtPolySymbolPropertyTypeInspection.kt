// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolProperty
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.types.Variance

internal class KtPolySymbolPropertyTypeInspection : LocalInspectionTool() {

  @OptIn(KaExperimentalApi::class)
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : KtVisitorVoid() {
      override fun visitProperty(property: KtProperty) {
        checkPropertyOrField(property, property.nameIdentifier)
      }

      override fun visitParameter(parameter: KtParameter) {
        // Check if this is a primary constructor property
        if (parameter.hasValOrVar()) {
          checkPropertyOrField(parameter, parameter.nameIdentifier)
        }
      }

      private fun checkPropertyOrField(
        declaration: KtCallableDeclaration,
        nameIdentifier: PsiElement?,
      ) {
        if (nameIdentifier == null) return

        // Find @PolySymbol.Property annotation
        val propertyAnnotation = declaration.annotationEntries.find { entry ->
          analyze(entry) {
            val fqName = entry.typeReference?.type?.expandedSymbol?.classId?.asSingleFqName()
            fqName?.asString() == "${PolySymbol::class.qualifiedName}.Property"
          }
        } ?: return

        // Get the property class from the annotation
        val propertyClassLiteral = propertyAnnotation.valueArguments.firstOrNull()
                                     ?.getArgumentExpression() as? KtClassLiteralExpression
                                   ?: return

        // Analyze types
        analyze(declaration) {
          val declaredType = declaration.returnType.withNullability(false)
          val expectedPropertyType =
            getPolySymbolPropertyType(propertyClassLiteral)
              ?.withNullability(false)
            ?: return@analyze

          // Check if the declared type is assignable to the expected property type
          if (!declaredType.isSubtypeOf(expectedPropertyType)) {
            holder.registerProblem(
              nameIdentifier,
              DevKitKotlinBundle.message(
                "inspection.polySymbol.property.type.mismatch",
                declaredType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT),
                expectedPropertyType.render(
                  KaTypeRendererForSource.WITH_SHORT_NAMES,
                  position = Variance.INVARIANT
                )
              ),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
          }
        }
      }

      private fun getPolySymbolPropertyType(classLiteral: KtClassLiteralExpression): KaType? {
        return analyze(classLiteral) {
          // Get the KClass<PropertyClass> type
          val receiverType = classLiteral.receiverType ?: return@analyze null

          // The property class should extend PolySymbolProperty<T>
          // We need to extract the type parameter T
          val propertyClass = receiverType.expandedSymbol ?: return@analyze null

          // Find PolySymbolProperty in the supertype hierarchy
          val polySymbolPropertySupertype = propertyClass.superTypes.find { superType ->
            superType.expandedSymbol?.classId?.asSingleFqName()?.asString() == PolySymbolProperty::class.qualifiedName
          } ?: return@analyze null

          // Extract the type parameter T from PolySymbolProperty<T>
          (polySymbolPropertySupertype as? KaClassType)?.typeArguments?.firstOrNull()?.type
        }
      }
    }
  }
}