// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.EventDispatcher
import java.util.*

interface GitStageUiSettings {
  var ignoredFilesShown: Boolean
  var isCommitAllEnabled: Boolean
  fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable)
}

interface GitStageUiSettingsListener : EventListener {
  fun settingsChanged()
}

class GitStageUiSettingsImpl(val project: Project) : GitStageUiSettings {
  private val projectSettings by lazy { project.service<GitStageUiProjectSettings>() }
  private val applicationSettings by lazy { service<GitStageUiApplicationSettings>() }

  override var ignoredFilesShown by projectSettings::ignoredFilesShown
  override var isCommitAllEnabled by applicationSettings::isCommitAllEnabled

  override fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable) {
    projectSettings.addListener(listener, disposable)
    applicationSettings.addListener(listener, disposable)
  }
}

@State(name = "Git.Stage.Ui.App.Settings", storages = [Storage("vcs.xml")], category = SettingsCategory.TOOLS)
class GitStageUiApplicationSettings : SimplePersistentStateComponent<GitStageUiApplicationSettings.State>(State()) {
  private val eventDispatcher = EventDispatcher.create(GitStageUiSettingsListener::class.java)

  internal var isCommitAllEnabled: Boolean
    get() = state.isCommitAllEnabled
    set(value) {
      state.isCommitAllEnabled = value
      eventDispatcher.multicaster.settingsChanged()
    }

  internal fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  override fun noStateLoaded() {
    val isCommitAllValue = Registry.get("git.stage.enable.commit.all")
    if (isCommitAllValue.isChangedFromDefault) {
      state.isCommitAllEnabled = isCommitAllValue.asBoolean()
      isCommitAllValue.resetToDefault()
    }
  }

  class State : BaseState() {
    var isCommitAllEnabled by property(true)
  }
}

@State(name = "Git.Stage.Ui.Settings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class GitStageUiProjectSettings(val project: Project) : SimplePersistentStateComponent<GitStageUiProjectSettings.State>(State()) {
  private val eventDispatcher = EventDispatcher.create(GitStageUiSettingsListener::class.java)

  internal var ignoredFilesShown: Boolean
    get() = state.ignoredFilesShown
    set(value) {
      state.ignoredFilesShown = value
      eventDispatcher.multicaster.settingsChanged()
    }

  internal fun addListener(listener: GitStageUiSettingsListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  class State : BaseState() {
    var ignoredFilesShown by property(false)
  }
}
