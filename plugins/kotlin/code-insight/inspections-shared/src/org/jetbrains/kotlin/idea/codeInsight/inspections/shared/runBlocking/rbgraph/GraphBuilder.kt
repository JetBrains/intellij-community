package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.RunBlockingInspection
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils.ElementFilters
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils.MyPsiUtils
import org.jetbrains.kotlin.idea.search.declarationsSearch.forEachOverridingElement
import org.jetbrains.kotlin.idea.search.declarationsSearch.hasOverridingElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction


/**
 * Constructs runBlocking graph, based on settings provided:
 *
 * @property project Project
 * @property totalFilesTodo Optional callback for progress indication, sets the total amount of files.
 * @property incrementFilesDone Optional callback increment files done.
 * @property rbFileFound The callback function to be called when a relevant file is found.
 * @property relevantFiles The list of relevant files to be processed.
 * @property level The exploration level for building the graph.
 * @property urlToVirtualFileMap A map from URL to virtual file for relevant files.
 * @property rbGraph The RBGraph object representing the graph.
 */
internal class GraphBuilder(private val project: Project) {
    private var totalFilesTodo: (Int) -> Unit = {}
    private var incrementFilesDone: (String) -> Unit = {}
    private var rbFileFound: (VirtualFile) -> Unit = {}
    private var relevantFiles: List<VirtualFile> = emptyList()
    private var level: RunBlockingInspection.ExplorationLevel = RunBlockingInspection.ExplorationLevel.DECLARATION
    private var urlToVirtualFileMap: MutableMap<String, VirtualFile> = mutableMapOf()
    
    private val rbGraph = RBGraph()

    fun setRelevantFiles(relevantFiles: List<VirtualFile>): GraphBuilder {
        this.relevantFiles = relevantFiles
        relevantFiles.forEach {urlToVirtualFileMap[it.url] = it}
        return this
    }
    fun setIncrementFilesDoneFunction(incrementFilesDone: (String) -> Unit): GraphBuilder {
        this.incrementFilesDone = incrementFilesDone
        return this
    }

    fun setTotalFilesTodo(totalFilesTodo: (Int) -> Unit): GraphBuilder {
        this.totalFilesTodo = totalFilesTodo
        return this
    }

    fun setRbFileFound(fileFound: (VirtualFile) -> Unit): GraphBuilder {
        this.rbFileFound = fileFound
        return this
    }

    fun setExplorationLevel(level: RunBlockingInspection.ExplorationLevel): GraphBuilder {
        this.level = level
        return this
    }
    
    fun buildGraph(): RBGraph {
        totalFilesTodo(relevantFiles.size)
        relevantFiles.forEachIndexed() { index, file ->
            runReadAction {
                incrementFilesDone(file.name)
                // Search kotlin file for runBlocking calls, and generate tree
                MyPsiUtils.findRunBlockings(file, project).forEach { rb ->
                    createSubtree(rb)
                    rbFileFound(file)
                }
                // Search for async, and launch builders in case globalscope or android scopes are used
                MyPsiUtils.findNonBlockingBuilders(file, project).forEach { builder ->
                    createSubtree(builder)
                }
                // suspend fun for completeness
                MyPsiUtils.findSuspendFuns(file, project).forEach { susFun ->
                    if (susFun is KtNamedFunction && susFun.fqName != null)
                        exploreFunDeclaration(susFun, rbGraph.getOrCreateFunction(susFun))
                }
            }
        }
        return rbGraph
    }
    
    
    private fun createSubtree(builder: PsiElement) {
        //Add runBlocking root to graph
        val runBlockingNode = rbGraph.addBuilder(builder)
        // Go straight to lambda arg
        if (builder is KtCallExpression) {
            val lam = builder.lambdaArguments.lastOrNull()?.getLambdaExpression() ?: return
            exploreFunDeclaration(lam, runBlockingNode)
        }
    }
    
    private fun extractCallsFromBody(body: PsiElement): List<KtCallExpression> {
        val methodCalls: List<KtCallExpression> = MyPsiUtils.findAllChildren(body, { it is KtCallExpression },
            { ElementFilters.launchBuilder.isAccepted(it)
                    || ElementFilters.asyncBuilder.isAccepted(it)
                    || ElementFilters.runBlockingBuilderInvocation.isAccepted(it)
                    || it is KtLambdaExpression
            }).filterIsInstance<KtCallExpression>()

        // Find all lambdas but not nested lambdas.
        val lambdaExprs = MyPsiUtils.findAllChildren(body, { it is KtLambdaExpression },
            { it.parent is KtLambdaExpression }
        ).filterIsInstance<KtLambdaExpression>()

        // Filter out lambdas that are not parameter to inline function
        val inlineLams = lambdaExprs.filter { ElementFilters.lambdaAsArgForInlineFun.isAccepted(it) 
                || ElementFilters.isSuspendLambda.isAccepted(it) }
        // Return found methodcalls + explore lambdas
        return methodCalls + inlineLams.flatMap(::extractCallsFromBody)
    }

    private fun exploreFunDeclaration(currentPsiEl: PsiElement, currentNode: FunctionNode) {
        // If explored stop
        if (currentNode.visited) return
        currentNode.visited = true
        //Find all calls from this code block

        val methodCalls = extractCallsFromBody(currentPsiEl)
        
        for (call in methodCalls) {
            // Find method decl for call
            val callee = call.calleeExpression 
            if (callee is KtNameReferenceExpression) {
                val psiFn = callee.reference?.resolve()
                if (psiFn is KtNamedFunction) {
                    // if method in non-relevant file skip call.calleeExpression.reference.resolve()
                    val funFile = MyPsiUtils.getFileForElement(psiFn)
                    if (!relevantFiles.contains(funFile)) continue

                    // Find all function overrides
                    val toExplore = mutableListOf<KtNamedFunction>()

                    when (level) {
                        RunBlockingInspection.ExplorationLevel.STRICT ->
                            if (!psiFn.hasOverridingElement()) toExplore.add(psiFn)

                        RunBlockingInspection.ExplorationLevel.DECLARATION -> toExplore.add(psiFn)
                        RunBlockingInspection.ExplorationLevel.ALL -> {
                            toExplore.add(psiFn)
                            psiFn.forEachOverridingElement { _, overrideFn ->
                                if (overrideFn is KtNamedFunction && relevantFiles.contains(
                                        MyPsiUtils.getFileForElement(overrideFn)
                                    )
                                ) toExplore.add(overrideFn)
                                true
                            }
                        }
                    }

                    toExplore.forEach { fn ->
                        if (fn.fqName != null) {
                            // Get or create function node and explore
                            val functionNode = rbGraph.getOrCreateFunction(fn)
                            // If no overrides -> Strong connection 
                            rbGraph.connect(currentNode, functionNode, MyPsiUtils.getUrl(call)!!, MyPsiUtils.getFileAndLine(call))
                            exploreFunDeclaration(fn, functionNode)
                        }
                    }
                }
            }
        }
    }
}