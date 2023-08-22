// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.jetbrains.kotlin.builtins.isBuiltinFunctionalTypeOrSubtype
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.FuzzyType
import org.jetbrains.kotlin.idea.util.fuzzyReturnType
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindExclude
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.TypeSubstitutor
import java.util.*

interface ContextVariablesProvider {
    fun functionTypeVariables(requiredType: FuzzyType): Collection<Pair<VariableDescriptor, TypeSubstitutor>>
}

class RealContextVariablesProvider(
    private val referenceVariantsHelper: ReferenceVariantsHelper,
    private val contextElement: PsiElement
) : ContextVariablesProvider {

    val allFunctionTypeVariables by lazy {
        collectVariables().filter { it.type.isBuiltinFunctionalTypeOrSubtype }
    }

    /*
    * The reason for using `nameFilter = MemberScope.ALL_NAME_FILTER` here is that we have
    * functionality that allows to complete arguments for a completing call like here:
    * class C {
    *   companion object {
    *     fun create(p: (Int) -> Unit) {}
    *   }
    * }
    *
    * val handler: (Int) -> Unit = {}
    *
    * val v: C = cr<caret>
    *
    * And here at <caret> it's possible to complete the full line: C.create(handler) !!!
    * */
    private fun collectVariables(): Collection<VariableDescriptor> {
        val descriptorFilter =
            DescriptorKindFilter.VARIABLES exclude DescriptorKindExclude.Extensions // we exclude extensions by performance reasons
        return referenceVariantsHelper.getReferenceVariants(
            contextElement,
            CallTypeAndReceiver.DEFAULT,
            descriptorFilter,
            nameFilter = MemberScope.ALL_NAME_FILTER).map { it as VariableDescriptor }
    }

    override fun functionTypeVariables(requiredType: FuzzyType): Collection<Pair<VariableDescriptor, TypeSubstitutor>> {
        val result = SmartList<Pair<VariableDescriptor, TypeSubstitutor>>()
        for (variable in allFunctionTypeVariables) {
            val substitutor = variable.fuzzyReturnType()?.checkIsSubtypeOf(requiredType) ?: continue
            result.add(variable to substitutor)
        }
        return result
    }
}

class CollectRequiredTypesContextVariablesProvider : ContextVariablesProvider {
    private val _requiredTypes = HashSet<FuzzyType>()

    val requiredTypes: Set<FuzzyType>
        get() = _requiredTypes

    override fun functionTypeVariables(requiredType: FuzzyType): Collection<Pair<VariableDescriptor, TypeSubstitutor>> {
        _requiredTypes.add(requiredType)
        return emptyList()
    }
}
