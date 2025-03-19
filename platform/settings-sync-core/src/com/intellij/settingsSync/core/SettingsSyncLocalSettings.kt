package com.intellij.settingsSync.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import org.jetbrains.annotations.TestOnly
import java.util.*

interface SettingsSyncLocalState {
  val applicationId: UUID
  var knownAndAppliedServerId: String?
  var isCrossIdeSyncEnabled: Boolean
  var userId: String?
  var providerCode: String?
}

@State(name = "SettingsSyncLocalSettings", storages = [Storage("settingsSyncLocal.xml", roamingType = RoamingType.DISABLED)])
@Service(Service.Level.APP)
class SettingsSyncLocalSettings : SimplePersistentStateComponent<SettingsSyncLocalSettings.State>(State()), SettingsSyncLocalState {

  companion object {
    fun getInstance(): SettingsSyncLocalSettings = ApplicationManager.getApplication().getService(SettingsSyncLocalSettings::class.java)
  }

  class State : BaseState() {
    var applicationId: String? by string(UUID.randomUUID().toString())
    var knownAndAppliedServerId: String? by string(null)
    var crossIdeSyncEnabled by property(false)
    var userId by string(null)
    var providerCode by string(null)

    @TestOnly
    internal fun reset() {
      applicationId = UUID.randomUUID().toString()
      knownAndAppliedServerId = null
      crossIdeSyncEnabled = false
      userId = null
      providerCode = null
    }
  }

  fun applyFromState(newState: SettingsSyncLocalState) {
    applicationId = newState.applicationId
    knownAndAppliedServerId = newState.knownAndAppliedServerId
    isCrossIdeSyncEnabled = newState.isCrossIdeSyncEnabled
    userId = newState.userId
    providerCode = newState.providerCode
  }

  override var applicationId: UUID
    get() = UUID.fromString(state.applicationId)
    set(value) {
      state.applicationId = value.toString()
    }

  override var knownAndAppliedServerId
    get() = state.knownAndAppliedServerId
    set(value) {
      state.knownAndAppliedServerId = value
    }

  override var isCrossIdeSyncEnabled: Boolean
    get() = state.crossIdeSyncEnabled
    set(value) {
      state.crossIdeSyncEnabled = value
    }

  override var userId: String?
    get() = state.userId
    set(value) {
      state.userId = value
    }

  override var providerCode: String?
    get() = state.providerCode
    set(value) {
      state.providerCode = value
    }
}

//  Temporary non-persistent form state akin to `SettingsSyncSettings`'s `SettingsSyncStateHolder`
class SettingsSyncLocalStateHolder(
  initState: SettingsSyncLocalSettings.State = SettingsSyncLocalSettings.State()
) : SettingsSyncLocalState {
  constructor(init: Boolean) : this(SettingsSyncLocalSettings.State().also { it.crossIdeSyncEnabled = init })

  @Volatile
  internal var state = initState

  override var applicationId: UUID
    get() = UUID.fromString(state.applicationId)
    set(value) {
      state.applicationId = value.toString()
    }

  override var knownAndAppliedServerId: String?
    get() = state.knownAndAppliedServerId
    set(value) {
      state.knownAndAppliedServerId = value
    }

  override var isCrossIdeSyncEnabled: Boolean
    get() = state.crossIdeSyncEnabled
    set(value) {
      state.crossIdeSyncEnabled = value
    }

  override var userId: String?
    get() = state.userId
    set(value) {
      state.userId = value
    }

  override var providerCode: String?
    get() = state.providerCode
    set(value) {
      state.providerCode = value
    }

}
