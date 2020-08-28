// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.candidates

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.ApiStatus

private val EP_NAME = ExtensionPointName<FilePredictionCandidateProvider>("com.intellij.filePrediction.candidateProvider")

@ApiStatus.Internal
interface FilePredictionCandidateProvider {
  fun getWeight(): Int

  fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<FilePredictionCandidateFile>
}

internal abstract class FilePredictionBaseCandidateProvider(private val weight: Int) : FilePredictionCandidateProvider {
  override fun getWeight(): Int = weight

  internal fun addWithLimit(from: Iterator<VirtualFile>,
                            to: MutableSet<FilePredictionCandidateFile>,
                            source: FilePredictionCandidateSource,
                            skip: VirtualFile?, limit: Int) {
    while (to.size < limit && from.hasNext()) {
      val next = from.next()
      if (!next.isDirectory && skip != next && next !is LightVirtualFile) {
        to.add(FilePredictionCandidateFile(next, source))
      }
    }
  }
}

open class CompositeCandidateProvider : FilePredictionCandidateProvider {
  override fun getWeight(): Int {
    return 0
  }

  open fun getProviders() : List<FilePredictionCandidateProvider> {
    return EP_NAME.extensionList.sortedBy { it.getWeight() }
  }

  override fun provideCandidates(project: Project, file: VirtualFile?, refs: Set<VirtualFile>, limit: Int): Collection<FilePredictionCandidateFile> {
    val result = HashSet<FilePredictionCandidateFile>()
    val providers = getProviders()
    for ((index, provider) in providers.withIndex()) {
      val providerLimit = (limit - result.size) / (providers.size - index)
      result.addAll(provider.provideCandidates(project, file, refs, providerLimit))
    }
    return result
  }
}

data class FilePredictionCandidateFile(val file: VirtualFile, val source: FilePredictionCandidateSource) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FilePredictionCandidateFile

    if (file != other.file) return false

    return true
  }

  override fun hashCode(): Int {
    return file.hashCode()
  }
}