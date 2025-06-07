// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.k2

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.*
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
        context(KaSession)
        private val KaType.classId: ClassId?
            get() = expandedSymbol?.classId

        context(KaSession)
        override val KtCallableDeclaration.returnTypeClassId: ClassId?
            get() = (symbol as KaCallableSymbol).returnType.classId

        context(KaSession)
        override val KtCallableDeclaration.receiverTypeClassId: ClassId?
            get() = (symbol as KaCallableSymbol).receiverType?.classId

        context(KaSession)
        override val KtCallableDeclaration.overrideCount: Int
            get() = (symbol as KaCallableSymbol).allOverriddenSymbols.count()

        context(KaSession)
        override val KtProperty.isJvmField: Boolean
            get() {
                val symbol = symbol as? KaPropertySymbol ?: return false
                return symbol.hasBackingField
                        && (symbol.backingFieldSymbol?.annotations?.contains(JvmAbi.JVM_FIELD_ANNOTATION_CLASS_ID) == true)
            }

        context(KaSession)
        @OptIn(KaExperimentalApi::class)
        private fun KaClassLikeSymbol.buildStarProjectedType(): KaType =
            buildClassType(this@buildStarProjectedType) {
                @OptIn(KaExperimentalApi::class)
                repeat(typeParameters.size) {
                    argument(buildStarTypeProjection())
                }
            }

        context(KaSession)
        private fun KaType.hasSuperTypeClassId(superTypeClassId: ClassId): Boolean {
            val superClassSymbol = findClass(superTypeClassId) ?: return false
            return isSubtypeOf(superClassSymbol.buildStarProjectedType())
        }

        context(KaSession)
        override fun KtClassOrObject.hasSuperClass(superTypeClassId: ClassId): Boolean {
            val subClassSymbol = classSymbol ?: return false
            return subClassSymbol.buildStarProjectedType().hasSuperTypeClassId(superTypeClassId)
        }

        context(KaSession)
        override fun KtTypeReference.hasSuperClass(superTypeClassId: ClassId): Boolean =
            type.hasSuperTypeClassId(superTypeClassId)

        context(KaSession)
        override fun KtCallExpression.resolveToConstructedClass(): KtClassOrObject? =
            resolveToCall()
                ?.successfulConstructorCallOrNull()
                ?.symbol
                ?.containingClassId
                ?.let { findClass(it) }
                ?.psi as? KtClassOrObject

        context(KaSession)
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
