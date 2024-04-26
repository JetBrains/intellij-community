// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.idea.refactoring.changeSignature.usages.KotlinCallableDefinitionUsage
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtCallableDeclaration

interface KotlinMethodDescriptor : KotlinModifiableMethodDescriptor<KotlinParameterInfo, DescriptorVisibility> {
    override val original: KotlinMethodDescriptor

    val baseDescriptor: CallableDescriptor

    val originalPrimaryCallable: KotlinCallableDefinitionUsage<PsiElement>
    val primaryCallables: Collection<KotlinCallableDefinitionUsage<PsiElement>>
    val affectedCallables: Collection<UsageInfo>

    override var receiver: KotlinParameterInfo?
}

val KotlinMethodDescriptor.returnTypeInfo: KotlinTypeInfo
    get() {
        val type = baseDescriptor.returnType
        val text = (baseDeclaration as? KtCallableDeclaration)?.typeReference?.text
            ?: type?.let { IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(type) }
            ?: "Unit"

        return KotlinTypeInfo(true, type, text)
    }

val KotlinMethodDescriptor.receiverTypeInfo: KotlinTypeInfo
    get() {
        val type = baseDescriptor.extensionReceiverParameter?.type
        val text = (baseDeclaration as? KtCallableDeclaration)?.receiverTypeReference?.text
            ?: type?.let { IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(type) }

        return KotlinTypeInfo(false, type, text)
    }
