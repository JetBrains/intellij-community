// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.model

import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.annotations.ApiStatus
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface TodoScope {
  @Serializable
  data object Project : TodoScope

  @Serializable
  data class CurrentFile(val fileId: VirtualFileId) : TodoScope

  @Serializable
  data class ChangeList(val fileIds: List<VirtualFileId>) : TodoScope

  @Serializable
  data class NamedScope(val scopeId: @NonNls String) : TodoScope
}

@ApiStatus.Internal
fun TodoScope.toSearchScope(project: Project): SearchScope? {
  return when (this) {
    TodoScope.Project -> null
    is TodoScope.CurrentFile -> GlobalSearchScope.fileScope(project, fileId.virtualFile())
    is TodoScope.ChangeList -> GlobalSearchScope.filesScope(project, fileIds.mapNotNull { it.virtualFile() })
    is TodoScope.NamedScope -> ScopesStateService.getInstance(project).getScopesState().getScopeDescriptorById(scopeId)?.scope
  }
}