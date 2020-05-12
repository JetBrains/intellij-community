package com.intellij.filePrediction

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VirtualFile

interface FilePredictionCandidateProvider {
  fun provideCandidates(project: Project, file: VirtualFile, refs: Set<VirtualFile>, limit: Int): Collection<VirtualFile>
}

internal object CompositeCandidateProvider : FilePredictionCandidateProvider {
  private val refProvider: FilePredictionCandidateProvider = FilePredictionReferenceProvider()
  private val neighborProvider: FilePredictionCandidateProvider = FilePredictionNeighborFilesProvider()

  override fun provideCandidates(project: Project, file: VirtualFile, refs: Set<VirtualFile>, limit: Int): Collection<VirtualFile> {
    val result = HashSet<VirtualFile>()
    result.addAll(refProvider.provideCandidates(project, file, refs, limit / 2))
    result.addAll(neighborProvider.provideCandidates(project, file, refs, limit - result.size))
    return result
  }
}

internal class FilePredictionReferenceProvider : FilePredictionCandidateProvider {
  override fun provideCandidates(project: Project, file: VirtualFile, refs: Set<VirtualFile>, limit: Int): Collection<VirtualFile> {
    if (refs.isEmpty()) {
      return emptySet()
    }

    val result = ArrayList<VirtualFile>()
    addWithLimit(refs.iterator(), result, file, limit)
    return result
  }
}

internal class FilePredictionNeighborFilesProvider : FilePredictionCandidateProvider {
  override fun provideCandidates(project: Project, file: VirtualFile, refs: Set<VirtualFile>, limit: Int): Collection<VirtualFile> {
    val result = ArrayList<VirtualFile>()
    val fileIndex = FileIndexFacade.getInstance(project)
    var parent = file.parent
    while (parent != null && parent.isDirectory && result.size < limit && fileIndex.isInProjectScope(parent)) {
      addWithLimit(parent.children.iterator(), result, file, limit)
      parent = parent.parent
    }
    return result
  }
}

private fun addWithLimit(from: Iterator<VirtualFile>, to: MutableList<VirtualFile>, skip: VirtualFile, limit: Int) {
  while (to.size < limit && from.hasNext()) {
    val next = from.next()
    if (!next.isDirectory && skip != next) {
      to.add(next)
    }
  }
}