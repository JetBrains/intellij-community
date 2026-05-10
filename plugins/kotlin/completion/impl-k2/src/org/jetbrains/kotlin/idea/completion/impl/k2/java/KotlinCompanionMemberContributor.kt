// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.java

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.JavaChainLookupElement
import com.intellij.codeInsight.completion.JavaMethodCallElement
import com.intellij.codeInsight.completion.JavaSmartCompletionContributor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.TypedLookupItem
import com.intellij.codeInsight.lookup.VariableLookupItem
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReferenceExpression
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.useSiteModule
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.addIfNotNull

internal class KotlinCompanionMemberContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, psiElement().withParent(PsiReferenceExpression::class.java),
            KotlinCompanionMemberCompletionProvider
        )
        extend(
            CompletionType.SMART, psiElement().withParent(PsiReferenceExpression::class.java),
            KotlinCompanionMemberCompletionProvider
        )
    }
}

private class KotlinCompanionMemberLookupElement(
    qualifier: LookupElement,
    private val main: LookupElement,
) : JavaChainLookupElement(qualifier, main) {

    /**
     * The original [JavaChainLookupElement] method does not take into account the lookup strings of [main]
     * which prevents elements showing up when any prefix is used.
     * This method also returns the lookup strings of [main] to ensure that completion is similar if there were no
     * `.Companion` prefix necessary.
     */
    override fun getAllLookupStrings(): Set<String> {
        return super.getAllLookupStrings() + main.allLookupStrings
    }
}

private object KotlinCompanionMemberCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val JVM_FIELD = ClassId(StandardClassIds.BASE_JVM_PACKAGE, Name.identifier("JvmField"))

    context(_: KaSession)
    private fun KaDeclarationSymbol.isMarkedAsStatic(): Boolean {
        return annotations.any { it.classId == StandardClassIds.Annotations.jvmStatic }
    }

    context(_: KaSession)
    private fun KaDeclarationSymbol.isJvmField(): Boolean {
        return annotations.any { it.classId == JVM_FIELD }
    }

    /**
     * Given the [declaration] returns a list of Java lookup elements for companion members.
     * In the case of properties, two such elements - for the getter and setter - might be returned.
     * In other cases at most one element is returned.
     */
    context(_: KaSession)
    private fun createCompanionLookupElements(declaration: KaDeclarationSymbol): List<LookupElement> = buildList {
        if (declaration is KaPropertySymbol) {
            val psi = declaration.psi as? KtProperty ?: return@buildList
            val methods = LightClassUtil.getLightClassPropertyMethods(psi)
            val getter = methods.getter
            val setter = methods.setter

            addIfNotNull(getter?.let(::JavaMethodCallElement))
            addIfNotNull(setter?.let(::JavaMethodCallElement))
        } else if (declaration is KaFunctionSymbol) {
            val psi = declaration.psi as? KtFunction ?: return@buildList
            val method = LightClassUtil.getLightClassMethod(psi) ?: return@buildList
            add(JavaMethodCallElement(method))
        }
    }

    /**
     * Checks if the given companion object's declaration symbol is visible at the [positionFile].
     * The checks here are simpler because companion object symbols cannot be abstract for example.
     */
    context(_: KaSession)
    private fun KaDeclarationSymbol.isVisibleAtPosition(positionFile: PsiFile): Boolean = when (visibility) {
        KaSymbolVisibility.PUBLIC -> true
        KaSymbolVisibility.INTERNAL -> {
            val positionModule = positionFile.getKaModule(positionFile.project, useSiteModule)
            positionModule == useSiteModule || positionModule in useSiteModule.directFriendDependencies
        }
        else -> false
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val parent = parameters.position.parent
        if (parent !is PsiReferenceExpression) return
        val firstChild = parent.qualifierExpression as? PsiReferenceExpression ?: return
        val resolvedElement = firstChild.resolve() ?: return
        val ktClass = (resolvedElement as? KtLightElement<*, *>)?.kotlinOrigin as? KtClassOrObject ?: return

        val companionObject = ktClass.companionObjects.firstOrNull() ?: return
        val companionObjectField = LightClassUtil.getLightFieldForCompanionObject(companionObject) ?: return
        val companionObjectLookupElement = VariableLookupItem(companionObjectField, true)

        val expectedTypes by lazy {
            JavaSmartCompletionContributor.getExpectedTypes(parameters)
        }

        val foundMembers: MutableSet<PsiElement> = mutableSetOf()

        analyze(companionObject) {
            val resolvedCompanion = companionObject.symbol
            val allMembers = resolvedCompanion.memberScope

            for (declaration in allMembers.declarations) {
                // If the declaration is marked as static or JvmField, it will already appear without `.Companion`.
                if (declaration.isMarkedAsStatic() || declaration.isJvmField()) continue
                if (!declaration.isVisibleAtPosition(parameters.originalFile)) continue
                // Hide suspend functions because they cannot easily be called from Java
                if (declaration is KaNamedFunctionSymbol && declaration.isSuspend) continue

                createCompanionLookupElements(declaration).forEach { lookupElement ->
                    val wrappedElement = KotlinCompanionMemberLookupElement(companionObjectLookupElement, lookupElement)
                    if (!result.prefixMatcher.prefixMatches(wrappedElement)) return@forEach

                    if (parameters.completionType == CompletionType.SMART) {
                        val itemType = (lookupElement as? TypedLookupItem)?.type
                        if (itemType == null || expectedTypes.none { it.type.isAssignableFrom(itemType) }) {
                            return@forEach
                        }
                    }

                    foundMembers.addIfNotNull(lookupElement.psiElement)
                    result.addElement(wrappedElement)
                }
            }
        }

        // Because both regular Java completion and this contributor can produce elements contained within the companion object,
        // we need to ensure that we do not duplicate elements.
        result.runRemainingContributors(parameters) { completionResult ->
            val lookupElement = completionResult.lookupElement
            if (lookupElement.psiElement in foundMembers) return@runRemainingContributors
            result.passResult(completionResult)
        }
    }
}
