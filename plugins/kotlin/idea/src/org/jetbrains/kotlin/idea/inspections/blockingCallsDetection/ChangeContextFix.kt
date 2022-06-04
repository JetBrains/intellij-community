// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.util.getFirstArgumentExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

internal class ChangeContextFix : LocalQuickFix {
    override fun getFamilyName(): String {
        return KotlinBundle.message("intention.switch.context.to.dispatchers.io")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val callExpression = descriptor.psiElement
            ?.parentsOfType<KtCallExpression>()
            ?.firstOrNull { it.calleeExpression?.textMatches("withContext") ?: false }
            ?: return

        val ktPsiFactory = KtPsiFactory(project, true)
        val replacedArgument = callExpression.resolveToCall(BodyResolveMode.PARTIAL)
            ?.getFirstArgumentExpression()
            ?.replaced(ktPsiFactory.createExpression("kotlinx.coroutines.Dispatchers.IO")) ?: return

        CoroutineBlockingCallInspectionUtils.postProcessQuickFix(replacedArgument, project)
    }
}