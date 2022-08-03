// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates.macro

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext

class AnyVariableMacro : BaseKotlinVariableMacro<Unit>() {
    override fun getName() = "kotlinAnyVariable"
    override fun getPresentableName() = "kotlinAnyVariable()"

    override fun initState(contextElement: KtElement, bindingContext: BindingContext) {
    }

    override fun isSuitable(variableDescriptor: VariableDescriptor, project: Project, state: Unit) = true
}
