// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.core.ExpectedInfos
import org.jetbrains.kotlin.idea.core.SmartCastCalculator
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.idea.util.toFuzzyType
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext

class SuitableVariableMacro : BaseKotlinVariableMacro<SuitableVariableMacro.State?>() {
    class State(val expectedInfos: Collection<ExpectedInfo>, val smartCastCalculator: SmartCastCalculator)

    override fun getName() = "kotlinVariable"
    override fun getPresentableName() = "kotlinVariable()"

    override fun initState(contextElement: KtElement, bindingContext: BindingContext): State? {
        val resolutionFacade = contextElement.getResolutionFacade()
        if (contextElement is KtNameReferenceExpression) {
            val callTypeAndReceiver = CallTypeAndReceiver.detect(contextElement)
            if (callTypeAndReceiver is CallTypeAndReceiver.DEFAULT) {
                val expectedInfos = ExpectedInfos(bindingContext, resolutionFacade, null).calculate(contextElement)
                if (expectedInfos.isNotEmpty()) {
                    val scope = contextElement.getResolutionScope(bindingContext, resolutionFacade)
                    val smartCastCalculator =
                        SmartCastCalculator(bindingContext, scope.ownerDescriptor, contextElement, null, resolutionFacade)
                    return State(expectedInfos, smartCastCalculator)
                }
            }
        }

        return null
    }

    override fun isSuitable(variableDescriptor: VariableDescriptor, project: Project, state: State?): Boolean {
        if (state == null) return true
        val types = state.smartCastCalculator.types(variableDescriptor)
        return state.expectedInfos
            .any { expectedInfo -> types.any { expectedInfo.filter.matchingSubstitutor(it.toFuzzyType(emptyList())) != null } }
    }
}
