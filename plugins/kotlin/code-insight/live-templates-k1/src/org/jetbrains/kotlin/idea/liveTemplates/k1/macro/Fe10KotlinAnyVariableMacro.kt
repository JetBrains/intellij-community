// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.liveTemplates.k1.macro

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext

class Fe10KotlinAnyVariableMacro : Fe10AbstractKotlinVariableMacro<Unit>() {
    override fun getName() = "kotlinAnyVariable"
    override fun getPresentableName() = "kotlinAnyVariable()"

    override fun initState(contextElement: KtElement, bindingContext: BindingContext) {}

    override fun isSuitable(variableDescriptor: VariableDescriptor, project: Project, state: Unit) = true
}
