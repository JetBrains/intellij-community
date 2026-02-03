package com.intellij.evaluationPlugin.languages.callGraphs

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.io.PrintStream

private fun isInProjectRoots(file: VirtualFile, projectRoots: List<String>, project: Project): Boolean {
  val projectDir = project.guessProjectDir() ?: return false
  return projectRoots.any { root ->
    projectDir.findFileByRelativePath(root)?.let { rootFile ->
      VfsUtilCore.isAncestor(rootFile, file, false)
    } ?: false
  }
}

fun collectPsiFiles(project: Project, fileTypes: List<FileType>, projectRoots: List<String>): List<PsiFile> {
  val psiManager = PsiManager.getInstance(project)
  val result = mutableListOf<PsiFile>()
  val index = ProjectFileIndex.getInstance(project)
  index.iterateContent { file ->
    if (!file.isDirectory && (file.fileType in fileTypes) && isInProjectRoots(file, projectRoots, project)) {
      psiManager.findFile(file)?.let { result.add(it) }
    }
    true
  }
  return result
}

fun PsiFile.getRelativePath(): String? {
  val vFile = virtualFile ?: return null
  val projectRoot = project.guessProjectDir() ?: return null
  return VfsUtilCore.getRelativePath(vFile, projectRoot, '/')
}

fun PsiElement.getNodeLocation(): CallGraphNodeLocation? {
  val relativePath = containingFile.getRelativePath() ?: return null
  val textRangeDefined = textRange ?: return null
  return CallGraphNodeLocation(
    projectRootFilePath = relativePath,
    textRange = textRangeDefined.startOffset..textRangeDefined.endOffset
  )
}

private fun supportsAnsi(): Boolean {
  val os = System.getProperty("os.name").lowercase()
  val term = System.getenv("TERM")?.lowercase().orEmpty()
  return !os.contains("win") || term.contains("xterm") || term.contains("ansi")
}

fun <T> Iterable<T>.forEachIndexedWithProgress(
  label: String = "Progress",
  barWidth: Int = 40,
  out: PrintStream = System.out,
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
