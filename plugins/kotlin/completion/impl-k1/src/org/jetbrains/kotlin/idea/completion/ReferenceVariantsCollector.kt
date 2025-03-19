// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.util.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.core.util.externalDescriptors
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.ShadowedDeclarationsFilter
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DataClassResolver
import org.jetbrains.kotlin.resolve.DescriptorUtils.isEnumClass
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.isTypeVariableType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.util.capitalizeDecapitalize.decapitalizeSmartForCompiler

data class ReferenceVariants(val imported: Collection<DeclarationDescriptor>, val notImportedExtensions: Collection<CallableDescriptor>)

private operator fun ReferenceVariants.plus(other: ReferenceVariants): ReferenceVariants {
    return ReferenceVariants(imported.union(other.imported), notImportedExtensions.union(other.notImportedExtensions))
}

class ReferenceVariantsCollector(
    private val referenceVariantsHelper: ReferenceVariantsHelper,
    private val indicesHelper: KotlinIndicesHelper,
    private val prefixMatcher: PrefixMatcher,
    private val applicabilityFilter: (DeclarationDescriptor) -> Boolean,
    private val nameExpression: KtSimpleNameExpression,
    private val callTypeAndReceiver: CallTypeAndReceiver<*, *>,
    private val resolutionFacade: ResolutionFacade,
    private val bindingContext: BindingContext,
    private val importableFqNameClassifier: ImportableFqNameClassifier,
    private val configuration: CompletionSessionConfiguration,
    private val allowExpectedDeclarations: Boolean,
    private val runtimeReceiver: ExpressionReceiver? = null,
) {

    companion object {
        private fun KotlinType.contains(isThatType: KotlinType.() -> Boolean): Boolean {
            if (isThatType()) return true
            if (arguments.isEmpty() || arguments.firstOrNull()?.isStarProjection == true) return false
            for (arg in arguments) {
                if (arg.type.contains { isThatType() }) return true
            }
            return false
        }

        private fun DeclarationDescriptor.hasSubstitutionFailure(): Boolean {
            val callable = this as? CallableDescriptor ?: return false
            return callable.valueParameters.any {
                it.type.contains { isNothing() } && !it.original.type.contains { isNothing() }
                        || it.type.contains { isTypeVariableType() }
                        || it.type.contains { isError }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T: DeclarationDescriptor> T.fixSubstitutionFailureIfAny(): T =
            if (hasSubstitutionFailure()) original as T else this

        private fun ReferenceVariants.fixDescriptors(): ReferenceVariants {
            val importedFixed = imported.map { it.fixSubstitutionFailureIfAny() }
            val notImportedFixed = notImportedExtensions.map { it.fixSubstitutionFailureIfAny() }
            return ReferenceVariants(importedFixed, notImportedFixed)
        }
    }

    private data class FilterConfiguration internal constructor(
        val descriptorKindFilter: DescriptorKindFilter,
        val additionalPropertyNameFilter: ((String) -> Boolean)?,
        val shadowedDeclarationsFilter: ShadowedDeclarationsFilter?,
        val completeExtensionsFromIndices: Boolean
    )

    private val prefix = prefixMatcher.prefix
    private val descriptorNameFilter = prefixMatcher.asStringNameFilter()

    private val collectedImported = LinkedHashSet<DeclarationDescriptor>()
    private val collectedNotImportedExtensions = LinkedHashSet<CallableDescriptor>()
    private var isCollectingFinished = false

    val allCollected: ReferenceVariants
        get() {
            assert(isCollectingFinished)
            return ReferenceVariants(collectedImported, collectedNotImportedExtensions)
        }

    fun collectingFinished() {
        assert(!isCollectingFinished)
        isCollectingFinished = true
    }

    fun collectReferenceVariants(descriptorKindFilter: DescriptorKindFilter): ReferenceVariants {
        assert(!isCollectingFinished)
        val config = configuration(descriptorKindFilter)

        val basic = collectBasicVariants(config)
        return basic + collectExtensionVariants(config, basic)
    }

    fun collectReferenceVariants(descriptorKindFilter: DescriptorKindFilter, consumer: (ReferenceVariants) -> Unit) {
        assert(!isCollectingFinished)
        val config = configuration(descriptorKindFilter)

        val basic = collectBasicVariants(config).fixDescriptors()
        consumer(basic)
        val extensions = collectExtensionVariants(config, basic).fixDescriptors()
        consumer(extensions)
    }

    data class ReferenceVariantsCollectors(
        val basic: Lazy<ReferenceVariants>,
        val extensions: Lazy<ReferenceVariants>
    )

    fun makeReferenceVariantsCollectors(descriptorKindFilter: DescriptorKindFilter): ReferenceVariantsCollectors {
        val config = configuration(descriptorKindFilter)

        val basic = lazy {
            assert(!isCollectingFinished)
            collectBasicVariants(config).fixDescriptors()
        }

        val extensions = lazy {
            assert(!isCollectingFinished)
            collectExtensionVariants(config, basic.value).fixDescriptors()
        }

        return ReferenceVariantsCollectors(basic, extensions)
    }

    private fun collectBasicVariants(filterConfiguration: FilterConfiguration): ReferenceVariants {
        val variants = doCollectBasicVariants(filterConfiguration)
        collectedImported += variants.imported
        return variants
    }

    private fun collectExtensionVariants(filterConfiguration: FilterConfiguration, basicVariants: ReferenceVariants): ReferenceVariants {
        val variants = doCollectExtensionVariants(filterConfiguration, basicVariants)
        collectedImported += variants.imported
        collectedNotImportedExtensions += variants.notImportedExtensions
        return variants
    }

    private fun configuration(descriptorKindFilter: DescriptorKindFilter): FilterConfiguration {
        val completeExtensionsFromIndices = descriptorKindFilter.kindMask.and(DescriptorKindFilter.CALLABLES_MASK) != 0
                && DescriptorKindExclude.Extensions !in descriptorKindFilter.excludes
                && callTypeAndReceiver !is CallTypeAndReceiver.IMPORT_DIRECTIVE

        @Suppress("NAME_SHADOWING")
        val descriptorKindFilter = if (completeExtensionsFromIndices)
            descriptorKindFilter exclude TopLevelExtensionsExclude // handled via indices
        else
            descriptorKindFilter

        val getOrSetPrefix = GET_SET_PREFIXES.firstOrNull { prefix.startsWith(it) }
        val additionalPropertyNameFilter: ((String) -> Boolean)? = getOrSetPrefix?.let {
            prefixMatcher.cloneWithPrefix(prefix.removePrefix(getOrSetPrefix).decapitalizeSmartForCompiler()).asStringNameFilter()
        }

        val shadowedDeclarationsFilter = if (runtimeReceiver != null)
            ShadowedDeclarationsFilter(bindingContext, resolutionFacade, nameExpression, runtimeReceiver)
        else
            ShadowedDeclarationsFilter.create(bindingContext, resolutionFacade, nameExpression, callTypeAndReceiver)

        return FilterConfiguration(
            descriptorKindFilter,
            additionalPropertyNameFilter,
            shadowedDeclarationsFilter,
            completeExtensionsFromIndices
        )
    }

    private fun doCollectBasicVariants(filterConfiguration: FilterConfiguration): ReferenceVariants {
        fun getReferenceVariants(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): Collection<DeclarationDescriptor> {
            return referenceVariantsHelper.getReferenceVariants(
                nameExpression,
                kindFilter,
                nameFilter,
                filterOutJavaGettersAndSetters = false,
                filterOutShadowed = false,
                excludeNonInitializedVariable = false,
                useReceiverType = runtimeReceiver?.type
            )
        }

        val basicNameFilter = descriptorNameFilter.toNameFilter()
        val (descriptorKindFilter, additionalPropertyNameFilter) = filterConfiguration

        var runDistinct = false

        var basicVariants = getReferenceVariants(descriptorKindFilter, basicNameFilter)
        if (additionalPropertyNameFilter != null) {
            basicVariants += getReferenceVariants(
                descriptorKindFilter.intersect(DescriptorKindFilter.VARIABLES),
                additionalPropertyNameFilter.toNameFilter()
            )
            runDistinct = true
        }

        val containingCodeFragment = nameExpression.containingKtFile as? KtCodeFragment
        if (containingCodeFragment != null) {
            val externalDescriptors = containingCodeFragment.externalDescriptors
            if (externalDescriptors != null) {
                basicVariants += externalDescriptors
                    .filter { descriptorKindFilter.accepts(it) && basicNameFilter(it.name) }
            }
        }

        if (runDistinct) {
            basicVariants = basicVariants.distinct()
        }

        basicVariants = basicVariants.filter { applicabilityFilter(it) }

        return ReferenceVariants(filterConfiguration.filterVariants(basicVariants).toHashSet(), emptyList())
    }

    private fun doCollectExtensionVariants(filterConfiguration: FilterConfiguration, basicVariants: ReferenceVariants): ReferenceVariants {
        val (_, additionalPropertyNameFilter, shadowedDeclarationsFilter, completeExtensionsFromIndices) = filterConfiguration

        if (completeExtensionsFromIndices) {
            val nameFilter = if (additionalPropertyNameFilter != null)
                descriptorNameFilter or additionalPropertyNameFilter
            else
                descriptorNameFilter
            val extensions = if (runtimeReceiver != null)
                indicesHelper.getCallableTopLevelExtensions(callTypeAndReceiver, listOf(runtimeReceiver.type), nameFilter)
            else
                indicesHelper.getCallableTopLevelExtensions(
                    callTypeAndReceiver, nameExpression, bindingContext, receiverTypeFromDiagnostic = null, nameFilter
                )

            val (extensionsVariants, notImportedExtensions) = extensions.filter { applicabilityFilter(it) }.partition {
                importableFqNameClassifier.isImportableDescriptorImported(
                    it
                )
            }

            val notImportedDeclarationsFilter = shadowedDeclarationsFilter?.createNonImportedDeclarationsFilter<CallableDescriptor>(
                importedDeclarations = basicVariants.imported + extensionsVariants,
                allowExpectedDeclarations = allowExpectedDeclarations,
            )

            val filteredImported = filterConfiguration.filterVariants(extensionsVariants + basicVariants.imported)

            val importedExtensionsVariants = filteredImported.filter { it !in basicVariants.imported }

            return ReferenceVariants(
                importedExtensionsVariants,
                notImportedExtensions.let { variants -> notImportedDeclarationsFilter?.invoke(variants) ?: variants }
            )
        }

        return ReferenceVariants(emptyList(), emptyList())
    }

    private fun <TDescriptor : DeclarationDescriptor> FilterConfiguration.filterVariants(_variants: Collection<TDescriptor>): Collection<TDescriptor> {
        var variants = _variants

        if (shadowedDeclarationsFilter != null)
            variants = shadowedDeclarationsFilter.filter(variants)

        if (!configuration.javaGettersAndSetters)
            variants = referenceVariantsHelper.filterOutJavaGettersAndSetters(variants)

        if (!configuration.dataClassComponentFunctions)
            variants = variants.filter { !isDataClassComponentFunction(it) }

        if (configuration.excludeEnumEntries) {
            variants = variants.filterNot(::isEnumEntriesProperty)
        }

        return variants
    }


    private val GET_SET_PREFIXES = listOf("get", "set", "ge", "se", "g", "s")

    private object TopLevelExtensionsExclude : DescriptorKindExclude() {
        override fun excludes(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor !is CallableMemberDescriptor) return false
            if (descriptor.extensionReceiverParameter == null) return false
            if (descriptor.kind != CallableMemberDescriptor.Kind.DECLARATION) return false /* do not filter out synthetic extensions */
            if (descriptor.isArtificialImportAliasedDescriptor) return false // do not exclude aliased descriptors - they cannot be completed via indices
            val containingPackage = descriptor.containingDeclaration as? PackageFragmentDescriptor ?: return false
            // TODO: temporary solution for Android synthetic extensions
            return !containingPackage.fqName.asString().startsWith("kotlinx.android.synthetic.")
        }

        override val fullyExcludedDescriptorKinds: Int get() = 0
    }

    private fun isDataClassComponentFunction(descriptor: DeclarationDescriptor): Boolean =
        descriptor is FunctionDescriptor && descriptor.isOperator &&
                DataClassResolver.isComponentLike(descriptor.name) && descriptor.kind == CallableMemberDescriptor.Kind.SYNTHESIZED

    private fun isEnumEntriesProperty(descriptor: DeclarationDescriptor): Boolean {
        return descriptor.name == StandardNames.ENUM_ENTRIES &&
                (descriptor as? CallableMemberDescriptor)?.kind == CallableMemberDescriptor.Kind.SYNTHESIZED &&
                isEnumClass(descriptor.containingDeclaration)
    }
}
