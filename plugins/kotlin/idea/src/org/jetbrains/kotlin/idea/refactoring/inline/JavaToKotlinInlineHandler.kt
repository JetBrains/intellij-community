// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeInliner.CodeToInlineBuilder
import org.jetbrains.kotlin.idea.codeInliner.PropertyUsageReplacementStrategy
import org.jetbrains.kotlin.idea.codeInliner.unwrapSpecialUsageOrNull
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.refactoring.inline.J2KInlineCache.Companion.findOrCreateUsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.J2KInlineCache.Companion.findUsageReplacementStrategy
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.J2kConverterExtension
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.PostProcessingTarget.MultipleFilesPostProcessingTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter.Companion.addImports
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAssignmentByLHS
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized

class JavaToKotlinInlineHandler : AbstractCrossLanguageInlineHandler() {
    override fun prepareReference(reference: PsiReference, referenced: PsiElement): MultiMap<PsiElement, String> {
        val referenceElement = reference.element
        if (referenceElement.language == KotlinLanguage.INSTANCE) {
            KotlinInlineRefactoringFUSCollector.log(elementFrom = referenced, languageTo = KotlinLanguage.INSTANCE, isCrossLanguage = true)
        }

        val javaMemberToInline = referenced.javaMemberToInline ?: return super.prepareReference(reference, referenced)
        validate(javaMemberToInline, referenceElement)?.let { error ->
            return createMultiMapWithSingleConflict(referenceElement, error)
        }

        try {
            val strategy = findOrCreateUsageReplacementStrategy(javaMemberToInline, referenceElement)
            if (strategy == null) KotlinBundle.message("failed.to.create.a.wrapper.for.inlining.to.kotlin") else null
        } catch (e: IllegalStateException) {
            LOG.error(e)
            e.message
        }?.let { error ->
            return createMultiMapWithSingleConflict(referenceElement, error)
        }

        return MultiMap.empty()
    }

    override fun performInline(usage: UsageInfo, referenced: PsiElement) {
        val unwrappedUsage = unwrapUsage(usage) ?: kotlin.run {
            LOG.error("Kotlin usage in $usage not found (element ${usage.element}")
            return
        }

        val unwrappedElement = unwrapElement(unwrappedUsage, referenced)
        val replacementStrategy = referenced.findUsageReplacementStrategy(withValidation = false) ?: kotlin.run {
            LOG.error("Can't find strategy for ${unwrappedElement::class} (${unwrappedElement.kotlinFqName}) => ${unwrappedElement.text}")
            return
        }

        replacementStrategy.createReplacer(unwrappedElement)?.invoke()
    }

    companion object {
        private val LOG = Logger.getInstance(JavaToKotlinInlineHandler::class.java)
    }
}

private val PsiElement.javaMemberToInline: PsiMember?
    get() = if (language == JavaLanguage.INSTANCE && (this is PsiMethod || this is PsiField)) this as PsiMember else null

private fun validate(referenced: PsiMember, reference: PsiElement): String? = when {
    referenced is PsiField && !referenced.hasInitializer() -> KotlinBundle.message("a.field.without.an.initializer.is.not.yet.supported")
    referenced is PsiMethod && referenced.isConstructor -> KotlinBundle.message("a.constructor.call.is.not.yet.supported")
    else -> findCallableConflictForUsage(reference)
}

private fun NewJavaToKotlinConverter.convertToKotlinNamedDeclaration(
    referenced: PsiMember,
    context: PsiElement,
): KtNamedDeclaration {
    val converterExtension = J2kConverterExtension.extension(kind = K1_NEW)
    val postProcessor = converterExtension.createPostProcessor()
    val processor = converterExtension.createWithProgressProcessor(
        progress = ProgressManager.getInstance().progressIndicator,
        files = listOf(referenced.containingFile as PsiJavaFile),
        phasesCount = phasesCount + postProcessor.phasesCount,
    )

    val (j2kResults, _, j2kContext) = ActionUtil.underModalProgress(project, KotlinBundle.message("action.j2k.name")) {
        elementsToKotlin(
            inputElements = listOf(referenced),
            processor = processor,
            bodyFilter = { it == referenced },
            forInlining = true
        )
    }

    val file = runReadAction {
        val factory = KtPsiFactory.contextual(context)
        val className = referenced.containingClass?.qualifiedName
        val j2kResult = j2kResults.first() ?: error("Can't convert to Kotlin ${referenced.text}")
        factory.createFile("dummy.kt", text = "class DuMmY_42_ : $className {\n${j2kResult.text}\n}")
            .also { it.addImports(j2kResult.importsToAdd) }
    }

    postProcessor.doAdditionalProcessing(
        target = MultipleFilesPostProcessingTarget(files = listOf(file)),
        converterContext = j2kContext,
        onPhaseChanged = { i, s -> processor.updateState(null, phasesCount + i, s) },
    )

    val fakeClass = file.declarations.singleOrNull() as? KtClass ?: error("Can't find dummy class in ${file.text}")
    return fakeClass.declarations.singleOrNull() as? KtNamedDeclaration ?: error("Can't find fake declaration in ${file.text}")
}

