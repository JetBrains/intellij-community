// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.parameterVisitor
import org.jetbrains.kotlin.resolve.BindingContext.UNUSED_MAIN_PARAMETER

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class UnusedMainParameterInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession) =
        parameterVisitor(fun(parameter: KtParameter) {
            val function = parameter.ownerFunction as? KtNamedFunction ?: return
            if (function.name != "main") return
            val context = function.analyzeWithContent()
            if (context[UNUSED_MAIN_PARAMETER, parameter] == true) {
                holder.registerProblem(
                    parameter,
                    KotlinBundle.message("since.kotlin.1.3.main.parameter.is.not.necessary"),
                    IntentionWrapper(RemoveUnusedFunctionParameterFix(parameter))
                )
            }
        })
}