// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections.branchedTransformations

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.getSubjectToIntroduce
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.introduceSubject
import org.jetbrains.kotlin.psi.KtWhenExpression

class IntroduceWhenSubjectInspection : AbstractApplicabilityBasedInspection<KtWhenExpression>(KtWhenExpression::class.java) {

    override fun isApplicable(element: KtWhenExpression) = element.getSubjectToIntroduce() != null

    override fun inspectionHighlightRangeInElement(element: KtWhenExpression) = element.whenKeyword.textRangeIn(element)

    override fun inspectionText(element: KtWhenExpression) = KotlinBundle.message("when.with.subject.should.be.used")

    override val defaultFixText get() = KotlinBundle.message("introduce.when.subject")

    override fun fixText(element: KtWhenExpression): String {
        val subject = element.getSubjectToIntroduce() ?: return ""
        return KotlinBundle.message("introduce.0.as.subject.0.when", subject.text)
    }

    override fun applyTo(element: KtWhenExpression, project: Project, editor: Editor?) {
        element.introduceSubject()
    }
}
