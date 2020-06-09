// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.util.concurrent.UncheckedExecutionException
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile

class GitIndexVirtualFileCache(private val project: Project) : Disposable {
  private val cache = CacheBuilder.newBuilder().weakValues().build<Key, GitIndexVirtualFile>(CacheLoader.from { key ->
    GitIndexVirtualFile(project, key!!.root, key.filePath)
  })

  fun get(root: VirtualFile, filePath: FilePath): GitIndexVirtualFile {
    try {
      return cache.get(Key(root, filePath))
    } catch (e: UncheckedExecutionException) {
      val cause = e.cause
      if (cause is ProcessCanceledException) {
        throw cause
      }
      throw e
    }
  }

  fun filesMatching(function: (GitIndexVirtualFile) -> Boolean): List<GitIndexVirtualFile> {
    val result = mutableListOf<GitIndexVirtualFile>()
    cache.asMap().forEach { (_, file) -> if (function(file)) result.add(file) }
    return result
  }

  override fun dispose() {
    cache.invalidateAll()
  }

  private data class Key(val root: VirtualFile, val filePath: FilePath)
}