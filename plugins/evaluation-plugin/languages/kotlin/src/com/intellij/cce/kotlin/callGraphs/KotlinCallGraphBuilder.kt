package com.intellij.cce.kotlin.callGraphs

import com.intellij.cce.callGraphs.*
import com.intellij.cce.core.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinCallGraphBuilder : CallGraphBuilder {
  override val language: Language = Language.KOTLIN

  override fun build(project: Project): CallGraph {
    val psiFiles = collectPsiFiles(project)
    println("number of psiFiles: ${psiFiles.size}")
    val nodes = collectNodes(psiFiles)
    println("number of nodes: ${nodes.size}")
    val edges = collectEdges(nodes, psiFiles)
    println("number of edges: ${edges.size}")
    return CallGraph(nodes, edges)
  }

  private fun collectPsiFiles(project: Project): List<PsiFile> {
    val psiManager = PsiManager.getInstance(project)
    val result = mutableListOf<PsiFile>()
    val index = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
    index.iterateContent { file ->
      if (!file.isDirectory && (file.extension == "kt" || file.fileType == KotlinFileType.INSTANCE)) {
        psiManager.findFile(file)?.let { result.add(it) }
      }
      true
    }
    return result
  }

  private fun collectNodes(psiFiles: List<PsiFile>): List<CallGraphNode> {
    val nodes = mutableListOf<CallGraphNode>()
    val visitor = object : KtTreeVisitorVoid() {
      override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val name = function.name ?: return
        val range = function.textRange
        nodes.add(
          CallGraphNode(
            address = CallGraphNodeLocation(projectRootFilePath = function.containingFile.virtualFile.path, textRange = range.startOffset..range.endOffset),
            projectName = function.project.name,
            id = name,
            qualifiedName = name
          )
        )
      }
    }
    psiFiles.forEach { it.accept(visitor) }
    return nodes
  }

  private fun collectEdges(nodes: List<CallGraphNode>, psiFiles: List<PsiFile>): List<CallGraphEdge> {
    val edges = mutableListOf<CallGraphEdge>()
    val locationToNode = nodes.associateBy { it.address }

    fun findNodeId(function: KtNamedFunction): String? {
      val range = function.textRange
      val loc = CallGraphNodeLocation(projectRootFilePath = function.containingFile.virtualFile.path, textRange = range.startOffset..range.endOffset)
      return locationToNode[loc]?.id
    }

    val visitor = object : KtTreeVisitorVoid() {
      override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val callerId = findNodeId(function) ?: return
        function.accept(object : KtTreeVisitorVoid() {
          override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            val calleeExpr = expression.calleeExpression ?: return
            val resolved = calleeExpr.mainReference?.resolve() ?: return
            val calleeFunction = resolved as? KtNamedFunction ?: return
            val calleeId = findNodeId(calleeFunction) ?: return
            edges.add(CallGraphEdge(callerId, calleeId))
          }
        })
      }
    }

    psiFiles.forEach { it.accept(visitor) }
    return edges
  }
}
