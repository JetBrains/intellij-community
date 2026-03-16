// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.cache.TodoCacheManager
import com.intellij.psi.search.IndexPattern
import com.intellij.psi.search.IndexPatternProvider
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus

/**
 * Empty implementation of TodoCacheManager that doesn't access file indexes (for frontend).
 * The actual index-based implementation [IndexTodoCacheManagerImpl] runs on the backend.
 * TODO: update to use RPC to fetch TODOs from backend
 */
@ApiStatus.Internal
class EmptyTodoCacheManager(private val project: Project) : TodoCacheManager {
  
  override fun processFilesWithTodoItems(processor: Processor<in PsiFile>): Boolean {
    return true
  }

  override fun getTodoCount(file: VirtualFile, patternProvider: IndexPatternProvider): Int {
    return -1
  }

  override fun getTodoCount(file: VirtualFile, pattern: IndexPattern): Int {
    return -1
  }
}
