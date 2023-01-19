// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.psi.childrenDfsSequence
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.variableCallOrThis
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureConfiguration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.modify
import org.jetbrains.kotlin.idea.refactoring.changeSignature.runChangeSignature
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.util.graph.DirectedGraph
import org.jetbrains.kotlin.util.graph.sortTopologically
import org.jetbrains.kotlin.util.sortedConservativelyBy

class ReorderParametersFix(
    element: KtNamedFunction,
    private val newParametersOrder: List<String>,
) : KotlinQuickFixAction<KtNamedFunction>(element) {
    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val function = element ?: return
        val descriptor =
            ActionUtil.underModalProgress(project, KotlinBundle.message("analyzing.functions"), function::descriptor)
                    as? FunctionDescriptor ?: return
        runChangeSignature(
            function.project,
            editor,
            descriptor,
            object : KotlinChangeSignatureConfiguration {
                override fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor =
                    originalDescriptor.modify { descriptor ->
                        if (descriptor.parametersCount == newParametersOrder.size) {
                            val parameterToIndex = newParametersOrder.withIndex().associate { it.value to it.index }
                            descriptor.parameters.sortBy { parameterToIndex[it.name] ?: -1 /*-1 is for receiver*/ }
                        }
                    }

                override fun performSilently(affectedFunctions: Collection<PsiElement>) = true
            },
            function,
            KotlinBundle.message("reorder.parameters.command")
        )
    }

    override fun startInWriteAction(): Boolean = false
    override fun getText(): String = KotlinBundle.message("reorder.parameters")
    override fun getFamilyName(): String = text

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val function = diagnostic.psiElement.parentOfType<KtNamedFunction>(withSelf = true) ?: return null
            val functionDescriptor = function.descriptor as? FunctionDescriptor ?: return null
            val parameters = function.valueParameterList?.parameters ?: return null
            val parametersDependencyGraph = parameters.asSequence().flatMap { parameter ->
                parameter.name?.let { parameterName ->
                    parameter.defaultValue
                        ?.childrenDfsSequence()
                        ?.filterIsInstance<KtNameReferenceExpression>()
                        ?.mapNotNull { it.resolveToCall()?.variableCallOrThis()?.resultingDescriptor as? ValueParameterDescriptor }
                        ?.filter { it.containingDeclaration == functionDescriptor }
                        ?.map { DirectedGraph.Edge(from = it.name.asString(), to = parameterName) }
                        ?.toList()
                } ?: emptyList()
            }.toSet()
            val parametersSortedTopologically = DirectedGraph(parametersDependencyGraph).sortTopologically() ?: return null
            val parameterToTopologicalIndex = parametersSortedTopologically.asSequence().withIndex()
                .flatMap { (topologicalIndex, parameters) -> parameters.map { IndexedValue(topologicalIndex, it) } }
                .associate { (topologicalIndex, parameterName) -> parameterName to topologicalIndex }
            val sortedParameters = parameters.asSequence()
                .mapNotNull(KtParameter::getName)
                .sortedConservativelyBy(parameterToTopologicalIndex::getValue)
                .toList()
            if (sortedParameters.size != parameters.size) return null
            return ReorderParametersFix(function, sortedParameters)
        }
    }
}
