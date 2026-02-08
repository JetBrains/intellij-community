package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.rbgraph

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.runBlocking.utils.MyPsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.LinkedList
import java.util.Queue

val LOG: Logger = Logger.getInstance(CodeStyle::class.java)
internal class RBGraph {
    private var functionMap = mutableMapOf<String, FunctionNode>()
    private val fileMap = mutableMapOf<String, MutableSet<FunctionNode>>()
    val edges = mutableSetOf<CallEdge>()
    
    fun clear() {
       functionMap.clear()
       fileMap.clear() 
       edges.clear()
    }
    
    fun addBuilder(psiElement: PsiElement): FunctionNode {
        if (psiElement !is KtCallExpression) {
            LOG.error(IllegalArgumentException("Builder must be KtCallExpression"))
            return FunctionNode("", "", "", "", false)
        } 
        val filePath = psiElement.containingFile.virtualFile.path
        val callee = psiElement.calleeExpression as KtNameReferenceExpression
        val ktFun = callee.reference?.resolve() as KtNamedFunction
        val fqName: String = ktFun.fqName.toString()
        val fileAndLine: String = MyPsiUtils.getFileAndLine(psiElement) 
        val url = MyPsiUtils.getUrl(psiElement) ?: ""
        
        val functionNode: FunctionNode = getOrAddBuilderToFM("${fqName}_${url}", url, fqName, fileAndLine)
        addToFileMap(functionNode, filePath)
        functionNode.isBuilder = true
        return functionNode
    }
    
    fun getOrCreateFunction(func: KtNamedFunction): FunctionNode {
        val id = FunctionNode.generateId(func)
        val filePath = func.containingFile.virtualFile.path
        return functionMap.getOrPut(id) {
            val node = FunctionNode(func)
            addToFileMap(node, filePath)
            node
        }
    }
    
    fun containsFun(id: String): Boolean = functionMap.contains(id)
    fun getFunction(id: String): FunctionNode {
        return functionMap[id]!!
    }

    
    /**
     * Connects two function nodes in the function hierarchy. 
     * Note that only parent.id and child.id are used to connect and not the actual objects.
     *
     * @param parent The parent function node.
     * @param child The child function node.
     * @param callSite The call site where the connection is made.
     * @param lineAndFile Something like: File.kt:1.
     */
    fun connect(parent: FunctionNode, child: FunctionNode, callSite: String, lineAndFile: String) =
        connect(parent.id, child.id, callSite, lineAndFile)
    
    
    /**
     * Connects two function nodes in the function hierarchy.
     *
     * @param parentId The id of the parent function node.
     * @param childId The id of the child function node.
     * @param callSite The call site where the connection is made.
     * @param lineAndFile Something like: File.kt:1.
     */
    fun connect(parentId: String, childId: String, callSite: String, lineAndFile: String) {
        // Happy path
        if (isConnected(parentId, childId)) return
        
        val parent = functionMap[parentId]!!
        val child = functionMap[childId]!!
        val newCallEdge = CallEdge(parent, child, callSite, lineAndFile)
        edges.add(newCallEdge)
        parent.addChild(newCallEdge)
        child.addParent(newCallEdge)
    }
    
    fun isConnected(parentId: String, childId: String): Boolean {
        val parent = functionMap[parentId]
        val child = functionMap[childId]
        if (parent == null || child == null) return false
        return parent.childEdges.any {it.child == child}
    }
    
    /**
     * Performs a breadth-first search starting from the given start node to find a builder or suspend node in the function graph.
     *
     * @param start The start node for the breadth-first search.
     * @return A list of nodes representing the path from the start node to the builder node.
     */
    fun findBuilderBFS(start: FunctionNode): List<CallEdge> {
        // Map to backtrack traversed path map<Parent, ChildConnection>
        val cameFrom: MutableMap<FunctionNode, CallEdge> = mutableMapOf()
        // Set all visited to false
        functionMap.values.forEach { it.visited = false }
        
        // BFS
        val queue: Queue<FunctionNode> = LinkedList()
        queue.add(start)
        var builderNode = start
        while (!queue.isEmpty()) {
            val currentNode = queue.poll()
            if (currentNode.isBuilder || currentNode.isSuspend) {
                builderNode = currentNode
                break
            }
            currentNode.visited = true
            currentNode.parentEdges
                .filter { !it.parent.visited }
                .map { it }
                .forEach { 
                    queue.add(it.parent)
                    cameFrom[it.parent] = it
                }
        }
        
        // Generate trace trough cameFrom backtrack map
        var backTrackEdge: CallEdge = cameFrom[builderNode] ?: return listOf()
        val traceAccumulator: MutableList<CallEdge> = mutableListOf(backTrackEdge)
        while (backTrackEdge.child != start) {
            backTrackEdge = cameFrom[backTrackEdge.child]!!
            traceAccumulator.add(backTrackEdge)
        }
        return traceAccumulator
    }
    
    private fun getOrAddBuilderToFM(id: String, declerationSite: String, fqName: String, fileAndLine: String): FunctionNode {
        return functionMap.getOrPut(id) { FunctionNode(id, declerationSite, fqName, fileAndLine, false) }
    }
    
    private fun addToFileMap(fn: FunctionNode, filePath: String) {
        val set = fileMap.getOrPut(filePath) { mutableSetOf() }
        set.add(fn)
    }
}