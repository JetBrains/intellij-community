// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent
import org.jetbrains.plugins.github.test.GithubTest

abstract class GithubCreateGistContentTestBase : GithubTest() {

  override fun setUp() {
    super.setUp()
    createProjectFiles()
  }

  protected fun checkEquals(expected: List<FileContent>, actual: List<FileContent>) {
    assertTrue("Gist content differs from sample", Comparing.haveEqualElements(expected, actual))
  }

  protected fun collectContents(
    project: Project,
    editor: Editor?,
    file: VirtualFile?,
    files: Array<VirtualFile>?,
  ): List<FileContent> {
    return GithubGistContentsCollector.collectContents(project, editor, file, files)
  }
}
