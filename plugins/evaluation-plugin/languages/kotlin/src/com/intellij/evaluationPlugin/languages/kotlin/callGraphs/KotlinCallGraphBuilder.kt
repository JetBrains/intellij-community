package com.intellij.evaluationPlugin.languages.kotlin.callGraphs

import com.intellij.cce.core.Language
import com.intellij.evaluationPlugin.languages.callGraphs.CallGraph
import com.intellij.evaluationPlugin.languages.callGraphs.CallGraphBuilder
import com.intellij.evaluationPlugin.languages.callGraphs.CallGraphEdge
import com.intellij.evaluationPlugin.languages.callGraphs.CallGraphNode
import com.intellij.evaluationPlugin.languages.callGraphs.collectPsiFiles
import com.intellij.evaluationPlugin.languages.callGraphs.forEachIndexedWithProgress
import com.intellij.evaluationPlugin.languages.callGraphs.getNodeLocation
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinCallGraphBuilder : CallGraphBuilder {
  override val supportedLanguages: List<Language> = listOf(Language.KOTLIN)

  override fun build(project: Project, projectRoots: List<String>): CallGraph {
    val psiFiles = collectPsiFiles(project, listOf(KotlinFileType.INSTANCE), projectRoots)
    val nodes = collectNodes(psiFiles)
    val edges = collectEdges(nodes, psiFiles)
    return CallGraph(nodes, edges)
  }

  private fun qualifiedNameOf(function: KtNamedFunction): String {
    return QualifiedNameProviderUtil.getQualifiedName(function) ?: (function.name ?: "")
  }

  private fun collectNodes(psiFiles: List<PsiFile>): List<CallGraphNode> {
    val nodes = mutableListOf<CallGraphNode>()
    val visitor = object : KtTreeVisitorVoid() {
      override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val loc = function.getNodeLocation() ?: return
        val qName = qualifiedNameOf(function)
        nodes.add(
          CallGraphNode(
            address = loc,
            projectName = function.project.name,
            id = nodes.size.toString(),
            qualifiedName = qName
          )
        )
      }
    }
    println("Collecting nodes from ${psiFiles.size} files...")
    psiFiles.forEachIndexedWithProgress { _, file ->
      file.accept(visitor)
    }
    return nodes
  }

  private fun collectEdges(nodes: List<CallGraphNode>, psiFiles: List<PsiFile>): List<CallGraphEdge> {
    val edges = mutableListOf<CallGraphEdge>()
    val locationToNode = nodes.associateBy { it.address }

    fun findNodeId(function: KtNamedFunction): String? {
      val loc = function.getNodeLocation()
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

    psiFiles.forEachIndexedWithProgress { _, file ->
      file.accept(visitor)
    }
    return edges
  }
}
