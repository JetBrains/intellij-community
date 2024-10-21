// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.CompletionWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.codeInsight.lookup.WeighingContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.proximity.PsiProximityComparator
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.implCommon.weighers.SoftDeprecationWeigher
import org.jetbrains.kotlin.idea.completion.smart.*
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.core.completion.DeclarationLookupObject
import org.jetbrains.kotlin.idea.core.completion.DescriptorBasedDeclarationLookupObject
import org.jetbrains.kotlin.idea.core.completion.PackageLookupObject
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.toFuzzyType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils.isEnumClass
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.findOriginalTopMostOverriddenDescriptors
import org.jetbrains.kotlin.types.typeUtil.isNothing

object PriorityWeigher : LookupElementWeigher("kotlin.priority") {
    override fun weigh(element: LookupElement, context: WeighingContext) = element.priority ?: ItemPriority.DEFAULT
}

object PreferDslMembers : LookupElementWeigher("kotlin.preferDsl") {
    override fun weigh(element: LookupElement, context: WeighingContext): Boolean {
        if (element.isDslMember == true) return false // high priority
        return true // lower priority
    }
}

class NotImportedWeigher(private val classifier: ImportableFqNameClassifier) : LookupElementWeigher("kotlin.notImported") {
    private enum class Weight {
        default,
        siblingImported,
        notImported,
        notToBeUsedInKotlin
    }

    override fun weigh(element: LookupElement): Comparable<*> {
        if (element.getUserData(NOT_IMPORTED_KEY) == null) return Weight.default
        val o = element.`object` as? DescriptorBasedDeclarationLookupObject
        val fqName = o?.importableFqName ?: return Weight.default
        return when (classifier.classify(fqName, o is PackageLookupObject)) {
            ImportableFqNameClassifier.Classification.siblingImported -> Weight.siblingImported
            ImportableFqNameClassifier.Classification.notImported -> Weight.notImported
            ImportableFqNameClassifier.Classification.notToBeUsedInKotlin -> Weight.notToBeUsedInKotlin
            else -> Weight.default
        }
    }
}

class NotImportedStaticMemberWeigher(private val classifier: ImportableFqNameClassifier) :
    LookupElementWeigher("kotlin.notImportedMember") {
    override fun weigh(element: LookupElement): Comparable<*>? {
        if (element.priority != ItemPriority.STATIC_MEMBER) return null
        val fqName = (element.`object` as DescriptorBasedDeclarationLookupObject).importableFqName ?: return null
        return classifier.classify(fqName.parent(), false)
    }
}

class ImportedWeigher(private val classifier: ImportableFqNameClassifier) : LookupElementWeigher("kotlin.imported") {
    private enum class Weight {
        currentPackage,
        preciseImport,
        defaultImport,
        allUnderImport
    }

    override fun weigh(element: LookupElement): Comparable<*>? {
        val o = element.`object` as? DescriptorBasedDeclarationLookupObject
        val fqName = o?.importableFqName ?: return null
        return when (classifier.classify(fqName, o is PackageLookupObject)) {
            ImportableFqNameClassifier.Classification.fromCurrentPackage -> Weight.currentPackage
            ImportableFqNameClassifier.Classification.defaultImport -> Weight.defaultImport
            ImportableFqNameClassifier.Classification.preciseImport -> Weight.preciseImport
            ImportableFqNameClassifier.Classification.allUnderImport -> Weight.allUnderImport
            else -> null
        }
    }
}

// analog of LookupElementProximityWeigher which does not work for us
internal class KotlinLookupElementProximityWeigher : CompletionWeigher() {
    override fun weigh(element: LookupElement, location: CompletionLocation): Comparable<Nothing>? {
        val psiElement = (element.`object` as? DescriptorBasedDeclarationLookupObject)?.psiElement ?: return null
        return PsiProximityComparator.getProximity({ psiElement }, location.completionParameters.position, location.processingContext)
    }
}

object SmartCompletionPriorityWeigher : LookupElementWeigher("kotlin.smartCompletionPriority") {
    override fun weigh(element: LookupElement, context: WeighingContext) =
        element.getUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY) ?: SmartCompletionItemPriority.DEFAULT
}

object KindWeigher : LookupElementWeigher("kotlin.kind") {
    private enum class Weight {
        probableKeyword,
        enumMember,
        callable,
        keyword,
        default,

        /**
         * This does not mean that the keyword cannot be used at all; it just means that
         * it is highly unlikely that it will be used.
         */
        notProbableKeyword,
        packages
    }

