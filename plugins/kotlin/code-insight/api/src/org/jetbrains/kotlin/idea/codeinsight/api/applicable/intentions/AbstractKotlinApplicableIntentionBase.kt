// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.KotlinApplicableToolBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.isApplicableToElement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.psi.KtElement
import kotlin.reflect.KClass

/**
 * [AbstractKotlinApplicableIntentionBase] is a base implementation for [AbstractKotlinApplicableIntention] and
 * [AbstractKotlinModCommandWithContext].
 *
 * Note: A [familyNameGetter] for [SelfTargetingIntention] does not have to be set because inheritors of
 * [AbstractKotlinApplicableIntentionBase] must override [getFamilyName].
 */
abstract class AbstractKotlinApplicableIntentionBase<ELEMENT : KtElement>(
    elementType: KClass<ELEMENT>,
) : SelfTargetingIntention<ELEMENT>(elementType.java, { "" }), KotlinApplicableToolBase<ELEMENT> {
    /**
     * @see com.intellij.codeInsight.intention.IntentionAction.getFamilyName
     */
    abstract override fun getFamilyName(): @IntentionFamilyName String

    /**
     * Checks the intention's applicability based on [isApplicableByPsi] and [KotlinApplicabilityRange]. An override must invoke
     * [setTextGetter] to configure the action name.
     */
    override fun isApplicableTo(element: ELEMENT, caretOffset: Int): Boolean = isApplicableToElement(element, caretOffset)

    final override fun applyTo(element: ELEMENT, editor: Editor?) = applyTo(element, element.project, editor)
}