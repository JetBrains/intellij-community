// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.completion

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import javax.swing.Icon

interface DescriptorBasedDeclarationLookupObject : DeclarationLookupObject {
    override val descriptor: DeclarationDescriptor?
    val importableFqName: FqName?
    val isDeprecated: Boolean
}

data class PackageLookupObject(val fqName: FqName) : DescriptorBasedDeclarationLookupObject {
    override val psiElement: PsiElement? get() = null
    override val descriptor: DeclarationDescriptor? get() = null
    override val name: Name get() = fqName.shortName()
    override val importableFqName: FqName get() = fqName
    override val isDeprecated: Boolean get() = false
    override fun getIcon(flags: Int): Icon = AllIcons.Nodes.Package
}

