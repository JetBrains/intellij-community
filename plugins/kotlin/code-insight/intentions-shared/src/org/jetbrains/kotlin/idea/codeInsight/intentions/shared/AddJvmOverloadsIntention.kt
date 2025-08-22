// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.PriorityAction.Priority
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.addAnnotation
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal class AddJvmOverloadsIntention : KotlinPsiUpdateModCommandAction.ClassBased<KtModifierListOwner, Unit>(KtModifierListOwner::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("add.jvmoverloads.annotation")

    override fun isElementApplicable(element: KtModifierListOwner, context: ActionContext): Boolean {
        val caretOffset = context.offset
        val parameters = when (element) {
            is KtNamedFunction -> {
                val funKeyword = element.funKeyword ?: return true
                val valueParameterList = element.valueParameterList ?: return true
                if (caretOffset !in funKeyword.startOffset..valueParameterList.endOffset) {
                    return true
                }

                valueParameterList.parameters
            }
            is KtSecondaryConstructor -> {
                val constructorKeyword = element.getConstructorKeyword()
                val valueParameterList = element.valueParameterList ?: return true
                if (caretOffset !in constructorKeyword.startOffset..valueParameterList.endOffset) {
                    return true
                }

                valueParameterList.parameters
            }
            is KtPrimaryConstructor -> {
                if (element.parent.safeAs<KtClass>()?.isAnnotation() == true) return true
                val parameters = (element.valueParameterList ?: return true).parameters

                // For primary constructors with all default values, a zero-arg constructor is generated anyway. If there's only one
                // parameter and it has a default value, the bytecode with and without @JvmOverloads is exactly the same.
                if (parameters.singleOrNull()?.hasDefaultValue() == true) {
                    return true
                }

                parameters
            }
            else -> return false
        }

        return element.containingKtFile.platform.isJvm()
                && parameters.any { it.hasDefaultValue() }
                && element.findAnnotation(JvmStandardClassIds.JVM_OVERLOADS_CLASS_ID) == null
    }

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation? {
        val caretOffset = context.offset
        val (targetName, parameters) = when (element) {
            is KtNamedFunction -> {
                val funKeyword = element.funKeyword ?: return null
                val valueParameterList = element.valueParameterList ?: return null
                if (caretOffset !in funKeyword.startOffset..valueParameterList.endOffset) {
                    return null
                }

                KotlinBundle.message("function.0", element.name.toString()) to valueParameterList.parameters
            }
            is KtSecondaryConstructor -> {
                val constructorKeyword = element.getConstructorKeyword()
                val valueParameterList = element.valueParameterList ?: return null
                if (caretOffset !in constructorKeyword.startOffset..valueParameterList.endOffset) {
                    return null
                }

                KotlinBundle.message("text.secondary.constructor") to valueParameterList.parameters
            }
            is KtPrimaryConstructor -> {
                if (element.parent.safeAs<KtClass>()?.isAnnotation() == true) return null
                val parameters = (element.valueParameterList ?: return null).parameters

                // For primary constructors with all default values, a zero-arg constructor is generated anyway. If there's only one
                // parameter and it has a default value, the bytecode with and without @JvmOverloads is exactly the same.
                if (parameters.singleOrNull()?.hasDefaultValue() == true) {
                    return null
                }

                KotlinBundle.message("text.primary.constructor") to parameters
            }
            else -> return null
        }

        if (element.containingKtFile.platform.isJvm()
            && parameters.any { it.hasDefaultValue() }
            && element.findAnnotation(JvmStandardClassIds.JVM_OVERLOADS_CLASS_ID) != null
        ) {
            return null
        }

        return Presentation.of(KotlinBundle.message("add.jvmoverloads.annotation.to.0", targetName)).withPriority(Priority.LOW)
    }

    override fun invoke(
        actionContext: ActionContext,
        element: KtModifierListOwner,
        elementContext: Unit,
        updater: ModPsiUpdater
    ) {
        if (element is KtPrimaryConstructor) {
            if (element.getConstructorKeyword() == null) {
                element.addBefore(KtPsiFactory(element.project).createConstructorKeyword(), element.valueParameterList)
            }
            element.addAnnotation(JvmStandardClassIds.JVM_OVERLOADS_CLASS_ID, whiteSpaceText = " ")
        } else {
            element.addAnnotation(JvmStandardClassIds.JVM_OVERLOADS_CLASS_ID)
        }
    }

    override fun KaSession.prepareContext(element: KtModifierListOwner) {
    }
}
