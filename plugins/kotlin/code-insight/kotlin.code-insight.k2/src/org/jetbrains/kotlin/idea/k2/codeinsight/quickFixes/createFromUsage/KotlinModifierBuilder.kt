// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiFactory


private val JVM_STATIC_FQN = FqName("kotlin.jvm.JvmStatic")

internal class KotlinModifierBuilder(
    private val targetContainer: KtElement,
    private val allowJvmStatic: Boolean = true
) {
    companion object {
        val javaPsiModifiersMapping = mapOf(
            JvmModifier.PRIVATE to KtTokens.PRIVATE_KEYWORD,
            JvmModifier.PUBLIC to KtTokens.PUBLIC_KEYWORD,
            JvmModifier.PROTECTED to KtTokens.PUBLIC_KEYWORD,
            JvmModifier.ABSTRACT to KtTokens.ABSTRACT_KEYWORD
        )
    }

    private val psiFactory = KtPsiFactory(targetContainer.project)

    val modifierList = psiFactory.createEmptyModifierList()

    private fun JvmModifier.transformAndAppend(): Boolean {
        javaPsiModifiersMapping[this]?.let {
            modifierList.add(psiFactory.createModifier(it))
            return true
        }

        when (this) {
            JvmModifier.STATIC -> {
                if (allowJvmStatic && targetContainer is KtClassOrObject) {
                    addAnnotation(JVM_STATIC_FQN)
                }
            }

            JvmModifier.ABSTRACT -> modifierList.add(psiFactory.createModifier(KtTokens.ABSTRACT_KEYWORD))
            JvmModifier.FINAL -> modifierList.add(psiFactory.createModifier(KtTokens.FINAL_KEYWORD))
            else -> return false
        }

        return true
    }

    var isValid = true
        private set

    fun addJvmModifier(modifier: JvmModifier) {
        isValid = isValid && modifier.transformAndAppend()
    }

    fun addJvmModifiers(modifiers: Iterable<JvmModifier>) {
        modifiers.forEach { addJvmModifier(it) }
    }

    fun addAnnotation(fqName: FqName) {
        if (!isValid) return
        modifierList.add(psiFactory.createAnnotationEntry("@${fqName.asString()}"))
    }
}
