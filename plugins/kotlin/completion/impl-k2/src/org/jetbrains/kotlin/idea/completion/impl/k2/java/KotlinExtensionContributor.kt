// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.java

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.AutoPopupControllerHelper
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.JavaFrontendCompletionUtil
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.TypedLookupItem
import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.util.ProcessingContext
import com.intellij.util.application
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaUnificationSubstitutorPolicy
import org.jetbrains.kotlin.analysis.api.components.asSignature
import org.jetbrains.kotlin.analysis.api.components.canBeAnalysed
import org.jetbrains.kotlin.analysis.api.components.createUnificationSubstitutor
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.useSiteModule
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinCompletionImplK2Bundle
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.CompletionShortNamesRenderer
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.TailTextProvider
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.TypeTextProvider.getTypeTextForCallable
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import javax.swing.Icon

/**
 * A contributor responsible for completing Kotlin extension members in **Java** files.
 * Kotlin extensions are hard to discover in Java code because accessing them requires qualifying with the
 * name of the containing file.
 * This contributor shows completion options for Kotlin extension members in Java code similar to how they
 * are shown in Kotlin files, as long as the receiver matches.
 * Upon completion, the items are inserted with the correct qualifier and the first argument is replaced by
 * the receiver expression.
 * *Note*: Currently, this contributor is only enabled in mixed Java/Kotlin projects, i.e.,
 * at least one module has the Kotlin plugin enabled.
 *
 * Example:
 * ```kotlin
 * // StringExtensions.kt
 * fun String.someExtension() {}
 * ```
 *
 * ```java
 * void main() {
 *     String s = "";
 *     s.some<caret>
 * }
 * ```
 *
 * will show `someExtension()` as a completion option and complete to
 * ```java
 * void main() {
 *     String s = "";
 *     StringExtensionsKt.someExtension(s);
 * }
 * ```
 */
internal class KotlinExtensionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, psiElement().withParent(PsiReferenceExpression::class.java),
            KotlinExtensionCompletionProvider
        )
        extend(
            CompletionType.SMART, psiElement().withParent(PsiReferenceExpression::class.java),
            KotlinExtensionCompletionProvider
        )
    }
}

