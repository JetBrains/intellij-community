package com.intellij.cce.java.callGraphs

import com.intellij.cce.callGraphs.*
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*


private fun getNodeLocation(method: PsiMethod): CallGraphNodeLocation {
  return CallGraphNodeLocation(
    projectRootFilePath = method.containingFile.virtualFile.path,
    textRange = method.textRange.startOffset..method.textRange.endOffset
  )
}

private class NodeCollectorVisitor : JavaRecursiveElementVisitor() {
  val nodes: MutableList<CallGraphNode> = mutableListOf()

  override fun visitMethod(method: PsiMethod) {
    nodes.add(buildNodeFromMethod(method, method.name))
  }

  private fun buildNodeFromMethod(method: PsiMethod, nodeId: String): CallGraphNode {
    val nodeLocation = getNodeLocation(method)
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
    val location = getNodeLocation(method)
    return locationToNode[location]!!.id
  }

  private fun findNodeIdOrNull(method: PsiMethod): String? {
    val location = getNodeLocation(method)
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
  override fun build(project: Project): CallGraph {
    val psiFiles = collectPsiFiles(project)
    val nodes = collectNodes(psiFiles)
    val edges = collectEdges(nodes, psiFiles)
    return CallGraph(nodes, edges)

  }

  private fun collectPsiFiles(project: Project): List<PsiFile> {
    val psiManager = PsiManager.getInstance(project)
    val result = mutableListOf<PsiFile>()
    val index = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
    index.iterateContent { file ->
      if (!file.isDirectory && file.fileType == com.intellij.ide.highlighter.JavaFileType.INSTANCE) {
        psiManager.findFile(file)?.let { result.add(it) }
      }
      true
    }
    return result
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