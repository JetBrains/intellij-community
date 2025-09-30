package com.intellij.cce.callGraphs

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

fun collectPsiFiles(project: Project, fileType: FileType): List<PsiFile> {
  val psiManager = PsiManager.getInstance(project)
  val result = mutableListOf<PsiFile>()
  val index = com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
  index.iterateContent { file ->
    if (!file.isDirectory && (file.fileType == fileType)) {
      psiManager.findFile(file)?.let { result.add(it) }
    }
    true
  }
  return result
}

fun PsiElement.getNodeLocation(): CallGraphNodeLocation {
  return CallGraphNodeLocation(
    projectRootFilePath = containingFile.virtualFile.path,
    textRange = textRange.startOffset..textRange.endOffset
  )
}
