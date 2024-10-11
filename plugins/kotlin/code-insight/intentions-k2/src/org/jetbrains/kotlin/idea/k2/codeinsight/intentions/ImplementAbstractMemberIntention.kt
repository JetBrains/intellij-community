// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ImplementAbstractMemberIntentionBase.ImplementableMember.JavaImplementableMember
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ImplementAbstractMemberIntentionBase.ImplementableMember.KtImplementableMember
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

internal class ImplementAbstractMemberIntention : ImplementAbstractMemberIntentionBase() {

    override fun computeText(element: KtNamedDeclaration): (() -> String)? = when (element) {
        is KtProperty -> KotlinBundle.lazyMessage("implement.abstract.property")
        is KtNamedFunction -> KotlinBundle.lazyMessage("implement.abstract.function")
        else -> null
    }

    override fun KaSession.createImplementableMember(
        targetClass: PsiElement,
        abstractMember: KtNamedDeclaration,
    ): ImplementableMember? {
        return when (targetClass) {
            is KtLightClass -> KtImplementableMember.from(
                analysisSession = this,
                targetClass = targetClass,
                abstractMember = abstractMember,
                preferConstructorParameters = false,
            )

            is KtEnumEntry -> KtImplementableMember.from(
                analysisSession = this,
                targetClass = targetClass,
                abstractMember = abstractMember,
                preferConstructorParameters = false,
            )

            is PsiClass -> JavaImplementableMember.from(
                targetClass = targetClass,
                abstractMember = abstractMember,
            )

            else -> null
        }
    }
}
