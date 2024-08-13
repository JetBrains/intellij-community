// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.ApplicationActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import javax.swing.JRadioButton

internal object ImportOldConfigsUsagesCollector : CounterUsagesCollector() {
  private val EVENT_GROUP = EventLogGroup("import.old.config", 4)
  private val IMPORT_DIALOG_SHOWN_EVENT = EVENT_GROUP.registerEvent("import.dialog.shown",
                                                                    Enum("selected", ImportOldConfigType::class.java),
                                                                    Boolean("config_folder_exists"))
  private val INITIAL_IMPORT_SCENARIO = EVENT_GROUP.registerEvent("import.initially",
                                                                  Enum("initial_import_scenario", ImportOldConfigsState.InitialImportScenario::class.java))

  override fun getGroup(): EventLogGroup = EVENT_GROUP

  internal class Trigger : ApplicationActivity {
    override suspend fun execute() {
      val state = ImportOldConfigsState.getInstance()
      val initialImportScenario = state.initialImportScenario
      if (initialImportScenario != null) {
        INITIAL_IMPORT_SCENARIO.log(initialImportScenario)
      }
      if (state.wasOldConfigPanelOpened()) {
        IMPORT_DIALOG_SHOWN_EVENT.log(state.type, state.doesSourceConfigFolderExist())
      }
    }
  }

  enum class ImportOldConfigType {
    FROM_PREVIOUS,
    FROM_CUSTOM,
    DO_NOT_IMPORT,
    OTHER,
    NOT_INITIALIZED
  }
}

internal class ImportOldConfigsState {
  @Volatile
  var initialImportScenario: InitialImportScenario? = null
    private set

  @Volatile
  private var oldConfigPanelWasOpened = false

  @Volatile
  private var sourceConfigFolderExists = false

  @Volatile
  var type: ImportOldConfigsUsagesCollector.ImportOldConfigType = ImportOldConfigsUsagesCollector.ImportOldConfigType.NOT_INITIALIZED
    private set

  companion object {
    private val _instance = ImportOldConfigsState()
    fun getInstance(): ImportOldConfigsState = _instance

    private fun getOldImportType(previous: JRadioButton,
                                 custom: JRadioButton,
                                 doNotImport: JRadioButton): ImportOldConfigsUsagesCollector.ImportOldConfigType {
      if (previous.isSelected) {
        return ImportOldConfigsUsagesCollector.ImportOldConfigType.FROM_PREVIOUS
      }
      if (custom.isSelected) {
        return ImportOldConfigsUsagesCollector.ImportOldConfigType.FROM_CUSTOM
      }
      return if (doNotImport.isSelected) ImportOldConfigsUsagesCollector.ImportOldConfigType.DO_NOT_IMPORT else ImportOldConfigsUsagesCollector.ImportOldConfigType.OTHER
    }
  }

  enum class InitialImportScenario {
    CLEAN_CONFIGS,
    IMPORTED_FROM_PREVIOUS_VERSION,
    IMPORTED_FROM_OTHER_PRODUCT,
    IMPORTED_FROM_CLOUD,
    CONFIG_DIRECTORY_NOT_FOUND,
    SHOW_DIALOG_NO_CONFIGS_FOUND,
    SHOW_DIALOG_CONFIGS_ARE_TOO_OLD,
    SHOW_DIALOG_REQUESTED_BY_PROPERTY,
    IMPORT_SETTINGS_ACTION,
    RESTORE_DEFAULT_ACTION
  }

  fun reportImportScenario(strategy: InitialImportScenario) {
    initialImportScenario = strategy
  }

  fun saveImportOldConfigType(previous: JRadioButton,
                              custom: JRadioButton,
                              doNotImport: JRadioButton,
                              configFolderExists: Boolean) {
    oldConfigPanelWasOpened = true
    sourceConfigFolderExists = configFolderExists
    type = getOldImportType(previous, custom, doNotImport)
  }

  fun wasOldConfigPanelOpened(): Boolean = oldConfigPanelWasOpened

  fun doesSourceConfigFolderExist(): Boolean = sourceConfigFolderExists
}