private class KotlinExtensionLookupItem(
    private val renderedParameters: String,
    private val methodWrapper: PsiMethod,
    private val containingClass: PsiClass,
    private val qualifierInCopy: PsiExpression,
    private val renderedTail: String,
    private val renderedTypeText: String,
    private val couldInsertSemicolon: Boolean,
    private val originalFile: PsiFile,
    private val icon: Icon,
) : LookupElement(), TypedLookupItem {

    private val methodName: String = methodWrapper.name

    private fun shouldInsertSemicolon(): Boolean {
        return couldInsertSemicolon && (type == null || type == PsiTypes.voidType())
    }

    override fun handleInsert(context: InsertionContext) {
        if (!containingClass.isValid) return

        val document = context.document
        PsiDocumentManager.getInstance(context.project).commitDocument(document)

        val qualifierText = qualifierInCopy.text
        context.document.deleteString(qualifierInCopy.startOffset, qualifierInCopy.endOffset)

        // Note: If there are parameters beyond the receiver, i.e.,
        // if any parameter needs to be added by the user after.
        val hasExtraParams = methodWrapper.parameters.size > 1
        val insertedSemicolon = if (shouldInsertSemicolon()) ";" else ""

        val codeStyleSettings = CodeStyle.getLanguageSettings(originalFile)
        val spaceAfterComma = if (codeStyleSettings.SPACE_AFTER_COMMA) " " else ""

        // The cursor is moved to the `tailOffset` by the platform, so we replace the range exactly up
        // to where the cursor should be moved.
        // Without extra parameters it should be moved after the statement,
        // otherwise it should be moved after the comma of the first parameter.
        val qualifierSuffix = if (hasExtraParams) ",$spaceAfterComma" else ")$insertedSemicolon"

        val newCall = "${containingClass.name}.${methodName}($qualifierText$qualifierSuffix"
        document.replaceString(
            qualifierInCopy.startOffset, context.tailOffset,
            newCall
        )

        // Now we finish the remaining statement. We are after the `tailOffset` so this will not affect where the
        // cursor will be placed in the end.
        if (hasExtraParams) {
            document.insertString(qualifierInCopy.startOffset + newCall.length, ")$insertedSemicolon")
        }
        context.commitDocument()

        (context.file as? PsiJavaFile)?.importClass(containingClass)

        if (hasExtraParams) {
            context.laterRunnable = Runnable {
                AutoPopupControllerHelper.getInstance(context.project)
                    .autoPopupParameterInfoAfterCompletion(context.editor, this)
            }
        }
    }

    override fun getPsiElement(): PsiElement {
        return methodWrapper
    }

    override fun getType(): PsiType? {
        return methodWrapper.returnType
    }

    override fun getLookupString(): String {
        return methodName
    }

    override fun getAllLookupStrings(): Set<String> {
        return setOf(lookupString)
    }

    override fun renderElement(presentation: LookupElementPresentation) {
        // The goal here is to show extensions in a similar way to Kotlin but making clear that it is a Kotlin extension.
        presentation.itemText = methodName
        presentation.appendTailText(renderedParameters, true)
        presentation.appendTailText(" ", true)
        presentation.appendTailText(KotlinCompletionImplK2Bundle.message("kotlin.extension.in.java"), true)
        presentation.appendTailText(renderedTail, true)
        presentation.typeText = renderedTypeText
        presentation.icon = icon
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KotlinExtensionLookupItem) return false
        if (methodWrapper != other.methodWrapper) return false
        if (containingClass != other.containingClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = methodWrapper.hashCode()
        result = 31 * result + containingClass.hashCode()
        return result
    }
}

