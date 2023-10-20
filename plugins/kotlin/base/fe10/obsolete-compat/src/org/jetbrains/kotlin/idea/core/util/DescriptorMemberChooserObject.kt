// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import javax.swing.Icon
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.DescriptorMemberChooserObject as DescriptorMemberChooserObjectNew

@Suppress("unused")
@ApiStatus.ScheduledForRemoval
@Deprecated("Use 'org.jetbrains.kotlin.idea.base.fe10.codeInsight.DescriptorMemberChooserObject' instead")
open class DescriptorMemberChooserObject(
    psiElement: PsiElement,
    descriptor: DeclarationDescriptor
) : DescriptorMemberChooserObjectNew(psiElement, descriptor) {
    companion object {
        @NlsSafe
        fun getText(descriptor: DeclarationDescriptor): String {
            return DescriptorMemberChooserObjectNew.getText(descriptor)
        }

        fun getIcon(declaration: PsiElement?, descriptor: DeclarationDescriptor): Icon? {
            return DescriptorMemberChooserObjectNew.getIcon(declaration, descriptor)
        }
    }
}