// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.introduceParameter

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.supertypes

class KotlinInplaceParameterIntroducer(
    originalDescriptor: IntroduceParameterDescriptor<FunctionDescriptor>,
    parameterType: KotlinType,
    suggestedNames: Array<out String>,
    project: Project,
    editor: Editor
) : KotlinInplaceParameterIntroducerBase<KotlinType, FunctionDescriptor>(
    originalDescriptor,
    parameterType,
    suggestedNames,
    project,
    editor
) {

    override fun performRefactoring(descriptor: IntroduceParameterDescriptor<FunctionDescriptor>) {
        descriptor.performRefactoring()
    }

    override fun switchToDialogUI() {
        stopIntroduce(myEditor)
        KotlinIntroduceParameterDialog(
            myProject,
            myEditor,
            getDescriptorToRefactor(true),
            myNameSuggestions.toTypedArray(),
            listOf(parameterType) + parameterType.supertypes(),
            KotlinIntroduceParameterHelper.Default<FunctionDescriptor>()
        ).show()
    }
}
