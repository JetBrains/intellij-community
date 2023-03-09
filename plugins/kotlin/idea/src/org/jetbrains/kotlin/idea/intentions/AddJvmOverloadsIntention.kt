// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.jvm.annotations.JVM_OVERLOADS_FQ_NAME
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

private val annotationFqName get() = JVM_OVERLOADS_FQ_NAME

class AddJvmOverloadsIntention : SelfTargetingIntention<KtModifierListOwner>(
    KtModifierListOwner::class.java,
    KotlinBundle.lazyMessage("add.jvmoverloads.annotation"),
), LowPriorityAction {
    override fun isApplicableTo(element: KtModifierListOwner, caretOffset: Int): Boolean {
        val (targetName, parameters) = when (element) {
            is KtNamedFunction -> {
                val funKeyword = element.funKeyword ?: return false
                val valueParameterList = element.valueParameterList ?: return false
                if (caretOffset !in funKeyword.startOffset..valueParameterList.endOffset) {
                    return false
                }

                KotlinBundle.message("function.0", element.name.toString()) to valueParameterList.parameters
            }
            is KtSecondaryConstructor -> {
                val constructorKeyword = element.getConstructorKeyword()
                val valueParameterList = element.valueParameterList ?: return false
                if (caretOffset !in constructorKeyword.startOffset..valueParameterList.endOffset) {
                    return false
                }

                KotlinBundle.message("text.secondary.constructor") to valueParameterList.parameters
            }
            is KtPrimaryConstructor -> {
                if (element.parent.safeAs<KtClass>()?.isAnnotation() == true) return false
                val parameters = (element.valueParameterList ?: return false).parameters

                // For primary constructors with all default values, a zero-arg constructor is generated anyway. If there's only one
                // parameter and it has a default value, the bytecode with and without @JvmOverloads is exactly the same.
                if (parameters.singleOrNull()?.hasDefaultValue() == true) {
                    return false
                }

                KotlinBundle.message("text.primary.constructor") to parameters
            }
            else -> return false
        }

        setTextGetter(KotlinBundle.lazyMessage("add.jvmoverloads.annotation.to.0", targetName))

        return element.containingKtFile.platform.isJvm()
                && parameters.any { it.hasDefaultValue() }
                && element.findAnnotation(annotationFqName) == null
    }

    override fun applyTo(element: KtModifierListOwner, editor: Editor?) {
        if (element is KtPrimaryConstructor) {
            if (element.getConstructorKeyword() == null) {
                element.addBefore(KtPsiFactory(element.project).createConstructorKeyword(), element.valueParameterList)
            }
            element.addAnnotation(annotationFqName, whiteSpaceText = " ")
        } else {
            element.addAnnotation(annotationFqName)
        }
    }
}