private fun unwrapUsage(usage: UsageInfo): KtReferenceExpression? {
    val ktReferenceExpression = usage.element as? KtReferenceExpression ?: return null
    return unwrapSpecialUsageOrNull(ktReferenceExpression) ?: ktReferenceExpression
}

private fun unwrapElement(unwrappedUsage: KtReferenceExpression, referenced: PsiElement): KtReferenceExpression {
    if (referenced !is PsiMember) return unwrappedUsage
    val name = referenced.name ?: return unwrappedUsage
    if (unwrappedUsage.textMatches(name)) return unwrappedUsage

    val qualifiedElementOrReference = unwrappedUsage.getQualifiedExpressionForSelectorOrThis()
    val assignment = qualifiedElementOrReference.getAssignmentByLHS()?.takeIf { it.operationToken == KtTokens.EQ } ?: return unwrappedUsage
    val argument = assignment.right ?: return unwrappedUsage
    if (unwrappedUsage.resolveToCall()?.resultingDescriptor?.isSynthesized != true) return unwrappedUsage

    val psiFactory = KtPsiFactory(unwrappedUsage.project)
    val callExpression = psiFactory.createExpressionByPattern("$name($0)", argument) as? KtCallExpression ?: return unwrappedUsage
    val resultExpression = assignment.replaced(unwrappedUsage.replaced(callExpression).getQualifiedExpressionForSelectorOrThis())
    return resultExpression.getQualifiedElementSelector() as KtReferenceExpression
}

class J2KInlineCache(private val strategy: UsageReplacementStrategy, private val originalText: String) {
    /**
     * @return [strategy] without validation if [elementToValidation] is null
     */
    private fun getStrategy(elementToValidation: PsiElement?): UsageReplacementStrategy? = strategy.takeIf {
        elementToValidation?.textMatches(originalText) != false
    }

    companion object {
        private val JAVA_TO_KOTLIN_INLINE_CACHE_KEY = Key<J2KInlineCache>("JAVA_TO_KOTLIN_INLINE_CACHE")

        fun PsiElement.findUsageReplacementStrategy(withValidation: Boolean): UsageReplacementStrategy? =
            getUserData(JAVA_TO_KOTLIN_INLINE_CACHE_KEY)?.getStrategy(this.takeIf { withValidation })

        fun PsiElement.setUsageReplacementStrategy(strategy: UsageReplacementStrategy): Unit =
            putUserData(JAVA_TO_KOTLIN_INLINE_CACHE_KEY, J2KInlineCache(strategy, text))

        internal fun findOrCreateUsageReplacementStrategy(javaMember: PsiMember, context: PsiElement): UsageReplacementStrategy? {
            javaMember.findUsageReplacementStrategy(withValidation = true)?.let { return it }

            val converter = NewJavaToKotlinConverter(
                javaMember.project,
                javaMember.module,
                ConverterSettings.defaultSettings
            )

            val declaration = converter.convertToKotlinNamedDeclaration(
                referenced = javaMember,
                context = context,
            )

            return createUsageReplacementStrategyForNamedDeclaration(
                declaration,
                javaMember.findExistingEditor(),
                fallbackToSuperCall = javaMember.containingClass?.hasModifier(JvmModifier.FINAL) == true,
            )?.also { javaMember.setUsageReplacementStrategy(it) }
        }
    }
}

private fun createUsageReplacementStrategyForNamedDeclaration(
    namedDeclaration: KtNamedDeclaration,
    editor: Editor?,
    fallbackToSuperCall: Boolean,
): UsageReplacementStrategy? = when (namedDeclaration) {
    is KtNamedFunction -> createUsageReplacementStrategyForFunction(
        function = namedDeclaration,
        editor = editor,
        fallbackToSuperCall = fallbackToSuperCall,
    )

    is KtProperty -> createReplacementStrategyForProperty(
        property = namedDeclaration,
        editor = editor,
        project = namedDeclaration.project,
        { decl, fallback ->
            CodeToInlineBuilder(
                originalDeclaration = decl,
                fallbackToSuperCall = fallback,
            )
        },
        { readReplacement, writeReplacement -> PropertyUsageReplacementStrategy(readReplacement, writeReplacement) },
        fallbackToSuperCall = fallbackToSuperCall
    )

    else -> null
}
