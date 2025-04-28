// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
class GitDisposable(
  @ApiStatus.Internal
  val coroutineScope: CoroutineScope,
) : Disposable {
  override fun dispose() {
  }

  fun childScope(name: String): CoroutineScope = coroutineScope.childScope(name)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitDisposable = project.service()
  }
}