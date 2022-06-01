// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.completion

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.util.PlatformIcons
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import javax.swing.Icon

interface DeclarationLookupObject : Iconable {
    val psiElement: PsiElement?
    val descriptor: DeclarationDescriptor?
    val name: Name?
    val importableFqName: FqName?
    val isDeprecated: Boolean
}

data class PackageLookupObject(val fqName: FqName) : DeclarationLookupObject {
    override val psiElement: PsiElement? get() = null
    override val descriptor: DeclarationDescriptor? get() = null
    override val name: Name get() = fqName.shortName()
    override val importableFqName: FqName get() = fqName
    override val isDeprecated: Boolean get() = false
    override fun getIcon(flags: Int): Icon = PlatformIcons.PACKAGE_ICON
}

