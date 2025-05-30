// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections.JavaMapForEachInspectionUtils
import org.jetbrains.kotlin.idea.inspections.collections.isMap
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.synthetic.isResolvedWithSamConversions

class JavaMapForEachInspection : AbstractApplicabilityBasedInspection<KtCallExpression>(KtCallExpression::class.java) {
    override fun isApplicable(element: KtCallExpression): Boolean {
        if (!JavaMapForEachInspectionUtils.applicableByPsi(element)) return false

        val context = element.analyze(BodyResolveMode.PARTIAL)
        val resolvedCall = element.getResolvedCall(context) ?: return false
        return resolvedCall.dispatchReceiver?.type?.isMap() == true && resolvedCall.isResolvedWithSamConversions()
    }

    override fun inspectionHighlightRangeInElement(element: KtCallExpression): TextRange? = element.calleeExpression?.textRangeIn(element)

    override fun inspectionText(element: KtCallExpression) =
        KotlinBundle.message("java.map.foreach.method.call.should.be.replaced.with.kotlin.s.foreach")

    override val defaultFixText get() = KotlinBundle.message("replace.with.kotlin.s.foreach")

    override fun applyTo(element: KtCallExpression, project: Project, editor: Editor?) {
        val lambda = element.singleLambdaArgumentExpression() ?: return
        val valueParameters = lambda.valueParameters
        lambda.functionLiteral.valueParameterList?.replace(
            KtPsiFactory(project).createLambdaParameterList("(${valueParameters[0].text}, ${valueParameters[1].text})")
        )
    }
}
