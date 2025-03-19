// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplacePrintlnWithLoggingInspection.Util.isPrintFunction
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.callExpressionVisitor

internal class ReplacePrintlnWithLoggingInspection : AbstractKotlinInspection() {
    private object Util {
        private val kotlinIoPackage: FqName = FqName("kotlin.io")

        private val printFunctions: Set<CallableId> = listOf(
            Name.identifier("print"),
            Name.identifier("println"),
        ).map { name -> CallableId(kotlinIoPackage, name) }.toSet()

        fun CallableId.isPrintFunction(): Boolean = this in printFunctions
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): KtVisitorVoid = callExpressionVisitor(fun(call) {
        val originalFile = call.containingFile.originalFile

        if (originalFile is KtFile && originalFile.isScript()) return

        val identifier = call.calleeExpression?.text ?: return

        val callableId = analyze(call) {
            call.resolveToCall()?.singleFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.callableId
        } ?: return

        if (!callableId.isPrintFunction()) return

        holder.registerProblem(call, KotlinBundle.message("uses.of.should.be.replaced.with.logging", identifier))
    })
}