    override fun weigh(element: LookupElement): Comparable<*> {
        return when (val o = element.`object`) {
            is PackageLookupObject -> Weight.packages

            is DescriptorBasedDeclarationLookupObject -> {
                when (val descriptor = o.descriptor) {
                    is VariableDescriptor, is FunctionDescriptor -> Weight.callable
                    is ClassDescriptor -> if (descriptor.kind == ClassKind.ENUM_ENTRY) Weight.enumMember else Weight.default
                    else -> Weight.default
                }
            }

            is KeywordLookupObject -> when (element.keywordProbability) {
                KeywordProbability.HIGH -> Weight.probableKeyword
                KeywordProbability.DEFAULT -> Weight.keyword
                KeywordProbability.LOW -> Weight.notProbableKeyword
            }

            else -> Weight.default
        }
    }
}

object CallableWeigher : LookupElementWeigher("kotlin.callableWeight") {
    private enum class Weight1 {
        local,
        memberOrExtension,
        typeParameterExtension,
        globalOrStatic,
        receiverCastRequired
    }

    private enum class Weight2 {
        thisClassMember,
        baseClassMember,
        thisTypeExtension,
        baseTypeExtension,
        other
    }

    private data class CompoundWeight(val weight1: Weight1, val receiverIndex: Int, val weight2: Weight2) : Comparable<CompoundWeight> {
        override fun compareTo(other: CompoundWeight): Int {
            if (weight1 != other.weight1) return weight1.compareTo(other.weight1)
            if (receiverIndex != other.receiverIndex) return receiverIndex.compareTo(other.receiverIndex)
            return weight2.compareTo(other.weight2)
        }
    }

    override fun weigh(element: LookupElement): Comparable<*>? {
        val weight = element.getUserData(CALLABLE_WEIGHT_KEY) ?: return null
        val w1 = when (weight.enum) {
            CallableWeightEnum.local -> Weight1.local

            CallableWeightEnum.thisClassMember,
            CallableWeightEnum.baseClassMember,
            CallableWeightEnum.thisTypeExtension,
            CallableWeightEnum.baseTypeExtension -> Weight1.memberOrExtension

            CallableWeightEnum.globalOrStatic -> Weight1.globalOrStatic

            CallableWeightEnum.typeParameterExtension -> Weight1.typeParameterExtension

            CallableWeightEnum.receiverCastRequired -> Weight1.receiverCastRequired
        }
        val w2 = when (weight.enum) {
            CallableWeightEnum.thisClassMember -> Weight2.thisClassMember
            CallableWeightEnum.baseClassMember -> Weight2.baseClassMember
            CallableWeightEnum.thisTypeExtension -> Weight2.thisTypeExtension
            CallableWeightEnum.baseTypeExtension -> Weight2.baseTypeExtension
            else -> Weight2.other
        }
        return CompoundWeight(w1, weight.receiverIndex ?: Int.MAX_VALUE, w2)
    }
}

object VariableOrFunctionWeigher : LookupElementWeigher("kotlin.variableOrFunction") {
    private enum class Weight {
        variable,
        function
    }

    override fun weigh(element: LookupElement): Comparable<*>? {
        val descriptor = (element.`object` as? DescriptorBasedDeclarationLookupObject)?.descriptor ?: return null
        return when (descriptor) {
            is VariableDescriptor -> Weight.variable
            is FunctionDescriptor -> Weight.function
            else -> null
        }
    }
}

/**
 * Decreases priority of properties when prefix starts with "get" or "set" (and the property name does not)
 */
object PreferGetSetMethodsToPropertyWeigher : LookupElementWeigher("kotlin.preferGetSetMethodsToProperty", false, true) {
    override fun weigh(element: LookupElement, context: WeighingContext): Int {
        val property = (element.`object` as? DescriptorBasedDeclarationLookupObject)?.descriptor as? PropertyDescriptor ?: return 0
        val prefixMatcher = context.itemMatcher(element)
        if (prefixMatcher.prefixMatches(property.name.asString())) return 0
        val matchedLookupStrings = element.allLookupStrings.filter { prefixMatcher.prefixMatches(it) }
        return if (matchedLookupStrings.all { it.startsWith("get") || it.startsWith("set") }) 1 else 0
    }
}

object DeprecatedWeigher : LookupElementWeigher("kotlin.deprecated") {
    override fun weigh(element: LookupElement): Int {
        val o = element.`object` as? DescriptorBasedDeclarationLookupObject ?: return 0
        return if (o.isDeprecated) 1 else 0
    }
}

