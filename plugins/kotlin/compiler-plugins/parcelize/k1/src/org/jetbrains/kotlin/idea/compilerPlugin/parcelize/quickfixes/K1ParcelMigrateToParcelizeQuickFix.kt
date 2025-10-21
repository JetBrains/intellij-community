// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.compilerPlugin.parcelize.quickfixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.compilerPlugin.parcelize.KotlinParcelizeBundle
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.TypeUtils

internal class K1ParcelMigrateToParcelizeQuickFix(clazz: KtClass) : KotlinQuickFixAction<KtClass>(clazz) {
    override fun getText() = KotlinParcelizeBundle.message("parcelize.fix.migrate.to.parceler.companion.object")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val clazz = element ?: return
        val applicator = ParcelMigrateToParcelizeQuickFixApplicator(Resolver)
        val preparedAction = with(clazz.analyze()) { applicator.prepare(clazz) }

        val ktPsiFactory = KtPsiFactory(project, markGenerated = true)
        preparedAction.execute(clazz, ktPsiFactory)
    }

    private object Resolver : ParcelMigrateToParcelizeResolver<BindingContext> {
        context(context: BindingContext)
        private fun KtTypeReference.getClassId() =
            context[BindingContext.TYPE, this]
                ?.constructor
                ?.declarationDescriptor
                ?.let { DescriptorUtils.getClassIdForNonLocalClass(it) }

        context(_: BindingContext)
        override val KtCallableDeclaration.returnTypeClassId: ClassId?
            get() = typeReference?.getClassId()

        context(_: BindingContext)
        override val KtCallableDeclaration.receiverTypeClassId: ClassId?
            get() = receiverTypeReference?.getClassId()

        context(context: BindingContext)
        override val KtCallableDeclaration.overrideCount: Int
            get() = context[BindingContext.FUNCTION, this]?.overriddenDescriptors?.size ?: 0

        context(_: BindingContext)
        override val KtProperty.isJvmField: Boolean
            get() = findAnnotation(JvmAbi.JVM_FIELD_ANNOTATION_FQ_NAME) != null

        private fun ClassifierDescriptor.hasSuperType(superTypeClassId: ClassId): Boolean =
            getAllSuperClassifiers().any { DescriptorUtils.getClassIdForNonLocalClass(it) == superTypeClassId }

        context(context: BindingContext)
        override fun KtClassOrObject.hasSuperClass(superTypeClassId: ClassId): Boolean =
            context[BindingContext.CLASS, this]?.hasSuperType(superTypeClassId) ?: false

        context(context: BindingContext)
        override fun KtTypeReference.hasSuperClass(superTypeClassId: ClassId): Boolean =
            context[BindingContext.TYPE, this]?.constructor?.declarationDescriptor?.hasSuperType(superTypeClassId) ?: false

        context(context: BindingContext)
        override fun KtCallExpression.resolveToConstructedClass(): KtClassOrObject? =
            (getResolvedCall(context)?.resultingDescriptor as? ConstructorDescriptor)
                ?.constructedClass
                ?.source
                ?.getPsi()
                as? KtClassOrObject

        context(context: BindingContext)
        override fun KtExpression.evaluateAsConstantInt(): Int? =
            context[BindingContext.COMPILE_TIME_VALUE, this]?.getValue(TypeUtils.NO_EXPECTED_TYPE) as? Int
    }

    companion object {
        val FACTORY_FOR_WRITE = factory(::K1ParcelMigrateToParcelizeQuickFix)
        val FACTORY_FOR_CREATOR = factory<KtObjectDeclaration> {
            it.getStrictParentOfType<KtClass>()?.let(::K1ParcelMigrateToParcelizeQuickFix)
        }
    }
}
