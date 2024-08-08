// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.WRONG_ANNOTATION_TARGET
import org.jetbrains.kotlin.diagnostics.Errors.WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.util.runSynchronouslyWithProgressIfEdt
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationTargetFix.Companion.getExistingAnnotationTargets
import org.jetbrains.kotlin.idea.util.runOnExpectAndAllActuals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.AnnotationChecker
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.TypedArrayValue
import org.jetbrains.kotlin.resolve.descriptorUtil.firstArgument
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddAnnotationTargetFix(annotationEntry: KtAnnotationEntry) : KotlinQuickFixAction<KtAnnotationEntry>(annotationEntry) {

    override fun getText() = KotlinBundle.message("fix.add.annotation.target")

    override fun getFamilyName() = text

    override fun startInWriteAction(): Boolean = false

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val annotationEntry = element ?: return

        val (annotationClass, annotationClassDescriptor) = annotationEntry.toAnnotationClass() ?: return

        val requiredAnnotationTargets = annotationEntry.getRequiredAnnotationTargets(annotationClass, annotationClassDescriptor, project)
        if (requiredAnnotationTargets.isEmpty()) return

        annotationClass.runOnExpectAndAllActuals(useOnSelf = true) {
            val ktClass = it.safeAs<KtClass>() ?: return@runOnExpectAndAllActuals
            runWriteAction {
                val psiFactory = KtPsiFactory(project)
                ktClass.addAnnotationTargets(requiredAnnotationTargets, psiFactory)
            }
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        private fun KtAnnotationEntry.toAnnotationClass(): Pair<KtClass, ClassDescriptor>? {
            val context = analyze(BodyResolveMode.PARTIAL)
            val annotationDescriptor = context[BindingContext.ANNOTATION, this] ?: return null
            val annotationTypeDescriptor = annotationDescriptor.type.constructor.declarationDescriptor as? ClassDescriptor ?: return null
            val annotationClass = (DescriptorToSourceUtils.descriptorToDeclaration(annotationTypeDescriptor) as? KtClass)?.takeIf {
                it.isAnnotation() && it.isWritable
            } ?: return null
            return annotationClass to annotationTypeDescriptor
        }

        // TODO K2 migration: see
        // org.jetbrains.kotlin.j2k.k2.postProcessings.PropertiesDataFilter#getPropertyWithAccessors.accessorsAreAnnotatedWithFunctionOnlyAnnotations.getExistingAnnotationTargets
        fun getExistingAnnotationTargets(annotationClassDescriptor: ClassDescriptor): Set<String> =
            annotationClassDescriptor.annotations
                .firstOrNull { it.fqName == StandardNames.FqNames.target }
                ?.firstArgument()
                .safeAs<TypedArrayValue>()
                ?.value
                ?.mapNotNull { it.safeAs<EnumValue>()?.enumEntryName?.asString() }
                ?.toSet()
                .orEmpty()

        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtAnnotationEntry>? {
            if (diagnostic.factory != WRONG_ANNOTATION_TARGET && diagnostic.factory != WRONG_ANNOTATION_TARGET_WITH_USE_SITE_TARGET) {
                return null
            }

            val entry = diagnostic.psiElement as? KtAnnotationEntry ?: return null
            val (annotationClass, annotationClassDescriptor) = entry.toAnnotationClass() ?: return null
            if (entry.getRequiredAnnotationTargets(annotationClass, annotationClassDescriptor, entry.project).isEmpty()) return null

            return AddAnnotationTargetFix(entry)
        }
    }
}

