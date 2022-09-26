// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.codeInsight

import com.intellij.codeInsight.generation.ClassMemberWithElement
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.PsiElementMemberChooserObject
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.KotlinDescriptorIconProvider
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import javax.swing.Icon

open class DescriptorMemberChooserObject(
    psiElement: PsiElement,
    open val descriptor: DeclarationDescriptor
) : PsiElementMemberChooserObject(
    psiElement,
    getText(descriptor),
    getIcon(psiElement, descriptor)
), ClassMemberWithElement {

    override fun getParentNodeDelegate(): MemberChooserObject {
        val parent = descriptor.containingDeclaration ?: error("No parent for $descriptor")

        val declaration = if (psiElement is KtDeclaration) { // kotlin
            PsiTreeUtil.getStubOrPsiParentOfType(psiElement, KtNamedDeclaration::class.java)
                ?: PsiTreeUtil.getStubOrPsiParentOfType(psiElement, KtFile::class.java)
        } else { // java or compiled
            (psiElement as PsiMember).containingClass
        } ?: error("No parent for $psiElement")

        return when (declaration) {
            is KtFile -> PsiElementMemberChooserObject(declaration, declaration.name)
            else -> DescriptorMemberChooserObject(declaration, parent)
        }
    }

    override fun equals(other: Any?) = this === other || other is DescriptorMemberChooserObject && descriptor == other.descriptor

    override fun hashCode() = descriptor.hashCode()

    override fun getElement() = psiElement

    companion object {
        private val MEMBER_RENDERER = DescriptorRenderer.withOptions {
            withDefinedIn = false
            modifiers = emptySet()
            startFromName = true
            classifierNamePolicy = ClassifierNamePolicy.SHORT
            presentableUnresolvedTypes = true
        }

        @NlsSafe
        fun getText(descriptor: DeclarationDescriptor): String {
            return if (descriptor is ClassDescriptor)
                descriptor.fqNameUnsafe.render()
            else
                MEMBER_RENDERER.render(descriptor)
        }

        fun getIcon(declaration: PsiElement?, descriptor: DeclarationDescriptor): Icon? = if (declaration != null && declaration.isValid) {
            val isClass = declaration is PsiClass || declaration is KtClass
            val flags = if (isClass) 0 else Iconable.ICON_FLAG_VISIBILITY
            if (declaration is KtDeclaration) {
                // kotlin declaration
                // visibility and abstraction better detect by a descriptor
                KotlinDescriptorIconProvider.getIcon(descriptor, declaration, flags)
            } else {
                // it is better to show java icons for java code
                declaration.getIcon(flags)
            }
        } else {
            KotlinDescriptorIconProvider.getIcon(descriptor, declaration, 0)
        }
    }
}
