// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic.PlatformClassMappedToKotlin
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

internal object MapPlatformClassToKotlinFixFactory {
    val mapPlatformClassToKotlinFixFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: PlatformClassMappedToKotlin ->
        listOfNotNull(
            (diagnostic.psi as? KtElement)?.let { ktElement ->
                MapPlatformClassToKotlinFix(ktElement, ElementContext(diagnostic.kotlinClass))
            }
        )
    }

    private data class ElementContext(
        val kotlinClass: ClassId,
    )

    private class MapPlatformClassToKotlinFix(
        private val ktElement: KtElement,
        private val elementContext: ElementContext,
    ) : KotlinQuickFixAction<KtElement>(ktElement) {
        override fun invoke(project: Project, editor: Editor?, file: KtFile) {
            val imports = mutableListOf<KtImportDirective>()
            val typeRefs = mutableMapOf<KtTypeReference, ClassId>()

            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                @OptIn(KaAllowAnalysisOnEdt::class)
                allowAnalysisOnEdt {
                    analyze(file) {
                        val analysis =
                            file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
                                .filterIsInstance<PlatformClassMappedToKotlin>()
                        for (diagnostic in analysis) {
                            when (val psi = diagnostic.psi) {
                                is KtImportDirective -> {
                                    // PlatformClassMappedToKotlinImportsChecker
                                    imports.add(psi)
                                }
                                is KtTypeReference -> {
                                    // PlatformClassMappedToKotlinTypeRefChecker
                                    typeRefs[psi] = diagnostic.kotlinClass
                                }
                            }
                        }
                    }
                }
            }

            for (importDirective in imports) {
                importDirective.delete()
            }

            val factory = KtPsiFactory(ktElement.project)

            for (usage in typeRefs) {
                val (typeRef, classId) = usage
                val typeElement = typeRef.typeElement
                val typeArguments = typeElement?.typeArgumentsAsTypes
                val typeArgumentsString = typeArguments?.joinToString(prefix = "<", postfix = ">") { it.text } ?: ""
                val nullity = if (typeElement is KtNullableType) "?" else ""
                val replacementType =
                    factory.createType(classId.shortClassName.identifier + typeArgumentsString + nullity)
                typeRef.replace(replacementType)
            }
        }

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("change.to.kotlin.class")

        @OptIn(KaExperimentalApi::class)
        override fun getText(): String {
            val kotlinClass = elementContext.kotlinClass
            val (platformClass, typeParamString) = analyze(ktElement) {
                val platformClass = when (ktElement) {
                    is KtImportDirective -> {
                        // PlatformClassMappedToKotlinImportsChecker
                        val ref = ktElement.importedReference?.getQualifiedElementSelector() as? KtReferenceExpression
                        (ref?.mainReference?.resolveToSymbol() as? KaClassSymbol)?.classId
                    }
                    is KtTypeReference -> {
                        // PlatformClassMappedToKotlinTypeRefChecker
                        (ktElement.type as? KaClassType)?.classId
                    }
                    else -> null
                } ?: return@analyze null to null
                val kotlinClassType = buildClassType(kotlinClass) as? KaClassType
                val typeParameters = kotlinClassType?.symbol?.typeParameters ?: return@analyze platformClass to null
                val typeParamString = typeParameters.joinToString(prefix = "<", postfix = ">") { it.name.identifier }
                platformClass to typeParamString
            }
            return if (platformClass != null && typeParamString != null) {
                KotlinBundle.message(
                    "change.all.usages.of.0.in.this.file.to.1",
                    platformClass.asFqNameString() + typeParamString,
                    kotlinClass.asFqNameString() + typeParamString,
                )
            } else {
                KotlinBundle.message(
                    "change.all.usages.of.0.in.this.file.to.a.kotlin.class",
                    platformClass?.asFqNameString() ?: "<unknown platform class>",
                )
            }
        }
    }
}