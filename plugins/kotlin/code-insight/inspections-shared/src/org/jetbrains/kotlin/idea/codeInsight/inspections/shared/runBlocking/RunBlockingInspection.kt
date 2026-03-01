// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptionsProcessor
import com.intellij.codeInspection.ex.JobDescriptor
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph.CallEdge
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph.FunctionNode
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph.GraphBuilder
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph.RBGraph
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils.ElementFilters
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils.MyPsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction


class RunBlockingInspection() : GlobalInspectionTool() {

    private val jobDescriptor = JobDescriptor(KotlinBundle.message("inspection.runblocking.presentation.descriptor"))

    enum class ExplorationLevel { STRICT, DECLARATION, ALL }

    override fun runInspection(
        scope: AnalysisScope,
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        val runBlockingProblems = analyzeProject(manager.project, scope, {jobDescriptor.totalAmount = it}, {
            globalContext.incrementJobDoneAmount(jobDescriptor, KotlinBundle.message("inspection.runblocking.analysis.graphbuilding.progress", it))
        })
        runBlockingProblems.forEach {
            runReadAction {
                val el = it.element
                val refEntity = globalContext.refManager.getReference(el.containingFile)
                val expr: PsiElement = (el as? KtCallExpression)?.calleeExpression ?: el
                problemDescriptionsProcessor.addProblemElement(
                    refEntity,
                    RunBlockingProblemDescriptor(expr, it.stacTrace)
                )
            }
        }
    }

    @JvmField
    var explorationLevel: ExplorationLevel = ExplorationLevel.DECLARATION

    override fun getOptionsPane(): OptPane {
        return OptPane.pane(
            OptPane.dropdown("explorationLevel", KotlinBundle.message("inspection.runblocking.presentation.settings.exploration.title"),
                             OptPane.option(ExplorationLevel.STRICT, KotlinBundle.message("inspection.runblocking.presentation.settings.exploration.option.strict")),
                             OptPane.option(ExplorationLevel.DECLARATION, KotlinBundle.message("inspection.runblocking.presentation.settings.exploration.option.declaration")),
                             OptPane.option(ExplorationLevel.ALL, KotlinBundle.message("inspection.runblocking.presentation.settings.exploration.option.all"))
            )
        )
    }

    override fun getAdditionalJobs(context: GlobalInspectionContext): Array<JobDescriptor> {
        return arrayOf(jobDescriptor)
    }

    override fun isGraphNeeded(): Boolean = false
    override fun isReadActionNeeded() = false
    
    private fun analyzeProject(project: Project, scope: AnalysisScope?, totalFilesTodo: (Int) -> Unit, incrementFilesDone: (String) -> Unit): List<RunBlockingProblem> {
        val relevantFiles = getRelevantFiles(project, scope)
        val rbFiles = mutableSetOf<VirtualFile>()
        
        val rbGraph = GraphBuilder(project)
            .setRbFileFound { file -> rbFiles.add(file) }
            .setIncrementFilesDoneFunction(incrementFilesDone)
            .setTotalFilesTodo(totalFilesTodo)
            .setRelevantFiles(relevantFiles)
            .setExplorationLevel(explorationLevel)
            .buildGraph()
        
        val runBlockingProblems = rbFiles.flatMap { checkRunBlockingsForFile(it, project, rbGraph) }
        return runBlockingProblems
        
    }

    private fun checkRunBlockingsForFile(file: VirtualFile, project: Project, rbGraph: RBGraph): List<RunBlockingProblem> {
        val rbs: MutableList<RunBlockingProblem> = mutableListOf()
        runReadAction {
            MyPsiUtils.findRunBlockings(file, project).forEach { rb ->
                val stackTrace = analyzeRunBlocking(rb, rbGraph)
                if (stackTrace != null) {
                    rbs.add(
                        RunBlockingProblem(rb, stackTrace)
                    )
                }
            }
        }
        return rbs
    }
    
    private fun analyzeRunBlocking(element: PsiElement, rbGraph: RBGraph): List<TraceElement>? {
        // Find first interesting parent if any, Aka function definition or async builder
        val psiFunOrBuilder = MyPsiUtils.findParent(element.parent, {
            it is KtNamedFunction
                    || ElementFilters.launchBuilder.isAccepted(it)
                    || ElementFilters.asyncBuilder.isAccepted(it)
                    || ElementFilters.runBlockingBuilderInvocation.isAccepted(it)
        }, {
            it is KtLambdaExpression
                    && !ElementFilters.lambdaAsArgForInlineFun.isAccepted(it)
                    && !ElementFilters.isSuspendLambda.isAccepted(it)
        })

        // If found element is function def, check if it runs in coroutine
        if (psiFunOrBuilder is KtNamedFunction) {
            // Check if function exists in graph (and therefore runs in coroutine)
            val functionId = FunctionNode.generateId(psiFunOrBuilder)
            if (rbGraph.containsFun(functionId)) {
                val funNode = rbGraph.getFunction(functionId)
                if (funNode.asyncContext) {
                    // Find the shortest path from async primitive to this runBlocking
                    val callEdgeTrace = rbGraph.findBuilderBFS(funNode)
                    return generateStackTrace(callEdgeTrace, funNode, element)
                }
                return null
            }
            // else if element is builder
        } else if (psiFunOrBuilder != null && psiFunOrBuilder is KtCallExpression) {
            val callee = psiFunOrBuilder.calleeExpression as KtNameReferenceExpression
            val ktFun = callee.reference?.resolve() as KtNamedFunction
            val fqName: String = ktFun.fqName.toString()
            return listOf(
                TraceElement(
                    fqName,
                    MyPsiUtils.getUrl(psiFunOrBuilder) ?: "",
                    MyPsiUtils.getFileAndLine(psiFunOrBuilder)
                ),
                TraceElement(
                    KotlinBundle.message("inspection.runblocking.analysis.found.runblocking"),
                    MyPsiUtils.getUrl(element) ?: "",
                    MyPsiUtils.getFileAndLine(element)
                )
            )
        }
        return null
    }

    private fun generateStackTrace(
        callEdgeTrace: List<CallEdge>,
        funNode: FunctionNode,
        element: PsiElement
    ): MutableList<TraceElement> {
        val stackTrace =
            if (callEdgeTrace.size != 0) mutableListOf<TraceElement>(TraceElement(
                callEdgeTrace[0].parent.fqName,
                callEdgeTrace[0].parent.declarationSite,
                callEdgeTrace[0].parent.fileAndLine))
            else mutableListOf<TraceElement>(TraceElement(funNode.fqName, funNode.declarationSite, funNode.fileAndLine))

        for (i in 0..<callEdgeTrace.size) {
            stackTrace.add(TraceElement(callEdgeTrace[i].child.fqName, callEdgeTrace[i].callSite, callEdgeTrace[i].fileAndLine))
        }
        stackTrace.add(TraceElement(
            KotlinBundle.message("inspection.runblocking.analysis.found.runblocking"),
            MyPsiUtils.getUrl(element) ?: "",
            MyPsiUtils.getFileAndLine(element)
        ))
        return stackTrace
    }

    private fun getRelevantFiles(project: Project, scope: AnalysisScope?): List<VirtualFile> {
        val relevantFiles = mutableListOf<VirtualFile>()
        ProjectFileIndex.getInstance(project).iterateContent {
            runReadAction {
                if (it.fileType is KotlinFileType && scope?.contains(it) != false) {
                    relevantFiles.add(it)
                }
                true
            }
        }
        return relevantFiles
    }
}
