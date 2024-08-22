// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter.markers

import com.intellij.codeInsight.daemon.impl.GutterTooltipBuilder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.psiUtil.isExpectDeclaration
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier

object KotlinGutterTooltipHelper : GutterTooltipBuilder() {
    override fun getLinkProtocol(): String {
        return "kotlinClass"
    }

    override fun shouldSkipAsFirstElement(element: PsiElement): Boolean {
        return element.unwrapped is KtCallableDeclaration || element.unwrapped is PsiMethod
    }

    override fun getLinkReferenceText(element: PsiElement): String? {
        val moduleName = element.module?.name?.let { "$it:" } ?: ""
        val qualifiedName = when (val el = element.unwrapped) {
            is PsiMethod -> el.containingClass?.qualifiedName
            is PsiClass -> el.qualifiedName
            is KtClass -> el.fqName?.asString()
            else -> PsiTreeUtil.getStubOrPsiParentOfType(el, KtClass::class.java)?.fqName?.asString()
        } ?: return null
        return moduleName + qualifiedName
    }

    override fun getContainingElement(element: PsiElement): PsiElement? {
        val unwrapped = element.unwrapped
        if (unwrapped is PsiMember) {
            return PsiTreeUtil.getStubOrPsiParentOfType(unwrapped, PsiMember::class.java) ?: unwrapped.containingFile
        }

        var member: KtDeclaration?
        if (unwrapped is KtParameter) {
            member = PsiTreeUtil.getStubOrPsiParentOfType(unwrapped, KtClass::class.java)
        }
        else {
            member = PsiTreeUtil.getStubOrPsiParentOfType(unwrapped, KtDeclaration::class.java)
        }
        if (member == null && unwrapped is KtDeclaration) {
            member = unwrapped.containingClass()
        }
        return member ?: unwrapped?.containingFile
    }

    override fun getLocationString(element: PsiElement): String? {
        val classOrObject = element.unwrapped as? KtClassOrObject ?: return null
        val moduleName = element.module?.name ?: return null
        val moduleNameRequired = classOrObject.hasActualModifier() || classOrObject.isExpectDeclaration()
        return if (moduleNameRequired) " [$moduleName]" else null
    }

    override fun getPresentableName(element: PsiElement): String? {
        return (element as? PsiNamedElement)?.name
    }

    override fun appendElement(sb: StringBuilder, element: PsiElement, skip: Boolean) {
        super.appendElement(sb, element, skip)
        val file = element.containingFile
        if (file is PsiClassOwner) {
            appendPackageName(sb, file.packageName)
        }
    }

    override fun isDeprecated(element: PsiElement): Boolean {
        return element is PsiDocCommentOwner && element.isDeprecated
    }

}