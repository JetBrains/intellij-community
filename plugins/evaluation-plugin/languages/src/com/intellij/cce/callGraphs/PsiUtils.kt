package com.intellij.cce.callGraphs

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

fun collectPsiFiles(project: Project, fileTypes: List<FileType>): List<PsiFile> {
  val psiManager = PsiManager.getInstance(project)
  val result = mutableListOf<PsiFile>()
  val index = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
  index.iterateContent { file ->
    if (!file.isDirectory && (file.fileType in fileTypes)) {
      psiManager.findFile(file)?.let { result.add(it) }
    }
    true
  }
  return result
}

fun PsiElement.getNodeLocation(): CallGraphNodeLocation {
  val vFile = containingFile.virtualFile
  val relativePath = VfsUtilCore.getRelativePath(vFile, project.guessProjectDir()!!, '/')!!
  return CallGraphNodeLocation(
    projectRootFilePath = relativePath,
    textRange = textRange.startOffset..textRange.endOffset
  )
}
