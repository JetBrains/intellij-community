// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class HgDisposable(private val cs: CoroutineScope) : Disposable {
  override fun dispose() {
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): HgDisposable {
      return project.service()
    }

    @JvmStatic
    fun getCoroutineScope(project: Project): CoroutineScope {
      return getInstance(project).cs
    }
  }
}
