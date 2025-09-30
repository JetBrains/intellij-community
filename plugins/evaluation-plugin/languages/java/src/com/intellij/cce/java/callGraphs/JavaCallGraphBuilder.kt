package com.intellij.cce.java.callGraphs

import com.intellij.cce.callGraphs.*
import com.intellij.cce.core.Language
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod


private class NodeCollectorVisitor : JavaRecursiveElementVisitor() {
  val nodes: MutableList<CallGraphNode> = mutableListOf()

  override fun visitMethod(method: PsiMethod) {
    nodes.add(buildNodeFromMethod(method, method.name))
  }

  private fun buildNodeFromMethod(method: PsiMethod, nodeId: String): CallGraphNode {
    val nodeLocation = method.getNodeLocation()
    val projectName = method.project.name
    val qualifiedName = QualifiedNameProviderUtil.getQualifiedName(method)!!

    return CallGraphNode(
      address = nodeLocation,
      projectName = projectName,
      id = nodeId,
      qualifiedName = qualifiedName
    )
  }
}

private class EdgeCollectorVisitor(
  private val locationToNode: Map<CallGraphNodeLocation, CallGraphNode>,
) : JavaRecursiveElementVisitor() {
  val edges: MutableList<CallGraphEdge> = mutableListOf()

  override fun visitMethod(method: PsiMethod) {
    val calledMethods = getCalledMethods(method)
    val callerId = findNodeId(method)
    for (calledMethod in calledMethods) {
      val calleeId = findNodeIdOrNull(calledMethod)
      if (calleeId != null) {
        edges.add(CallGraphEdge(callerId, calleeId))
      }
    }
  }

  private fun findNodeId(method: PsiMethod): String {
    val location = method.getNodeLocation()
    return locationToNode[location]!!.id
  }

  private fun findNodeIdOrNull(method: PsiMethod): String? {
    val location = method.getNodeLocation()
    return locationToNode[location]?.id
  }

  private fun getCalledMethods(method: PsiMethod): List<PsiMethod> {
    val calledMethods = mutableListOf<PsiMethod>()
    val visitor = object : JavaRecursiveElementVisitor() {
      override fun visitCallExpression(callExpression: PsiCallExpression) {
        super.visitCallExpression(callExpression)
        callExpression.resolveMethod()?.let { resolved ->
          calledMethods.add(resolved)
        }
      }
    }
    method.accept(visitor)
    return calledMethods.toList()
  }
}


class JavaCallGraphBuilder : CallGraphBuilder {
  override val language: Language = Language.JAVA
  override fun build(project: Project): CallGraph {
    val psiFiles = collectPsiFiles(project, JavaFileType.INSTANCE)
    val nodes = collectNodes(psiFiles)
    val edges = collectEdges(nodes, psiFiles)
    return CallGraph(nodes, edges)

  }


  private fun collectNodes(psiFiles: List<PsiFile>): List<CallGraphNode> {
    val nodeCollectorVisitor = NodeCollectorVisitor()
    psiFiles.forEach { it.accept(nodeCollectorVisitor) }
    return nodeCollectorVisitor.nodes
  }

  private fun collectEdges(nodes: List<CallGraphNode>, psiFiles: List<PsiFile>): List<CallGraphEdge> {
    val edgeCollectorVisitor = EdgeCollectorVisitor(nodes.associateBy { it.address })
    psiFiles.forEach { it.accept(edgeCollectorVisitor) }
    return edgeCollectorVisitor.edges
  }
}