// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinFindUsagesProviderBase
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.completion.KotlinIdeaCompletionBundle
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.types.typeUtil.isUnit

class KotlinFindUsagesProvider : KotlinFindUsagesProviderBase() {

    override fun getDescriptiveName(element: PsiElement): String {

        if (element !is KtFunction) return super.getDescriptiveName(element)

        val name = element.name ?: ""
        val descriptor = element.unsafeResolveToDescriptor() as FunctionDescriptor
        val renderer = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS
        val paramsDescription =
            descriptor.valueParameters.joinToString(prefix = "(", postfix = ")") { renderer.renderType(it.type) }
        val returnType = descriptor.returnType
        val returnTypeDescription = if (returnType != null && !returnType.isUnit()) renderer.renderType(returnType) else null
        val funDescription = "$name$paramsDescription" + (returnTypeDescription?.let { ": $it" } ?: "")
        return element.containerDescription?.let { KotlinIdeaCompletionBundle.message("find.usage.provider.0.of.1", funDescription, it) }
            ?: KotlinIdeaCompletionBundle.message("find.usage.provider.0", funDescription)
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String =
        getDescriptiveName(element)
}
