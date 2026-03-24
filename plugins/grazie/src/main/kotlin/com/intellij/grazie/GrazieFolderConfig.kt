package com.intellij.grazie

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
  name = "GraziFolderConfig",
  presentableName = GrazieFolderConfig.PresentableNameGetter::class,
  storages = [Storage("grazie_folder.xml", roamingType = RoamingType.DISABLED)],
  exportable = true,
  additionalExportDirectory = "grazie"
)
internal class GrazieFolderConfig: PersistentStateComponent<GrazieFolderConfig.State> {

  object State

  override fun getState(): GrazieFolderConfig.State = GrazieFolderConfig.State
  override fun loadState(state: GrazieFolderConfig.State) {}

  class PresentableNameGetter : State.NameGetter() {
    override fun get() = GrazieBundle.message("grazie.config.folder.name")
  }
}
