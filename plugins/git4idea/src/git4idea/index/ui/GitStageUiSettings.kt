// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import java.util.*

interface GitStageUiSettings {
  var ignoredFilesShown: Boolean
  fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable)
}

interface GitStageUiSettingsListener : EventListener {
  fun settingsChanged()
}

@State(name = "Git.Stage.Ui.Settings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GitStageUiSettingsImpl(val project: Project) : SimplePersistentStateComponent<GitStageUiSettingsImpl.State>(State()), GitStageUiSettings {
  private val eventDispatcher = EventDispatcher.create(GitStageUiSettingsListener::class.java)

  override var ignoredFilesShown: Boolean
    get() = state.ignoredFilesShown
    set(value) {
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
