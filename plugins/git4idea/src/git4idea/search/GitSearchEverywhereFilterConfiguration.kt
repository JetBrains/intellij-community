// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.search

import com.intellij.ide.util.gotoByName.ChooseByNameFilterConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@Service
@State(name = "GitSEFilterConfiguration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GitSearchEverywhereFilterConfiguration(@Suppress("unused") private val project: Project)
  : ChooseByNameFilterConfiguration<GitSearchEverywhereItemType>() {

  init {
    setVisible(GitSearchEverywhereItemType.LOCAL_BRANCH, false)
    setVisible(GitSearchEverywhereItemType.REMOTE_BRANCH, false)
    setVisible(GitSearchEverywhereItemType.TAG, false)
    setVisible(GitSearchEverywhereItemType.COMMIT_BY_MESSAGE, false)
  }

  override fun nameForElement(type: GitSearchEverywhereItemType) = type.name
}
