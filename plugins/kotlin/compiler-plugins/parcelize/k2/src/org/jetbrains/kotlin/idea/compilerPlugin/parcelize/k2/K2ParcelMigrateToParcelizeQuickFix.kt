// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.k2

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.components.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.buildStarTypeProjection
import org.jetbrains.kotlin.analysis.api.components.defaultType
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelMigrateToParcelizeQuickFixApplicator
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelMigrateToParcelizeResolver
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.factory
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class K2ParcelMigrateToParcelizeQuickFix(clazz: KtClass) : AbstractKotlinApplicableQuickFix<KtClass>(clazz) {
    override fun getFamilyName() = KotlinParcelizeBundle.message("parcelize.fix.migrate.to.parceler.companion.object")

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun apply(element: KtClass, project: Project, editor: Editor?, file: KtFile) {
        val preparedAction = allowAnalysisOnEdt {
            analyze(element) {
                ParcelMigrateToParcelizeQuickFixApplicator(Resolver).prepare(element)
            }
        }

        val ktPsiFactory = KtPsiFactory(project, markGenerated = true)
        preparedAction.execute(element, ktPsiFactory)
    }

    private object Resolver : ParcelMigrateToParcelizeResolver<KaSession> {
        context(_: KaSession)
        private val KaType.classId: ClassId?
            get() = expandedSymbol?.classId

        context(_: KaSession)
        override val KtCallableDeclaration.returnTypeClassId: ClassId?
            get() = (symbol as KaCallableSymbol).returnType.classId

        context(_: KaSession)
        override val KtCallableDeclaration.receiverTypeClassId: ClassId?
            get() = (symbol as KaCallableSymbol).receiverType?.classId

        context(_: KaSession)
        override val KtCallableDeclaration.overrideCount: Int
            get() = (symbol as KaCallableSymbol).allOverriddenSymbols.count()

        context(_: KaSession)
        override val KtProperty.isJvmField: Boolean
            get() {
                val symbol = symbol as? KaPropertySymbol ?: return false
                return symbol.hasBackingField
                        && (symbol.backingFieldSymbol?.annotations?.contains(JvmAbi.JVM_FIELD_ANNOTATION_CLASS_ID) == true)
            }

        context(_: KaSession)
        @OptIn(KaExperimentalApi::class)
        private fun KaClassLikeSymbol.buildStarProjectedType(): KaType =
            buildClassType(this@buildStarProjectedType) {
                @OptIn(KaExperimentalApi::class)
                repeat((this@buildStarProjectedType.defaultType as? KaClassType)?.qualifiers?.sumOf { it.typeArguments.size } ?: 0) {
                    argument(buildStarTypeProjection())
                }
            }

        context(_: KaSession)
        private fun KaType.hasSuperTypeClassId(superTypeClassId: ClassId): Boolean {
            val superClassSymbol = findClass(superTypeClassId) ?: return false
            return isSubtypeOf(superClassSymbol.buildStarProjectedType())
        }

        context(_: KaSession)
        override fun KtClassOrObject.hasSuperClass(superTypeClassId: ClassId): Boolean {
            val subClassSymbol = classSymbol ?: return false
            return subClassSymbol.buildStarProjectedType().hasSuperTypeClassId(superTypeClassId)
        }

        context(_: KaSession)
        override fun KtTypeReference.hasSuperClass(superTypeClassId: ClassId): Boolean =
            type.hasSuperTypeClassId(superTypeClassId)

        context(_: KaSession)
        override fun KtCallExpression.resolveToConstructedClass(): KtClassOrObject? =
            resolveToCall()
                ?.successfulConstructorCallOrNull()
                ?.symbol
                ?.containingClassId
                ?.let { findClass(it) }
                ?.psi as? KtClassOrObject

        context(_: KaSession)
        override fun KtExpression.evaluateAsConstantInt(): Int? =
            (evaluate() as? KaConstantValue.IntValue)?.value
    }

    companion object {
        val FACTORY_FOR_WRITE = factory(::K2ParcelMigrateToParcelizeQuickFix)
        val FACTORY_FOR_CREATOR = factory<KtObjectDeclaration> {
            it.getStrictParentOfType<KtClass>()?.let(::K2ParcelMigrateToParcelizeQuickFix)
        }
    }
}
