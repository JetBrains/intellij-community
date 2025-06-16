// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.core.isOverridable
import org.jetbrains.kotlin.idea.resolve.frontendService
import org.jetbrains.kotlin.idea.util.isEffectivelyActual
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.name.JvmStandardClassIds.TRANSIENT_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable

class RedundantNullableReturnTypeInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            check(function)
        }

        override fun visitProperty(property: KtProperty) {
            if (property.isVar) return
            check(property)
        }

        @OptIn(FrontendInternals::class)
        private fun check(declaration: KtCallableDeclaration) {
            val typeReference = declaration.typeReference ?: return
            val typeElement = typeReference.typeElement as? KtNullableType ?: return
            if (typeElement.innerType == null) return
            val questionMark = typeElement.questionMarkNode as? LeafPsiElement ?: return

            if (declaration.isOverridable() || declaration.isExpectDeclaration() || declaration.isEffectivelyActual()) return

            val dataFlowValueFactory: DataFlowValueFactory = declaration.getResolutionFacade().frontendService<DataFlowValueFactory>()
            val moduleDescriptor: ModuleDescriptor = declaration.findModuleDescriptor()
            val languageVersionSettings: LanguageVersionSettings = declaration.languageVersionSettings

            val declarationDescriptor: DeclarationDescriptor
            val actualReturnTypes = when (declaration) {
                is KtNamedFunction -> {
                    val context = declaration.bodyExpression?.analyze() ?: return
                    declarationDescriptor = declaration.descriptor(context) ?: return
                    declaration.bodyExpression?.actualReturnTypes(
                        context, declarationDescriptor, dataFlowValueFactory, moduleDescriptor, languageVersionSettings
                    ) ?: return
                }

                is KtProperty -> {
                    val initializer = declaration.initializer
                    val getter = declaration.accessors.singleOrNull { it.isGetter }
                    val getterBody = getter?.bodyExpression
                    val context = getterBody?.analyze() ?: initializer?.analyze() ?: return
                    declarationDescriptor = declaration.descriptor(context) ?: return
                    buildList {
                        if (initializer != null) {
                            val actualReturnTypes = initializer.actualReturnTypes(
                                context, declarationDescriptor, dataFlowValueFactory, moduleDescriptor, languageVersionSettings
                            )
                            addAll(actualReturnTypes)
                        }
                        if (getterBody != null) {
                            val getterDescriptor = getter.descriptor(context) ?: return
                            val actualReturnTypes = getterBody.actualReturnTypes(
                                context, getterDescriptor, dataFlowValueFactory, moduleDescriptor, languageVersionSettings
                            )
                            addAll(actualReturnTypes)
                        }
                    }
                }

                else -> return
            }
            if (actualReturnTypes.isEmpty() || actualReturnTypes.any { it.isNullable() }) return
            if (declarationDescriptor.hasJvmTransientAnnotation()) return

            val declarationName = declaration.nameAsSafeName.asString()
            val description = if (declaration is KtProperty) {
                KotlinBundle.message("0.is.always.non.null.type", declarationName)
            } else {
                KotlinBundle.message("0.always.returns.non.null.type", declarationName)
            }
            holder.registerProblem(
                typeReference,
                questionMark.textRangeIn(typeReference),
                description,
                MakeNotNullableFix()
            )
        }
    }

    private fun KtDeclaration.descriptor(context: BindingContext): DeclarationDescriptor? =
        context[BindingContext.DECLARATION_TO_DESCRIPTOR, this]

    private fun DeclarationDescriptor.hasJvmTransientAnnotation() =
        (this as? PropertyDescriptor)?.backingField?.annotations?.findAnnotation(TRANSIENT_ANNOTATION_FQ_NAME) != null

    private fun KtExpression.actualReturnTypes(
        context: BindingContext,
        declarationDescriptor: DeclarationDescriptor,
        dataFlowValueFactory: DataFlowValueFactory,
        moduleDescriptor: ModuleDescriptor,
        languageVersionSettings: LanguageVersionSettings
    ): List<KotlinType> {
        val returnTypes = collectDescendantsOfType<KtReturnExpression> {
            it.getTargetFunctionDescriptor(context) == declarationDescriptor
        }.flatMap {
            it.returnedExpression.types(context, dataFlowValueFactory, moduleDescriptor, languageVersionSettings)
        }
        return if (this is KtBlockExpression) {
            returnTypes
        } else {
            returnTypes + types(context, dataFlowValueFactory, moduleDescriptor, languageVersionSettings)
        }
    }

    private fun KtExpression?.types(
        context: BindingContext,
        dataFlowValueFactory: DataFlowValueFactory,
        moduleDescriptor: ModuleDescriptor,
        languageVersionSettings: LanguageVersionSettings
    ): List<KotlinType> {
        if (this == null) return emptyList()
        val type = context.getType(this) ?: return emptyList()
        val dataFlowInfo = context[BindingContext.EXPRESSION_TYPE_INFO, this]?.dataFlowInfo ?: return emptyList()
        val dataFlowValue = dataFlowValueFactory.createDataFlowValue(this, type, context, moduleDescriptor)
        val stableTypes = dataFlowInfo.getStableTypes(dataFlowValue, languageVersionSettings)
        return if (stableTypes.isNotEmpty()) stableTypes.toList() else listOf(type)
    }

    private class MakeNotNullableFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("make.not.nullable")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val typeReference = descriptor.psiElement as? KtTypeReference ?: return
            val typeElement = typeReference.typeElement as? KtNullableType ?: return
            val innerType = typeElement.innerType ?: return
            typeElement.replace(innerType)
        }
    }
}
