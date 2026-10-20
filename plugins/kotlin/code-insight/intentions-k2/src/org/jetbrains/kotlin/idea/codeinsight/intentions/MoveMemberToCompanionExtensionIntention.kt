// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.projectStructure.kaModule
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class MoveMemberToCompanionExtensionIntention: MoveMemberToCompanionBlockIntention(textGetter = KotlinBundle.messagePointer("convert.to.companion.extension")) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (!element.kaModule(null).languageVersionSettings.supportsFeature(LanguageFeature.CompanionExtensions)) {
            return null
        }
        if (element.hasModifier(KtTokens.PROTECTED_KEYWORD)) return null
        val targetClass = getTargetClass(element) ?: return null
        if (generateSequence(targetClass as KtClassOrObject) {
                it.containingClassOrObject
            }.any {
                it.hasModifier(KtTokens.PROTECTED_KEYWORD) || it.hasModifier(KtTokens.PRIVATE_KEYWORD) && it.parent !is KtFile
            }) return null
        return super.applicabilityRange(element)
    }

    override fun getTarget(element: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        return getTargetClass(element)?.let { K2MoveTargetDescriptor.CompanionExtension(it) }
    }

    private fun getTargetClass(element: KtNamedDeclaration): KtClass? {
        val containingClassOrObject = element.containingClassOrObject
        return if (containingClassOrObject is KtObjectDeclaration && containingClassOrObject.isCompanion()) {
            containingClassOrObject.containingClass()
        } else {
            containingClassOrObject as? KtClass
        }
    }
}