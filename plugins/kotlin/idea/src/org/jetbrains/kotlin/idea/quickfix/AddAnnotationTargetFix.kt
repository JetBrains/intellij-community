// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

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
import org.jetbrains.kotlin.idea.util.runOnExpectAndAllActuals
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.EMPTY
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_CLASSIFIER
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_CONSTRUCTOR
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_EXPRESSION
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_LOCAL_VARIABLE
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_MEMBER_FUNCTION
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_MEMBER_PROPERTY
import org.jetbrains.kotlin.resolve.AnnotationTargetLists.T_VALUE_PARAMETER_WITHOUT_VAL
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class AddAnnotationTargetFix(annotationEntry: KtAnnotationEntry) : KotlinQuickFixAction<KtAnnotationEntry>(annotationEntry) {

    override fun getText() = KotlinBundle.message("fix.add.annotation.target")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val annotationEntry = element ?: return

        val (annotationClass, annotationClassDescriptor) = annotationEntry.toAnnotationClass() ?: return

        val requiredAnnotationTargets = annotationEntry.getRequiredAnnotationTargets(annotationClass, annotationClassDescriptor, project)
        if (requiredAnnotationTargets.isEmpty()) return

        val psiFactory = KtPsiFactory(annotationEntry)
        annotationClass.runOnExpectAndAllActuals(useOnSelf = true) {
            it.safeAs<KtClass>()?.addAnnotationTargets(requiredAnnotationTargets, psiFactory)
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
    val ignoreAnnotationTargets = if (annotationClassDescriptor.hasRequiresOptInAnnotation()) {
        setOf(AnnotationTarget.EXPRESSION, AnnotationTarget.FILE, AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
    } else emptySet()
    val annotationTargetValueNames = AnnotationTarget.values().toSet().minus(ignoreAnnotationTargets).map { it.name }
    if (annotationTargetValueNames.isEmpty()) return emptyList()

    val requiredTargets = getActualTargetList()
    if (requiredTargets.isEmpty()) return emptyList()

    val searchScope = GlobalSearchScope.allScope(project)
    val otherReferenceRequiredTargets = ReferencesSearch.search(annotationClass, searchScope).mapNotNull { reference ->
        if (reference.element is KtNameReferenceExpression) {
            // Kotlin annotation
            reference.element.getNonStrictParentOfType<KtAnnotationEntry>()?.takeIf { it != this }?.getActualTargetList()
        } else {
            // Java annotation
            (reference.element.parent as? PsiAnnotation)?.getActualTargetList()
        }
    }.flatten().toSet()

    return (requiredTargets + otherReferenceRequiredTargets).asSequence()
        .distinct()
        .filter { it.name in annotationTargetValueNames }
        .sorted()
        .toList()
}

private fun ClassDescriptor.hasRequiresOptInAnnotation() = annotations.any { it.fqName == FqName("kotlin.RequiresOptIn") }

private fun getActualTargetList(annotated: PsiTarget): AnnotationTargetList {
    return when (annotated) {
        is PsiClass -> T_CLASSIFIER
        is PsiMethod ->
            when {
                annotated.isConstructor -> T_CONSTRUCTOR
                else -> T_MEMBER_FUNCTION
            }
        is PsiExpression -> T_EXPRESSION
        is PsiField -> T_MEMBER_PROPERTY(backingField = true, delegate = false)
        is PsiLocalVariable -> T_LOCAL_VARIABLE
        is PsiParameter -> T_VALUE_PARAMETER_WITHOUT_VAL
        else -> EMPTY
    }
}

private fun PsiAnnotation.getActualTargetList(): List<KotlinTarget> {
    val annotated = parent.parent as? PsiTarget ?: return emptyList()
    return getActualTargetList(annotated).defaultTargets
}

private fun KtAnnotationEntry.getActualTargetList(): List<KotlinTarget> {
    val annotatedElement = getStrictParentOfType<KtModifierList>()?.owner as? KtElement
        ?: getStrictParentOfType<KtAnnotatedExpression>()?.baseExpression
        ?: getStrictParentOfType<KtFile>()
        ?: return emptyList()

    val targetList = AnnotationChecker.getActualTargetList(annotatedElement, null, BindingTraceContext().bindingContext)

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
