// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.devkit.psiViewer

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.types.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import java.nio.file.Files
import java.nio.file.Path

internal class ClassLoadingLogAnalyzeAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val psiFile = e.getData(CommonDataKeys.PSI_FILE)
    e.presentation.isEnabledAndVisible = project != null && psiFile != null && psiFile.name.endsWith(".txt")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE)
    val path = psiFile.virtualFile.toNioPathOrNull() ?: return

    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        val parsedLog = parseLog(path)
        val result = analyze(project, parsedLog)

        val edgesCsv = result.edges
          .mapIndexed { index, edge ->
            "$index,${edge.from},${edge.to}"
          }
          .joinToString(prefix = "Id,Source,Target\n", separator = "\n")

        // https://gephi.org/features/
        Files.writeString(path.parent.resolve("edges.gephi.csv"), edgesCsv)

      }, JavaPsiViewerBundle.message(
      "dialog.title.analyzing.class.loading.log"), true, e.project)
  }
}

data class AnalysisResult(val edges: Collection<Edge>)

data class ClassPosition(val name: String, val index: Int)

data class Edge(val from: String, val to: String)

private val doNotAnalyzePackages: List<String> = listOf(
  "sun.",
  "jdk.",
  "java.",
  "javax.",
  "com.google.common.",
  "com.fasterxml.",
  "io.opentelemetry.",
  "kotlinx.serialization.",
  "kotlin.reflect.",
)

private fun parseLog(logPath: Path): Map<String, ClassPosition> {
  return readLog(logPath).asSequence()
    .filterNot { it.className.contains("LambdaForm\$") || it.className.contains("\$\$Lambda") }
    .filterNot { clazz -> doNotAnalyzePackages.any { clazz.className.startsWith(it) } }
    .mapIndexed { index, loadingEntry ->
      val className = loadingEntry.className.replace("\$", ".")
      className to ClassPosition(className, index)
    }
    .toMap()
}

private fun analyze(project: Project, classesLog: Map<String, ClassPosition>): AnalysisResult {
  val javaPsi = JavaPsiFacade.getInstance(project)
  val scope = GlobalSearchScope.allScope(project)

  val edges = linkedSetOf<Edge>()
  val application = ApplicationManager.getApplication()

  for (logClass in classesLog) {
    application.runReadAction {
      val psiClass = javaPsi.findClass(logClass.value.name, scope) ?: return@runReadAction
      visitClass(psiClass, edges, classesLog)
    }
  }

  return AnalysisResult(edges)
}

private fun visitClass(psiClass: PsiClass, edges: MutableCollection<Edge>, classesLog: Map<String, ClassPosition>) {
  val fromClass = psiClass.qualifiedName ?: return

  for (superType in psiClass.superTypes) {
    visitType(fromClass, superType, edges, classesLog)
  }

  for (field in psiClass.allFields) {
    visitField(fromClass, field, edges, classesLog)
  }

  for (psiMethod in psiClass.allMethods) {
    visitMethod(fromClass, psiMethod, edges, classesLog)
  }

  for (constructor in psiClass.constructors) {
    visitMethod(fromClass, constructor, edges, classesLog)
  }
}

private fun visitField(fromClass: String, psiField: PsiField, edges: MutableCollection<Edge>, classesLog: Map<String, ClassPosition>) {
  visitType(fromClass, psiField.type, edges, classesLog)
}

private fun visitMethod(fromClass: String, psiMethod: PsiMethod, edges: MutableCollection<Edge>, classesLog: Map<String, ClassPosition>) {
  for (psiParameter in psiMethod.parameters) {
    visitType(fromClass, psiParameter.type, edges, classesLog)
  }
  visitType(fromClass, psiMethod.returnType, edges, classesLog)
}

private fun visitType(fromClass: String, psiType: JvmType?, edges: MutableCollection<Edge>, classesLog: Map<String, ClassPosition>) {
  val fromClassEntry = classesLog[fromClass] ?: return

  psiType?.accept(object : JvmTypeVisitor<Unit> {
    override fun visitType(type: JvmType) {
    }

    override fun visitReferenceType(type: JvmReferenceType) {
      val resolvedClassName = (type.resolve() as? JvmClass)?.qualifiedName ?: return
      val referencedEntry = classesLog[resolvedClassName]

      if (referencedEntry != null) {
        if (referencedEntry.index > fromClassEntry.index) {
          edges.add(Edge(fromClass, resolvedClassName))
        }
      }
    }

    override fun visitPrimitiveType(type: JvmPrimitiveType) {
    }

    override fun visitArrayType(type: JvmArrayType) {
    }

    override fun visitWildcardType(type: JvmWildcardType) {
    }
  })
}

internal data class LoadingEntry(val className: String, val sizeBytes: Int)

private val linePattern: Regex = Regex("\\[([.\\d]+)]\\sLoading class:\\s(\\S+)\\s\\((\\d+)\\sbytes\\)")

internal fun parseLine(line: String): LoadingEntry? {
  val result = linePattern.matchEntire(line.replace(',', '.'))
  return result?.let {
    val classText = it.groupValues[2].replace("/", ".")
    val sizeBytes = it.groupValues[3].toInt()

    LoadingEntry(classText, sizeBytes)
  }
}

internal fun readLog(path: Path): List<LoadingEntry> {
  return Files.readAllLines(path)
    .mapNotNull(::parseLine)
}