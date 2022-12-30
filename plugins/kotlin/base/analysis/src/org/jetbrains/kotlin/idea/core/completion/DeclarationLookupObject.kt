// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.completion

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.Name

interface DeclarationLookupObject : Iconable {
    val psiElement: PsiElement?
    val name: Name?

    @Deprecated("Use 'descriptor' available in 'DescriptorBasedDeclarationLookupObject' instead")
    val descriptor: DeclarationDescriptor?
}