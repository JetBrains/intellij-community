// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinPsiDiagnosticBasedInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import kotlin.reflect.KClass

internal class MapPlatformClassToKotlinInspection :
    KotlinPsiDiagnosticBasedInspectionBase<KtElement, KaFirDiagnostic.PlatformClassMappedToKotlin, MapPlatformClassToKotlinInspection.ElementContext>() {
    data class ElementContext(
        val kotlinClass: ClassId,
    )

    override val diagnosticType: KClass<KaFirDiagnostic.PlatformClassMappedToKotlin>
        get() = KaFirDiagnostic.PlatformClassMappedToKotlin::class

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> {
        val file = holder.file
        return if (file !is KtFile ||
            InjectedLanguageManager.getInstance(holder.project).isInjectedViewProvider(file.viewProvider)
        ) {
            KtVisitorVoid()
        } else {
            object : KtVisitorVoid() {
                override fun visitImportDirective(importDirective: KtImportDirective) {
                    visitTargetElement(importDirective, holder, isOnTheFly)
                }

                override fun visitTypeReference(typeReference: KtTypeReference) {
                    visitTargetElement(typeReference, holder, isOnTheFly)
                }
            }
        }
    }

    override fun KaSession.prepareContextByDiagnostic(
        element: KtElement,
        diagnostic: KaFirDiagnostic.PlatformClassMappedToKotlin
    ): ElementContext =
        ElementContext(diagnostic.kotlinClass)

    override fun getProblemDescription(
        element: KtElement,
        context: ElementContext,
    ): @InspectionMessage String =
        KotlinBundle.message("change.to.kotlin.class")

    override fun createQuickFixes(
        element: KtElement,
        context: ElementContext,
    ): Array<KotlinModCommandQuickFix<KtElement>> {
        val smartPointer = element.createSmartPointer()
        return arrayOf(object : KotlinModCommandQuickFix<KtElement>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("change.to.kotlin.class")

            @OptIn(KaExperimentalApi::class)
            override fun getName(): String = getName(smartPointer) { element ->
                val kotlinClass = context.kotlinClass
                val (platformClass, typeParamString) = analyze(element) {
                    val platformClass = when (element) {
                        is KtImportDirective -> {
                            // PlatformClassMappedToKotlinImportsChecker
                            val ref = element.importedReference?.getQualifiedElementSelector() as? KtReferenceExpression
                            (ref?.mainReference?.resolveToSymbol() as? KaClassSymbol)?.classId
                        }

                        is KtTypeReference -> {
                            // PlatformClassMappedToKotlinTypeRefChecker
                            (element.type as? KaClassType)?.classId
                        }

                        else -> null
                    } ?: return@analyze null to null
                    val kotlinClassType = buildClassType(kotlinClass) as? KaClassType
                    val typeParameters = kotlinClassType?.symbol?.typeParameters ?: return@analyze platformClass to null
                    val typeParamString = typeParameters.joinToString(prefix = "<", postfix = ">") { it.name.identifier }
                    platformClass to typeParamString
                }
                if (platformClass != null && typeParamString != null) {
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

            override fun applyFix(
                project: Project,
                element: KtElement,
                updater: ModPsiUpdater
            ) {
                val elt = element.let(updater::getWritable)
                when (elt) {
                    is KtImportDirective -> {
                        // PlatformClassMappedToKotlinImportsChecker
                        elt.delete()
                    }
                    is KtTypeReference -> {
                        // PlatformClassMappedToKotlinTypeRefChecker
                        val typeElement = elt.typeElement
                        val typeArguments = typeElement?.typeArgumentsAsTypes
                        val typeArgumentsString = typeArguments?.joinToString(prefix = "<", postfix = ">") { it.text } ?: ""
                        val nullity = if (typeElement is KtNullableType) "?" else ""
                        val factory = KtPsiFactory(element.project)
                        val replacementType =
                            factory.createType(context.kotlinClass.shortClassName.identifier + typeArgumentsString + nullity)
                        elt.replace(replacementType)
                    }
                }
            }
        })
    }
}