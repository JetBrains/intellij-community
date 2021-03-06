// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.util.*

interface GitStageUiSettings {
  fun ignoredFilesShown(): Boolean
  fun setIgnoredFilesShown(value: Boolean)
  fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable)
}

interface GitStageUiSettingsListener: EventListener {
  fun settingsChanged()
}

@State(name = "Git.Stage.Ui.Settings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GitStageUiSettingsImpl(val project: Project) : SimplePersistentStateComponent<GitStageUiSettingsImpl.State>(State()), GitStageUiSettings {
  private val eventDispatcher = EventDispatcher.create(GitStageUiSettingsListener::class.java)

  override fun ignoredFilesShown(): Boolean = state.ignoredFilesShown
  override fun setIgnoredFilesShown(value: Boolean) {
    state.ignoredFilesShown = value
    eventDispatcher.multicaster.settingsChanged()
  }

  override fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  class State : BaseState() {
    var ignoredFilesShown by property(false)
  }
}
