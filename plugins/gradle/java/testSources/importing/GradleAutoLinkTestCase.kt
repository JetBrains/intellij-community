// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.testFramework.openProjectAsync
import org.jetbrains.plugins.gradle.testFramework.util.openProjectAsyncAndWait

abstract class GradleAutoLinkTestCase : ExternalSystemTestCase() {

  override fun runInDispatchThread() = false

  override fun getTestsTempDir() = "tmp${System.currentTimeMillis()}"

  override fun getExternalSystemConfigFileName() = throw UnsupportedOperationException()

  suspend fun openProjectAsyncAndWait(virtualFile: VirtualFile): Project {
    return openProjectAsyncAndWait(virtualFile, UnlinkedProjectStartupActivity())
  }

  suspend fun openProjectAsync(virtualFile: VirtualFile): Project {
    return openProjectAsync(virtualFile, UnlinkedProjectStartupActivity())
  }
}