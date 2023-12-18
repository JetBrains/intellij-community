// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.*
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

class KotlinElementActionsFactory : JvmElementActionsFactory() {
    private fun JvmClass.toKtClassOrFile(): KtElement? = when (val psi = sourceElement) {
        is KtClassOrObject -> psi
        is KtLightElement<*, *> -> psi.kotlinOrigin
        else -> null
    }

    override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
        val targetClass = targetClass.toKtClassOrFile() as? KtClassOrObject ?: return emptyList()

        val result = mutableListOf<IntentionAction>()

        result.add(
            CreateKotlinCallableAction(
                request = request,
                targetClass = targetClass,
                abstract = false
            )
        )

        if (targetClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
            result.add(
                CreateKotlinCallableAction(
                    request = request,
                    targetClass = targetClass,
                    abstract = true
                )
            )
        }

        return result
    }
}