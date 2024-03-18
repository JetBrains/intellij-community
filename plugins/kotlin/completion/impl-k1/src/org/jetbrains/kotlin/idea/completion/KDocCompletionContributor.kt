// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.kdoc.getKDocLinkMemberScope
import org.jetbrains.kotlin.idea.kdoc.getKDocLinkResolutionScope
import org.jetbrains.kotlin.idea.kdoc.getParamDescriptors
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.utils.collectDescriptorsFiltered

class KDocCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, psiElement().inside(KDocName::class.java),
            KDocNameCompletionProvider
        )

        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(
                StandardPatterns.or(psiElement(KDocTokens.LEADING_ASTERISK), psiElement(KDocTokens.START))
            ),
            KDocTagCompletionProvider
        )

        extend(
            CompletionType.BASIC,
            psiElement(KDocTokens.TAG_NAME), KDocTagCompletionProvider
        )
    }
}

object KDocNameCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        KDocNameCompletionSession(parameters, result).complete()
    }
}

class KDocNameCompletionSession(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet
) : CompletionSession(CompletionSessionConfiguration(parameters), parameters, resultSet) {

    override val descriptorKindFilter: DescriptorKindFilter? get() = null
    override val expectedInfos: Collection<ExpectedInfo> get() = emptyList()

    override fun doComplete() {
        val position = parameters.position.getParentOfType<KDocName>(false) ?: return
        val declaration = position.getContainingDoc().getOwner() ?: return
        val kdocLink = position.getStrictParentOfType<KDocLink>()!!
        val declarationDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration] ?: return
        withCollectRequiredContextVariableTypes { lookupFactory ->
            if (kdocLink.getTagIfSubject()?.knownTag == KDocKnownTag.PARAM) {
                addParamCompletions(position, declarationDescriptor, lookupFactory)
            } else {
                addLinkCompletions(declarationDescriptor, kdocLink, lookupFactory)
            }
        }
    }


    private fun addParamCompletions(
        position: KDocName,
        declarationDescriptor: DeclarationDescriptor,
        lookupFactory: LookupElementFactory,
    ) {
        if (position.getQualifier() != null) return

        val section = position.getContainingSection()
        val documentedParameters = section.findTagsByName("param").map { it.getSubjectName() }.toSet()
        getParamDescriptors(declarationDescriptor)
            .filter { it.name.asString() !in documentedParameters }
            .forEach {
                collector.addElement(lookupFactory.createLookupElement(it, useReceiverTypes = false, parametersAndTypeGrayed = true))
            }
    }

    private fun collectDescriptorsForLinkCompletion(
        declarationDescriptor: DeclarationDescriptor,
        kDocLink: KDocLink
    ): Collection<DeclarationDescriptor> {
        val contextScope = getKDocLinkResolutionScope(resolutionFacade, declarationDescriptor)

        val qualifier = kDocLink.qualifier
        val nameFilter = descriptorNameFilter.toNameFilter()
        return if (qualifier.isNotEmpty()) {
            val parentDescriptors =
                resolveKDocLink(bindingContext, resolutionFacade, declarationDescriptor, kDocLink, kDocLink.getTagIfSubject(), qualifier)
            parentDescriptors.flatMap {
                val scope = getKDocLinkMemberScope(it, contextScope)
                scope.getContributedDescriptors(nameFilter = nameFilter)
            }
        } else {
            contextScope.collectDescriptorsFiltered(DescriptorKindFilter.ALL, nameFilter, changeNamesForAliased = true)
        }
    }

    private fun addLinkCompletions(declarationDescriptor: DeclarationDescriptor, kDocLink: KDocLink, lookupFactory: LookupElementFactory) {
        collectDescriptorsForLinkCompletion(declarationDescriptor, kDocLink).forEach {
            val element = lookupFactory.createLookupElement(it, useReceiverTypes = true, parametersAndTypeGrayed = true)
            collector.addElement(
                // insert only plain name here, no qualifier/parentheses/etc.
                LookupElementDecorator.withDelegateInsertHandler(element, EmptyDeclarativeInsertHandler)
            )
        }
    }
}
