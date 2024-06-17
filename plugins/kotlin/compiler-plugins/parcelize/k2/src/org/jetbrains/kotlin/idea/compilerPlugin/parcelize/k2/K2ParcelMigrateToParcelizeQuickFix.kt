// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.k2

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.fixes.AbstractKotlinApplicableQuickFix
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelMigrateToParcelizeQuickFixApplicator
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.ParcelMigrateToParcelizeResolver
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes.factory
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class K2ParcelMigrateToParcelizeQuickFix(clazz: KtClass) : AbstractKotlinApplicableQuickFix<KtClass>(clazz) {
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
        private val KtType.classId: ClassId?
            get() = expandedSymbol?.classId

        context(KaSession)
        override val KtCallableDeclaration.returnTypeClassId: ClassId?
            get() = getSymbolOfType<KaCallableSymbol>().returnType.classId

        context(KaSession)
        override val KtCallableDeclaration.receiverTypeClassId: ClassId?
            get() = getSymbolOfType<KaCallableSymbol>().receiverType?.classId

        context(KaSession)
        override val KtCallableDeclaration.overrideCount: Int
            get() = getSymbolOfType<KaCallableSymbol>().getAllOverriddenSymbols().size

        context(KaSession)
        override val KtProperty.isJvmField: Boolean
            get() {
                val symbol = getVariableSymbol() as? KtPropertySymbol ?: return false
                return symbol.hasBackingField && (symbol.backingFieldSymbol?.hasAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_CLASS_ID) == true)
            }

        context(KaSession)
        private fun KaClassLikeSymbol.buildStarProjectedType(): KtType =
            buildClassType(this@buildStarProjectedType) {
                repeat(typeParameters.size) {
                    argument(KtStarTypeProjection(token))
                }
            }

        context(KaSession)
        private fun KtType.hasSuperTypeClassId(superTypeClassId: ClassId): Boolean {
            val superClassSymbol = getClassOrObjectSymbolByClassId(superTypeClassId) ?: return false
            return isSubTypeOf(superClassSymbol.buildStarProjectedType())
        }

        context(KaSession)
        override fun KtClassOrObject.hasSuperClass(superTypeClassId: ClassId): Boolean {
            val subClassSymbol = getClassOrObjectSymbol() ?: return false
            return subClassSymbol.buildStarProjectedType().hasSuperTypeClassId(superTypeClassId)
        }

        context(KaSession)
        override fun KtTypeReference.hasSuperClass(superTypeClassId: ClassId): Boolean =
            getKtType().hasSuperTypeClassId(superTypeClassId)

        context(KaSession)
        override fun KtCallExpression.resolveToConstructedClass(): KtClassOrObject? =
            resolveCallOld()
                ?.successfulConstructorCallOrNull()
                ?.symbol
                ?.containingClassId
                ?.let { getClassOrObjectSymbolByClassId(it) }
                ?.psi as? KtClassOrObject

        context(KaSession)
        override fun KtExpression.evaluateAsConstantInt(): Int? =
            (evaluate() as? KaConstantValue.KaIntConstantValue)?.value
    }

    companion object {
        val FACTORY_FOR_WRITE = factory(::K2ParcelMigrateToParcelizeQuickFix)
        val FACTORY_FOR_CREATOR = factory<KtObjectDeclaration> {
            it.getStrictParentOfType<KtClass>()?.let(::K2ParcelMigrateToParcelizeQuickFix)
        }
    }
}
