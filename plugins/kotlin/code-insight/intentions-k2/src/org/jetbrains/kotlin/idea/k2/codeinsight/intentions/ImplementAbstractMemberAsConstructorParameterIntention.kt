// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ImplementAbstractMemberIntentionBase.ImplementableMember.KtImplementableMember
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty

internal class ImplementAbstractMemberAsConstructorParameterIntention : ImplementAbstractMemberIntentionBase() {
    override fun computeText(element: KtNamedDeclaration): (() -> String)? {
        if (element !is KtProperty) return null
        return KotlinBundle.lazyMessage("implement.as.constructor.parameter")
    }

    override fun applicabilityRange(element: KtNamedDeclaration): TextRange? {
        if (element !is KtProperty) return null
        return super.applicabilityRange(element)
    }

    override fun KaSession.createImplementableMember(
        targetClass: PsiElement,
        abstractMember: KtNamedDeclaration,
    ): ImplementableMember? {
        return when (targetClass) {
            is KtLightClass -> {
                KtImplementableMember.from(
                    analysisSession = this,
                    targetClass = targetClass,
                    abstractMember = abstractMember,
                    preferConstructorParameters = true,
                )
            }

            else -> null
        }
    }
}
