package com.intellij.cce.callGraphs

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

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
  return CallGraphNodeLocation(
    projectRootFilePath = relativePath,
    textRange = textRange.startOffset..textRange.endOffset
  )
}
