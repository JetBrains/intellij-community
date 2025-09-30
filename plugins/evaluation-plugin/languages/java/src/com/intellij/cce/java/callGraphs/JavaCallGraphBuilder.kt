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

private class EdgeCollectorVisitor(
  private val locationToNode: Map<CallGraphNodeLocation, CallGraphNode>,
) : JavaRecursiveElementVisitor() {
  val edges: MutableList<CallGraphEdge> = mutableListOf()

  override fun visitMethod(method: PsiMethod) {
    val callerId = findNodeIdOrNull(method)
    if (callerId == null) return

    val calledMethods = getCalledMethods(method)
    for (calledMethod in calledMethods) {
      val calleeId = findNodeIdOrNull(calledMethod)
      if (calleeId != null) {
        edges.add(CallGraphEdge(callerId, calleeId))
      }
    }
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
  override val supportedLanguages: List<Language> = listOf(Language.JAVA)

  override fun build(project: Project, projectRoots: List<String>): CallGraph {
    val psiFiles = collectPsiFiles(project, listOf(JavaFileType.INSTANCE), projectRoots)
    val nodes = buildNodes(psiFiles)
    val edges = collectEdges(nodes, psiFiles)
    return CallGraph(nodes, edges)
  }

  private fun buildNodes(psiFiles: List<PsiFile>): List<CallGraphNode> {
    val psiMethods = collectAllPsiMethods(psiFiles)
    return psiMethods.mapIndexed { index, method -> buildNodeFromMethod(method, index.toString()) }
  }

  private fun collectAllPsiMethods(psiFiles: List<PsiFile>): List<PsiMethod> {
    val psiMethods = mutableListOf<PsiMethod>()
    val psiMethodsCollector = object : JavaRecursiveElementVisitor() {
      override fun visitMethod(method: PsiMethod) {
        super.visitMethod(method)
        psiMethods.add(method)
      }
    }
    psiFiles.forEach { it.accept(psiMethodsCollector) }
    return psiMethods
  }

  private fun buildNodeFromMethod(method: PsiMethod, nodeId: String): CallGraphNode {
    val nodeLocation = method.getNodeLocation()!!
    val projectName = method.project.name
    val qualifiedName = QualifiedNameProviderUtil.getQualifiedName(method)!!

    return CallGraphNode(
      address = nodeLocation,
      projectName = projectName,
      id = nodeId,
      qualifiedName = qualifiedName
    )
  }

  private fun collectEdges(nodes: List<CallGraphNode>, psiFiles: List<PsiFile>): List<CallGraphEdge> {
    val edgeCollectorVisitor = EdgeCollectorVisitor(nodes.associateBy { it.address })
    psiFiles.forEach { it.accept(edgeCollectorVisitor) }
    return edgeCollectorVisitor.edges
  }
}