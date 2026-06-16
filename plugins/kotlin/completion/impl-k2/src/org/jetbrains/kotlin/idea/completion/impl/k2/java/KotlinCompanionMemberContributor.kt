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
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceExpression
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * A contributor responsible for providing completion of companion object members in **Java** files.
 * In Java, the discoverability of Kotlin companion members is hindered by having to type `.Companion`
 * to access the companion object instance and its members.
 * This contributor provides a shortcut that will show elements of companion objects when just typing the
 * name of the Kotlin class, similar to how it works in Kotlin.
 * Upon completion, the qualifier for the companion object will be automatically added to the reference expression.
 */
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
    context(_: KaSession)
    private fun KaDeclarationSymbol.isMarkedAsStatic(): Boolean {
        return annotations.any { it.classId == StandardClassIds.Annotations.jvmStatic }
    }

    context(_: KaSession)
    private fun KaDeclarationSymbol.isJvmField(): Boolean {
        return annotations.any { it.classId == JvmStandardClassIds.Annotations.JvmField }
    }

    /**
     * Given the [declaration] returns a list of Java lookup elements for companion members.
     * In the case of properties, two such elements - for the getter and setter - might be returned.
     * In other cases at most one element is returned.
     */
    context(_: KaSession)
    private fun createCompanionLookupElements(declaration: KaDeclarationSymbol): List<LookupElement> {
        val lightDeclarations = when (declaration) {
            is KaPropertySymbol -> {
                val psi = declaration.psi as? KtProperty ?: return emptyList()
                val methods = LightClassUtil.getLightClassPropertyMethods(psi)
                val getter = methods.getter
                // Only show the setter if it is not declared as private or protected
                val setter = methods.setter?.takeIf {
                    !it.hasModifier(JvmModifier.PRIVATE) && !it.hasModifier(JvmModifier.PROTECTED)
                }

                listOfNotNull(getter, setter)
            }

            is KaFunctionSymbol -> {
                val psi = declaration.psi as? KtFunction ?: return emptyList()
                val method = LightClassUtil.getLightClassMethod(psi) ?: return emptyList()
                listOf(method)
            }

            else -> emptyList()
        }

        return lightDeclarations.map(::JavaMethodCallElement)
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        if (!Registry.`is`("kotlin.java.completion.companion.members", false)) return

        val parent = parameters.position.parent
        if (parent !is PsiReferenceExpression) return
        val qualifierExpression = parent.qualifierExpression as? PsiReferenceExpression ?: return
        val resolvedElement = qualifierExpression.resolve() ?: return
        val ktClass = (resolvedElement as? KtLightElement<*, *>)?.kotlinOrigin as? KtClassOrObject ?: return

        val companionObject = ktClass.companionObjects.firstOrNull() ?: return
        val companionObjectField = LightClassUtil.getLightFieldForCompanionObject(companionObject) ?: return
        val companionObjectLookupElement = VariableLookupItem(companionObjectField, true)

        val expectedTypes by lazy {
            JavaSmartCompletionContributor.getExpectedTypes(parameters)
        }

        val completedMembers: MutableSet<PsiElement> = mutableSetOf()

        analyze(companionObject) {
            val resolvedCompanion = companionObject.symbol
            val allMembers = resolvedCompanion.memberScope

            val originalFileKaModule = parameters.originalFile.getKaModule(parent.project, useSiteModule)

            for (declaration in allMembers.declarations) {
                val psi = declaration.psi as? KtDeclaration ?: continue
                if (!psi.isVisibleIgnoringProtected(originalFileKaModule)) continue

                // If the declaration is marked as static or JvmField, it will already appear without `.Companion`.
                if (declaration.isMarkedAsStatic() || declaration.isJvmField()) continue
                // Hide suspend functions because they cannot easily be called from Java
                if (declaration is KaNamedFunctionSymbol && declaration.isSuspend) continue

                for (lookupElement in createCompanionLookupElements(declaration)) {
                    val wrappedElement = KotlinCompanionMemberLookupElement(companionObjectLookupElement, lookupElement)
                    if (!result.prefixMatcher.prefixMatches(wrappedElement)) continue

                    if (parameters.completionType == CompletionType.SMART) {
                        val itemType = (lookupElement as? TypedLookupItem)?.type
                        if (itemType == null || expectedTypes.none { it.type.isAssignableFrom(itemType) }) {
                            continue
                        }
                    }

                    completedMembers.addIfNotNull(lookupElement.psiElement)
                    result.addElement(wrappedElement)
                }
            }
        }

        // Because both regular Java completion and this contributor can produce elements contained within the companion object,
        // we need to ensure that we do not duplicate elements.
        result.runRemainingContributors(parameters) { completionResult ->
            val lookupElement = completionResult.lookupElement
            if (lookupElement.psiElement in completedMembers) return@runRemainingContributors
            result.passResult(completionResult)
        }
    }
}


/**
 * Returns the [Visibility] directly declared on the declaration symbol.
 */
private fun KtDeclaration.getDirectVisibility(): Visibility = when {
    hasModifier(KtTokens.PRIVATE_KEYWORD) -> Visibilities.Private
    hasModifier(KtTokens.PROTECTED_KEYWORD) -> Visibilities.Protected
    hasModifier(KtTokens.INTERNAL_KEYWORD) -> Visibilities.Internal
    hasModifier(KtTokens.PUBLIC_KEYWORD) -> Visibilities.Public
    else -> Visibilities.DEFAULT_VISIBILITY
}

private val KtDeclaration.containingClassesWithSelf: Sequence<KtDeclaration>
    get() = generateSequence(this) { it.containingClassOrObject }

/**
 * Returns the effective visibility of the declaration symbol, taking into account any containing symbol's visibility.
 */
private fun KtDeclaration.getEffectiveVisibility(): Visibility {
    return containingClassesWithSelf.minOfWith(Comparator { a, b -> a.compareTo(b) ?: 0 }) {
        it.getDirectVisibility()
    }
}

/**
 * Checks if the given declaration is visible from the position of [fromModule].
 * The check assumes protected members are not visible for simplicity.
 * We cannot use [KaUseSiteVisibilityChecker] here because the position might be in a Java file.
 */
internal fun KtDeclaration.isVisibleIgnoringProtected(fromModule: KaModule): Boolean = when (getEffectiveVisibility()) {
    Visibilities.Public -> true
    Visibilities.Internal -> {
        val fileModule = containingKtFile.getKaModule(containingKtFile.project, fromModule)
        fileModule == fromModule || fileModule in fromModule.directFriendDependencies
    }

    else -> false
}
