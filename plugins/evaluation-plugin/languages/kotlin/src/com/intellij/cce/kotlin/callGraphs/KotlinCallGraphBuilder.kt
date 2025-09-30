package com.intellij.cce.kotlin.callGraphs

import com.intellij.cce.callGraphs.*
import com.intellij.cce.core.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid


private fun supportsAnsi(): Boolean {
  val os = System.getProperty("os.name").lowercase()
  val term = System.getenv("TERM")?.lowercase().orEmpty()
  return !os.contains("win") || term.contains("xterm") || term.contains("ansi")
}

fun <T> Iterable<T>.forEachIndexedWithProgress(
  label: String = "Progress",
  barWidth: Int = 40,
  out: java.io.PrintStream = System.out,
  action: (index: Int, item: T) -> Unit,
) {
  val data: List<T> = when (this) {
    is Collection<T> -> this as? List<T> ?: this.toList()
    else -> this.toList()
  }
  val total = data.size
  if (total == 0) return

  val ansi = supportsAnsi()
  fun render(i: Int) {
    val done = i + 1
    val pct = (done * 100.0 / total).toInt()
    if (ansi) {
      out.print("\u001B[2K\r")
    }
    else {
      out.print("\r")
    }
    val filled = (pct * barWidth / 100).coerceIn(0, barWidth)
    val bar = buildString {
      append('[')
      repeat(filled) { append('=') }
      if (filled < barWidth) append('>')
      repeat((barWidth - filled - 1).coerceAtLeast(0)) { append(' ') }
      append(']')
    }
    out.printf("%s %s %3d%% (%d/%d)", label, bar, pct, done, total)
    out.flush()
  }

  data.forEachIndexed { index, item ->
    action(index, item)
    render(index)
  }

  out.println()
}

class KotlinCallGraphBuilder : CallGraphBuilder {
  override val supportedLanguages: List<Language> = listOf(Language.KOTLIN)

  override fun build(project: Project, projectRoots: List<String>): CallGraph {
    val psiFiles = collectPsiFiles(project, listOf(KotlinFileType.INSTANCE), projectRoots)
    val nodes = collectNodes(psiFiles)
    val edges = collectEdges(nodes, psiFiles)
    return CallGraph(nodes, edges)
  }


  private fun collectNodes(psiFiles: List<PsiFile>): List<CallGraphNode> {
    val nodes = mutableListOf<CallGraphNode>()
    val visitor = object : KtTreeVisitorVoid() {
      override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val name = function.name ?: return
        nodes.add(
          CallGraphNode(
            address = function.getNodeLocation()!!,
            projectName = function.project.name,
            id = nodes.size.toString(),
            qualifiedName = name
          )
        )
      }
    }
    println("Collecting nodes from ${psiFiles.size} files...")
    psiFiles.forEachIndexedWithProgress { index, file ->
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

    psiFiles.forEachIndexedWithProgress { index, file ->
      file.accept(visitor)
    }
    return edges
  }
}
