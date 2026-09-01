// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.projectStructure.kaModule
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.refactoring.move.descriptor.K2MoveTargetDescriptor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal class MoveMemberToCompanionBlockIntention : MoveMemberIntention(
    textGetter = KotlinBundle.messagePointer("move.to.companion.block")
) {
    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (!element.kaModule(null).languageVersionSettings.supportsFeature(LanguageFeature.CompanionBlocks)) return null
        if (element is KtClassOrObject) return null
        if (!isApplicableForMoveMember(element)) return null
        val containingClassOrObject = element.containingClassOrObject
        if (containingClassOrObject is KtObjectDeclaration && !containingClassOrObject.isCompanion()) return null
        return findTextRangeForMoveMemberIntention(element)
    }

    override fun getTarget(element: KtNamedDeclaration): K2MoveTargetDescriptor.Declaration<*>? {
        val containingClassOrObject = element.containingClassOrObject
        val targetClass = if (containingClassOrObject is KtObjectDeclaration && containingClassOrObject.isCompanion()) {
            containingClassOrObject.containingClass()
        } else {
            containingClassOrObject as? KtClass
        } ?: return null
        return K2MoveTargetDescriptor.CompanionBlock(targetClass)
    }

    override fun startInWriteAction(): Boolean = false
}
