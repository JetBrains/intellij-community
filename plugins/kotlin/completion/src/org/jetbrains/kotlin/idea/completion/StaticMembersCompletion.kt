// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.codeInsight.collectSyntheticStaticMembersAndConstructors
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.resolve.ImportedFromObjectCallableDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.KotlinType
import java.util.*

class StaticMembersCompletion(
    private val prefixMatcher: PrefixMatcher,
    private val resolutionFacade: ResolutionFacade,
    private val lookupElementFactory: LookupElementFactory,
    alreadyAdded: Collection<DeclarationDescriptor>,
    private val isJvmModule: Boolean
) {
    private val alreadyAdded = alreadyAdded.mapTo(HashSet()) {
        if (it is ImportedFromObjectCallableDescriptor<*>) it.callableFromObject else it
    }

    fun decoratedLookupElementFactory(itemPriority: ItemPriority): AbstractLookupElementFactory = object : AbstractLookupElementFactory {
        override fun createStandardLookupElementsForDescriptor(
            descriptor: DeclarationDescriptor,
            useReceiverTypes: Boolean
        ): Collection<LookupElement> {
            if (!useReceiverTypes) return emptyList()
            return lookupElementFactory.createLookupElement(descriptor, useReceiverTypes = false)
                .decorateAsStaticMember(descriptor, classNameAsLookupString = false)
                ?.assignPriority(itemPriority)
                ?.suppressAutoInsertion()
                .let(::listOfNotNull)
        }

        override fun createLookupElement(
            descriptor: DeclarationDescriptor, useReceiverTypes: Boolean,
            qualifyNestedClasses: Boolean, includeClassTypeArguments: Boolean,
            parametersAndTypeGrayed: Boolean
        ): LookupElement? = null
    }

    fun membersFromImports(file: KtFile): Collection<DeclarationDescriptor> {
        val containers = file.importDirectives.filter { !it.isAllUnder }.mapNotNull {
            it.targetDescriptors(resolutionFacade).map { descriptor ->
                descriptor.containingDeclaration
            }.distinct().singleOrNull() as? ClassDescriptor
        }.toSet()

        val result = ArrayList<DeclarationDescriptor>()

        val kindFilter = DescriptorKindFilter.CALLABLES exclude DescriptorKindExclude.Extensions
        val nameFilter = prefixMatcher.asNameFilter()
        for (container in containers) {
            val memberScope = if (container.kind == ClassKind.OBJECT) container.unsubstitutedMemberScope else container.staticScope
            val members =
                memberScope.getDescriptorsFiltered(kindFilter, nameFilter) + memberScope.collectSyntheticStaticMembersAndConstructors(
                    resolutionFacade,
                    kindFilter,
                    nameFilter
                )
            members.filterTo(result) { it is CallableDescriptor && it !in alreadyAdded }
        }
        return result
    }

    //TODO: filter out those that are accessible from SmartCompletion.additionalItems
    //TODO: what about enum members?
    //TODO: better presentation for lookup elements from imports too
    //TODO: from the same file

    fun processMembersFromIndices(indicesHelper: KotlinIndicesHelper, processor: (DeclarationDescriptor) -> Unit) {
        val descriptorKindFilter = DescriptorKindFilter.CALLABLES exclude DescriptorKindExclude.Extensions
        val nameFilter: (String) -> Boolean = { prefixMatcher.prefixMatches(it) }

        val filter = { _: KtNamedDeclaration, _: KtObjectDeclaration -> true }

        indicesHelper.processObjectMembers(descriptorKindFilter, nameFilter, filter) {
            if (it !in alreadyAdded) {
                processor(it)
            }
        }

        if (isJvmModule) {
            indicesHelper.processJavaStaticMembers(descriptorKindFilter, nameFilter) {
                if (it !in alreadyAdded) {
                    processor(it)
                }
            }
        }
    }

    fun completeFromImports(file: KtFile, collector: LookupElementsCollector) {
        val factory = decoratedLookupElementFactory(ItemPriority.STATIC_MEMBER_FROM_IMPORTS)
        membersFromImports(file).forEach { descriptor ->
            factory.createStandardLookupElementsForDescriptor(descriptor, useReceiverTypes = true).forEach(collector::addElement)
            collector.flushToResultSet()
        }
    }

    /**
     * Collects extensions declared as members in objects into [collector], for example:
     *
     * ```
     * object Obj {
     *     fun String.foo() {}
     * }
     * ```
     *
     * `foo` here is object member extension.
     */
    fun completeObjectMemberExtensionsFromIndices(
        indicesHelper: KotlinIndicesHelper,
        receiverTypes: Collection<KotlinType>,
        callTypeAndReceiver: CallTypeAndReceiver<*, CallType<out KtElement?>>,
        collector: LookupElementsCollector
    ) {
        val factory = decoratedLookupElementFactory(ItemPriority.STATIC_MEMBER)

        indicesHelper.processCallableExtensionsDeclaredInObjects(
            callTypeAndReceiver,
            receiverTypes,
            nameFilter = { prefixMatcher.prefixMatches(it) },
            processor = { descriptor ->
                if (descriptor !in alreadyAdded) {
                    factory.createStandardLookupElementsForDescriptor(descriptor, useReceiverTypes = true).forEach(collector::addElement)
                    collector.flushToResultSet()
                }
            }
        )
    }

    /**
     * Find all extension methods and properties declared in objects or inherited by objects
     * from their superclasses, and add them to the collector.
     *
     * @param indicesHelper an instance of indices helper to look up for candidate objects
     * @param receiverTypes the receiver types at the completion site
     * @param callTypeAndReceiver the type of call
     * @param collector a collector for candidates
     */
    fun completeExplicitAndInheritedMemberExtensionsFromIndices(
        indicesHelper: KotlinIndicesHelper,
        receiverTypes: Collection<KotlinType>,
        callTypeAndReceiver: CallTypeAndReceiver<*, *>,
        collector: LookupElementsCollector
    ) {
        val factory = decoratedLookupElementFactory(ItemPriority.STATIC_MEMBER)

        indicesHelper.processAllCallablesFromSubclassObjects(
            callTypeAndReceiver,
            receiverTypes,
            nameFilter = { prefixMatcher.prefixMatches(it) },
            processor = { callableDescriptor ->
                if (callableDescriptor.isExtension && callableDescriptor !in alreadyAdded) {
                    factory.createStandardLookupElementsForDescriptor(callableDescriptor, useReceiverTypes = true).forEach(collector::addElement)
                    collector.flushToResultSet()
                }
            }
        )
    }

    fun completeFromIndices(indicesHelper: KotlinIndicesHelper, collector: LookupElementsCollector) {
        val factory = decoratedLookupElementFactory(ItemPriority.STATIC_MEMBER)
        processMembersFromIndices(indicesHelper) { descriptor ->
            factory.createStandardLookupElementsForDescriptor(descriptor, useReceiverTypes = true).forEach(collector::addElement)
            collector.flushToResultSet()
        }
    }
}