object K1SoftDeprecationWeigher : LookupElementWeigher(SoftDeprecationWeigher.WEIGHER_ID) {
    override fun weigh(element: LookupElement): Boolean {
        val declarationLookupObject = element.`object` as? DescriptorBasedDeclarationLookupObject ?: return false
        val descriptor = declarationLookupObject.descriptor ?: return false
        val languageVersionSettings = declarationLookupObject.psiElement?.languageVersionSettings ?: return false
        return SoftDeprecationWeigher.isSoftDeprecatedFqName(descriptor.fqNameSafe, languageVersionSettings)
                || isEnumValuesSoftDeprecatedMethod(declarationLookupObject, descriptor, languageVersionSettings)
    }

    /**
     * Lower soft-deprecated `Enum.values()` method in completion.
     * See [KT-22298](https://youtrack.jetbrains.com/issue/KTIJ-22298/Soft-deprecate-Enumvalues-for-Kotlin-callers).
     */
    private fun isEnumValuesSoftDeprecatedMethod(
        declarationLookupObject: DescriptorBasedDeclarationLookupObject,
        descriptor: DeclarationDescriptor,
        languageVersionSettings: LanguageVersionSettings
    ): Boolean {
        return languageVersionSettings.isEnumValuesSoftDeprecateEnabled() &&
                isEnumClass(descriptor.containingDeclaration) &&
                StandardNames.ENUM_VALUES == declarationLookupObject.name &&
                // Don't touch user-declared methods with the name "values"
                (descriptor as? CallableMemberDescriptor)?.kind == CallableMemberDescriptor.Kind.SYNTHESIZED
    }
}

object PreferMatchingItemWeigher : LookupElementWeigher("kotlin.preferMatching", false, true) {
    private enum class Weight {
        keywordExactMatch,
        defaultExactMatch,
        functionExactMatch,
        notImportedExactMatch,
        specialExactMatch,
        notExactMatch
    }

    override fun weigh(element: LookupElement, context: WeighingContext): Comparable<*> {
        val prefix = context.itemPattern(element)
        if (element.lookupString != prefix) {
            return Weight.notExactMatch
        } else {
            return when (val o = element.`object`) {
                is KeywordLookupObject -> Weight.keywordExactMatch

                is DescriptorBasedDeclarationLookupObject -> {
                    val smartCompletionPriority = element.getUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY)
                    when {
                        smartCompletionPriority != null && smartCompletionPriority != SmartCompletionItemPriority.DEFAULT -> Weight.specialExactMatch
                        element.getUserData(NOT_IMPORTED_KEY) != null -> Weight.notImportedExactMatch
                        o.descriptor is FunctionDescriptor -> Weight.functionExactMatch
                        else -> Weight.defaultExactMatch
                    }
                }

                else -> Weight.defaultExactMatch
            }
        }
    }
}

