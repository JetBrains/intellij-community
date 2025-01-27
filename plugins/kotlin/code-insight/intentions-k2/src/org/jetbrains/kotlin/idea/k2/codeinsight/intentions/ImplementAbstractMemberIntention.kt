// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ImplementAbstractMemberIntentionBase.ImplementableMember.JavaImplementableMember
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.ImplementAbstractMemberIntentionBase.ImplementableMember.KtImplementableMember
import org.jetbrains.kotlin.psi.*

internal class ImplementAbstractMemberIntention : ImplementAbstractMemberIntentionBase() {

    override fun computeText(element: KtNamedDeclaration): (() -> String)? = when (element) {
        is KtProperty -> KotlinBundle.lazyMessage("implement.abstract.property")
        is KtNamedFunction -> KotlinBundle.lazyMessage("implement.abstract.function")
        else -> null
    }

    override fun createImplementableMember(
        targetClass: PsiElement,
        abstractMember: KtNamedDeclaration,
    ): ImplementableMember? {
        return when (targetClass) {
            is KtEnumEntry -> KtImplementableMember.from(
                targetClass = targetClass,
                abstractMember = abstractMember,
                preferConstructorParameters = false,
            )

            is KtClass -> KtImplementableMember.from(
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