private fun KtAnnotationEntry.getRequiredAnnotationTargets(
    annotationClass: KtClass,
    annotationClassDescriptor: ClassDescriptor,
    project: Project
): List<KotlinTarget> {
    val ignoredTargets = if (annotationClassDescriptor.hasRequiresOptInAnnotation()) {
        listOf(AnnotationTarget.EXPRESSION, AnnotationTarget.FILE, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
            .map { it.name }
            .toSet()
    } else emptySet()

    val existingTargets = getExistingAnnotationTargets(annotationClassDescriptor)

    val validTargets = AnnotationTarget.values()
        .map { it.name }
        .minus(ignoredTargets)
        .minus(existingTargets)
        .toSet()
    if (validTargets.isEmpty()) return emptyList()

    val requiredTargets = getActualTargetList()
    if (requiredTargets.isEmpty()) return emptyList()

    val searchScope = GlobalSearchScope.allScope(project)
    return project.runSynchronouslyWithProgressIfEdt(KotlinBundle.message("progress.looking.up.add.annotation.usage"), true) {
        val otherReferenceRequiredTargets = ReferencesSearch.search(annotationClass, searchScope).mapNotNull { reference ->
            if (reference.element is KtNameReferenceExpression) {
                // Kotlin annotation
                reference.element
                    .getStrictParentOfType<KtConstructorCalleeExpression>()
                    ?.parent.safeAs<KtAnnotationEntry>()
                    ?.takeIf { it != this }
                    ?.getActualTargetList()
            } else {
                // Java annotation
                (reference.element.parent as? PsiAnnotation)?.getActualTargetList()
            }
        }.flatten().toSet()

        (requiredTargets + otherReferenceRequiredTargets).asSequence()
            .distinct()
            .filter { it.name in validTargets }
            .sorted()
            .toList()
    } ?: emptyList()
}

private fun ClassDescriptor.hasRequiresOptInAnnotation() = annotations.any { it.fqName == FqName("kotlin.RequiresOptIn") }

private fun PsiAnnotation.getActualTargetList(): List<KotlinTarget> {
    val target = when (val annotated = this.parent.parent) {
        is PsiClass -> KotlinTarget.CLASS
        is PsiMethod -> when {
            annotated.isConstructor -> KotlinTarget.CONSTRUCTOR
            else -> KotlinTarget.FUNCTION
        }
        is PsiExpression -> KotlinTarget.EXPRESSION
        is PsiField -> KotlinTarget.FIELD
        is PsiLocalVariable -> KotlinTarget.LOCAL_VARIABLE
        is PsiParameter -> KotlinTarget.VALUE_PARAMETER
        is PsiTypeParameterList -> KotlinTarget.TYPE
        is PsiReferenceList -> KotlinTarget.TYPE_PARAMETER
        else -> null
    }
    return listOfNotNull(target)
}

private fun KtAnnotationEntry.getActualTargetList(): List<KotlinTarget> {
    val annotatedElement = getStrictParentOfType<KtModifierList>()?.owner as? KtElement
        ?: getStrictParentOfType<KtAnnotatedExpression>()?.baseExpression
        ?: getStrictParentOfType<KtFile>()
        ?: return emptyList()

    val targetList = AnnotationChecker.getActualTargetList(annotatedElement, null, BindingTraceContext(this.project).bindingContext)

    val useSiteTarget = this.useSiteTarget ?: return targetList.defaultTargets
    val annotationUseSiteTarget = useSiteTarget.getAnnotationUseSiteTarget()
    val target = KotlinTarget.USE_SITE_MAPPING[annotationUseSiteTarget] ?: return emptyList()

    if (annotationUseSiteTarget == AnnotationUseSiteTarget.FIELD) {
        if (KotlinTarget.MEMBER_PROPERTY !in targetList.defaultTargets && KotlinTarget.TOP_LEVEL_PROPERTY !in targetList.defaultTargets) {
            return emptyList()
        }
        val property = annotatedElement as? KtProperty
        if (property != null && (LightClassUtil.getLightClassPropertyMethods(property).backingField == null || property.hasDelegate())) {
            return emptyList()
        }
    } else {
        if (target !in with(targetList) { defaultTargets + canBeSubstituted + onlyWithUseSiteTarget }) {
            return emptyList()
        }
    }

    return listOf(target)
}

private fun KtClass.addAnnotationTargets(annotationTargets: List<KotlinTarget>, psiFactory: KtPsiFactory) {
    val retentionAnnotationName = StandardNames.FqNames.retention.shortName().asString()
    if (annotationTargets.any { it == KotlinTarget.EXPRESSION }) {
        val retentionEntry = annotationEntries.firstOrNull { it.typeReference?.text == retentionAnnotationName }
        val newRetentionEntry = psiFactory.createAnnotationEntry(
            "@$retentionAnnotationName(${StandardNames.FqNames.annotationRetention.shortName()}.${AnnotationRetention.SOURCE.name})"
        )
        if (retentionEntry == null) {
            addAnnotationEntry(newRetentionEntry)
        } else {
            retentionEntry.replace(newRetentionEntry)
        }
    }

    val targetAnnotationName = StandardNames.FqNames.target.shortName().asString()
    val targetAnnotationEntry = annotationEntries.find { it.typeReference?.text == targetAnnotationName } ?: run {
        val text = "@$targetAnnotationName${annotationTargets.toArgumentListString()}"
        addAnnotationEntry(psiFactory.createAnnotationEntry(text))
        return
    }
    val valueArgumentList = targetAnnotationEntry.valueArgumentList
    if (valueArgumentList == null) {
        val text = annotationTargets.toArgumentListString()
        targetAnnotationEntry.add(psiFactory.createCallArguments(text))
    } else {
        val arguments = targetAnnotationEntry.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
        for (target in annotationTargets) {
            val text = target.asNameString()
            if (text !in arguments) valueArgumentList.addArgument(psiFactory.createArgument(text))
        }
    }
}

private fun List<KotlinTarget>.toArgumentListString() = joinToString(separator = ", ", prefix = "(", postfix = ")") { it.asNameString() }

private fun KotlinTarget.asNameString() = "${StandardNames.FqNames.annotationTarget.shortName().asString()}.$name"
