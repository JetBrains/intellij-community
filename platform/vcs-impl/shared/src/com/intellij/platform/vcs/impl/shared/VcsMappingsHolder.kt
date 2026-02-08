// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.util.paths.FilePathMapping
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsApi
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface VcsMappingsHolder {
  fun getRootFor(filePath: FilePath): FilePath?

  fun getRepositoryIdFor(filePath: FilePath): RepositoryId?

  fun getAllRoots(): List<FilePath>

  fun hasMultipleRoots(): Boolean

  companion object {
    fun getInstance(project: Project): VcsMappingsHolder = project.service<VcsMappingsHolderImpl>()
  }
}

@Service(Service.Level.PROJECT)
private class VcsMappingsHolderImpl(val project: Project, cs: CoroutineScope) : VcsMappingsHolder {
  private val mappings: StateFlow<FilePathMapping<VcsMappedRoot>> = flow {
    emitAll(VcsMappingsApi.getInstance().getMappings(project.projectId()).map { it.convertMapping() })
  }.stateIn(cs, SharingStarted.Eagerly, FilePathMapping(false))

  override fun getRootFor(filePath: FilePath): FilePath? = getMappingFor(filePath)?.path

  override fun getRepositoryIdFor(filePath: FilePath): RepositoryId? {
    return getMappingFor(filePath)?.repositoryId(project)
  }

  override fun getAllRoots(): List<FilePath> = mappings.value.values().map { it.path }

  override fun hasMultipleRoots(): Boolean = mappings.value.values().filter { it.vcs != null }.size > 1

  private fun getMappingFor(filePath: FilePath): VcsMappedRoot? = mappings.value.getMappingFor(filePath.path)

  private fun VcsMappingsDto.convertMapping(): FilePathMapping<VcsMappedRoot> {
    val resultMapping = FilePathMapping<VcsMappedRoot>(CaseSensitivityInfoHolder.caseSensitive)
    mappings.forEach { mapping ->
      val filePath = mapping.root.filePath
      resultMapping.add(filePath.path, VcsMappedRoot(filePath, mapping.vcs))
    }
    return resultMapping
  }
}

private data class VcsMappedRoot(
  val path: FilePath,
  val vcs: VcsKey?,
) {
  fun repositoryId(project: Project): RepositoryId? =
    if (vcs != null) RepositoryId.from(project.projectId(), path) else null
}