@OptIn(KaExperimentalApi::class)
private object KotlinExtensionCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val enabledUserDataKey = Key.create<CachedValue<Boolean>>("KOTLIN_PLUGIN_ENABLED")

    context(_: KaSession)
    private fun KaType.processApplicableExtensions(
        prefixMatcher: PrefixMatcher,
        processor: (extension: KaCallableSymbol, methodWrapper: PsiMethod) -> Unit
    ) {
        val extensionsFromIndex = KtSymbolFromIndexProvider(file = null).getExtensionCallableSymbolsByNameFilter(
            { prefixMatcher.prefixMatches(it.asString()) },
            listOf(this)
        ) psiFilter@{ extension ->
            // We only support top-level extensions
            if (extension.containingClassOrObject != null) return@psiFilter false
            // We do not want to show suspend methods
            if (extension.hasModifier(KtTokens.SUSPEND_KEYWORD)) return@psiFilter false

            // Hide non-visible extensions or ones that cannot be analyzed in the current session
            extension.isVisibleIgnoringProtected(useSiteModule) && extension.canBeAnalysed()
        }

        extensionsFromIndex.forEach { extension ->
            // Getting matching extensions from the index does not check for generics inside the type properly,
            // which means false positive matches could be included. We filter them out manually.
            val receiverType = extension.receiverType ?: return@forEach
            val unifier = createUnificationSubstitutor(this, receiverType, KaUnificationSubstitutorPolicy.UNIVERSAL)
            if (unifier == null) return@forEach

            if (extension is KaPropertySymbol) {
                val psi = extension.psi as? KtProperty ?: return@forEach
                val methods = LightClassUtil.getLightClassPropertyMethods(psi)
                val getter = methods.getter
                val setter = methods.setter

                if (getter != null) {
                    processor(extension, getter)
                }
                if (setter != null) {
                    processor(extension, setter)
                }
            } else if (extension is KaNamedFunctionSymbol) {
                val psi = extension.psi as? KtFunction ?: return@forEach
                val methodWrapper = LightClassUtil.getLightClassMethod(psi) ?: return@forEach
                processor(extension, methodWrapper)
            }
        }
    }

    /**
     * Checks if the project has the Kotlin plugin enabled in any of its modules.
     * Caches the results for improved performance because adding Kotlin is a very rare operation.
     */
    private fun Project.hasKotlinPlugin(): Boolean {
        return CachedValuesManager.getManager(this).getCachedValue(this, enabledUserDataKey, {
            val modificationTracker = JavaLibraryModificationTracker.getInstance(this)

            val modules = ModuleManager.getInstance(this).modules
            val result = modules.any { it.hasKotlinPluginEnabled() }
            CachedValueProvider.Result.create(result, modificationTracker)
        }, false)
    }

    context(_: KaSession)
    private fun KaCallableSymbol.renderParameters(methodWrapper: PsiMethod): String = when (this) {
        is KaFunctionSymbol -> {
            CompletionShortNamesRenderer.renderFunctionParameters(valueParameters.map { it.asSignature() })
        }

        is KaPropertySymbol if methodWrapper.parameterList.parametersCount > 1 -> {
            val setterParameters = setter?.valueParameters ?: emptyList()
            CompletionShortNamesRenderer.renderFunctionParameters(setterParameters.map { it.asSignature() })
        }

        else -> {
            "()"
        }
    }

    private fun KaSymbol.getExtensionIcon(): Icon = when (this) {
        is KaPropertySymbol if !isVal -> KotlinIcons.FIELD_VAR_KOTLIN
        is KaPropertySymbol -> KotlinIcons.FIELD_VAL_KOTLIN
        else -> KotlinIcons.FUNCTION_KOTLIN
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        if (!Registry.`is`("kotlin.java.completion.extensions", false)) return
        // We only show extension methods in mixed Java/Kotlin projects
        if (!parameters.position.project.hasKotlinPlugin()) return
        // Completion can (rarely) be run synchronously, we do not want to use the contributor
        // in this case due to analysis potentially taking a long time.
        if (application.isWriteAccessAllowed) return

        // We run other contributors before this one to not reduce performance
        result.runRemainingContributors(parameters, true)

        val parent = parameters.position.parent
        if (parent !is PsiReferenceExpression) return
        val qualifierInCopy = parent.qualifierExpression ?: return
        val qualifierType = qualifierInCopy.type ?: return

        val kaModule = parameters.originalFile.getKaModule(parent.project, null)

        val expectedTypes by lazy {
            JavaSmartCompletionContributor.getExpectedTypes(parameters)
        }

        analyze(kaModule) {
            val qualifierKaType = qualifierType.asKaType(parameters.originalFile)?.lowerBoundIfFlexible() ?: return@analyze
            qualifierKaType.processApplicableExtensions(result.prefixMatcher) { extension, methodWrapper ->
                val containingClass = methodWrapper.containingClass ?: return@processApplicableExtensions

                if (parameters.completionType == CompletionType.SMART) {
                    // Do not show extensions if they do not match expected types during smart completion
                    val type = methodWrapper.returnType
                    if (type == null || expectedTypes.none { it.type.isAssignableFrom(type) }) {
                        return@processApplicableExtensions
                    }
                }

                val renderedParameters = extension.renderParameters(methodWrapper)
                val couldInsertSemicolon = JavaFrontendCompletionUtil.insertSemicolon(parent.parent)

                val element = KotlinExtensionLookupItem(
                    methodWrapper = methodWrapper,
                    containingClass = containingClass,
                    renderedParameters = renderedParameters,
                    renderedTypeText = getTypeTextForCallable(extension.asSignature(), treatAsFunctionCall = true),
                    renderedTail = TailTextProvider.getTailText(extension),
                    qualifierInCopy = qualifierInCopy,
                    couldInsertSemicolon = couldInsertSemicolon,
                    originalFile = parameters.originalFile,
                    icon = extension.getExtensionIcon(),
                )

                result.addElement(element)
            }
        }
    }
}