class SmartCompletionInBasicWeigher(
    private val smartCompletion: SmartCompletion,
    private val callTypeAndReceiver: CallTypeAndReceiver<*, *>,
    private val resolutionFacade: ResolutionFacade,
    private val bindingContext: BindingContext
) : LookupElementWeigher("kotlin.smartInBasic", true, false) {

    companion object {
        val KEYWORD_VALUE_MATCHED_KEY = Key<Unit>("SmartCompletionInBasicWeigher.KEYWORD_VALUE_MATCHED_KEY")
        val NAMED_ARGUMENT_KEY = Key<Unit>("SmartCompletionInBasicWeigher.NAMED_ARGUMENT_KEY")
    }

    private val descriptorsToSkip = smartCompletion.descriptorsToSkip
    private val expectedInfos = smartCompletion.expectedInfos

    private val PRIORITY_COUNT = SmartCompletionItemPriority.values().size

    private fun itemWeight(priority: SmartCompletionItemPriority, nameSimilarity: Int) =
        (nameSimilarity.toLong() shl 32) + PRIORITY_COUNT - priority.ordinal

    private val NAMED_ARGUMENT_WEIGHT = 1L

    private val NO_MATCH_WEIGHT = 0L

    private val DESCRIPTOR_TO_SKIP_WEIGHT = -1L // if descriptor is skipped from smart completion then it's probably irrelevant

    override fun weigh(element: LookupElement): Long {
        if (element.getUserData(NAMED_ARGUMENT_KEY) != null) {
            return NAMED_ARGUMENT_WEIGHT
        }

        val priority = element.getUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY)
        if (priority != null) { // it's an "additional item" came from smart completion, don't match it against expected type
            return itemWeight(priority, element.getUserData(NAME_SIMILARITY_KEY) ?: 0)
        }

        val o = element.`object`

        if ((o as? DescriptorBasedDeclarationLookupObject)?.descriptor in descriptorsToSkip) return DESCRIPTOR_TO_SKIP_WEIGHT

        if (expectedInfos.isEmpty()) return NO_MATCH_WEIGHT

        val smartCastCalculator = smartCompletion.smartCastCalculator

        val (fuzzyTypes, name) = when (o) {
            is DescriptorBasedDeclarationLookupObject -> {
                val descriptor = o.descriptor ?: return NO_MATCH_WEIGHT
                descriptor.fuzzyTypesForSmartCompletion(
                    smartCastCalculator,
                    callTypeAndReceiver,
                    resolutionFacade,
                    bindingContext
                ) to descriptor.name
            }

            is ThisItemLookupObject -> smartCastCalculator.types(o.receiverParameter).map { it.toFuzzyType(emptyList()) } to null

            else -> return NO_MATCH_WEIGHT
        }

        if (fuzzyTypes.isEmpty()) return NO_MATCH_WEIGHT

        val matched: Collection<Pair<ExpectedInfo, ExpectedInfoMatch>> = expectedInfos.map { it to fuzzyTypes.matchExpectedInfo(it) }
        if (matched.all { it.second == ExpectedInfoMatch.noMatch }) return NO_MATCH_WEIGHT

        val nameSimilarity = if (name != null) {
            val matchingInfos = matched.filter { it.second != ExpectedInfoMatch.noMatch }.map { it.first }
            calcNameSimilarity(name.asString(), matchingInfos)
        } else {
            0
        }

        return if (matched.any { it.second.isMatch() })
            itemWeight(SmartCompletionItemPriority.DEFAULT, nameSimilarity)
        else
            itemWeight(SmartCompletionItemPriority.NULLABLE, nameSimilarity)
    }
}

class PreferContextElementsWeigher(context: DeclarationDescriptor) : LookupElementWeigher("kotlin.preferContextElements", true, false) {
    private val contextElements = context.parentsWithSelf
        .takeWhile { it !is PackageFragmentDescriptor }
        .toList()
        .flatMap { if (it is CallableDescriptor) it.findOriginalTopMostOverriddenDescriptors() else listOf(it) }
        .toSet()
    private val contextElementNames = contextElements.map { it.name }.toSet()

    override fun weigh(element: LookupElement): Boolean {
        val lookupObject = element.`object` as? DescriptorBasedDeclarationLookupObject ?: return false
        val descriptor = lookupObject.descriptor ?: return false
        return descriptor.isContextElement()
    }

    private fun DeclarationDescriptor.isContextElement(): Boolean {
        if (name !in contextElementNames) return false // optimization

        if (this is CallableMemberDescriptor) {
            val overridden = this.overriddenDescriptors
            if (overridden.isNotEmpty()) {
                return overridden.any { it.isContextElement() }
            }
        }

        return original in contextElements
    }
}

object ByNameAlphabeticalWeigher : LookupElementWeigher("kotlin.byNameAlphabetical") {
    override fun weigh(element: LookupElement): String? {
        val lookupObject = element.`object` as? DeclarationLookupObject ?: return null
        return lookupObject.name?.asString()
    }
}

object PreferLessParametersWeigher : LookupElementWeigher("kotlin.preferLessParameters") {
    override fun weigh(element: LookupElement): Int? {
        val lookupObject = element.`object` as? DescriptorBasedDeclarationLookupObject ?: return null
        val function = lookupObject.descriptor as? FunctionDescriptor ?: return null
        val valueParameters = function.valueParameters
        val size = valueParameters.size
        return if (
          valueParameters.singleOrNull()?.isVararg == true &&
          Registry.`is`("kotlin.auto.completion.prefer.vararg.to.noargs")
        ) {
            -1
        } else {
            size
        }
    }
}

class CallableReferenceWeigher(private val callType: CallType<*>) : LookupElementWeigher("kotlin.callableReference") {
    override fun weigh(element: LookupElement): Int? {
        if (callType is CallType.CallableReference || element.getUserData(SMART_COMPLETION_ITEM_PRIORITY_KEY) == SmartCompletionItemPriority.CALLABLE_REFERENCE) {
            val descriptor = (element.`object` as? DescriptorBasedDeclarationLookupObject)?.descriptor as? CallableDescriptor
            return if (descriptor?.returnType?.isNothing() == true) 1 else 0
        }
        return null
    }
}
