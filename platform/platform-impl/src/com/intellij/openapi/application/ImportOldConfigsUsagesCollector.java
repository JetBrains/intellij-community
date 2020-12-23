// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.openapi.application.ImportOldConfigsUsagesCollector.ImportOldConfigsState.InitialImportScenario;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.internal.statistic.eventLog.events.EventFields.Boolean;
import static com.intellij.internal.statistic.eventLog.events.EventFields.Enum;

public class ImportOldConfigsUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup EVENT_GROUP = new EventLogGroup("import.old.config", 4);
  private static final EventId2<ImportOldConfigType, Boolean> IMPORT_DIALOG_SHOWN_EVENT =
    EVENT_GROUP.registerEvent("import.dialog.shown", Enum("selected", ImportOldConfigType.class), Boolean("config_folder_exists"));
  private static final EventId1<InitialImportScenario> INITIAL_IMPORT_SCENARIO =
    EVENT_GROUP.registerEvent("import.initially", Enum("initial_import_scenario", InitialImportScenario.class));

  @Override
  public EventLogGroup getGroup() {
    return EVENT_GROUP;
  }

  public static class Trigger implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
      ImportOldConfigsState state = ImportOldConfigsState.getInstance();
      InitialImportScenario initialImportScenario = state.getInitialImportScenario();
      if (initialImportScenario != null) {
        INITIAL_IMPORT_SCENARIO.log(initialImportScenario);
      }
      if (state.wasOldConfigPanelOpened()) {
        IMPORT_DIALOG_SHOWN_EVENT.log(state.getType(), state.doesSourceConfigFolderExist());
      }
    }
  }

  public static class ImportOldConfigsState {
    private static final ImportOldConfigsState ourInstance = new ImportOldConfigsState();

    public static @NotNull ImportOldConfigsState getInstance() {
      return ourInstance;
    }

    private volatile @Nullable InitialImportScenario myInitialImportScenario = null;
    private volatile boolean myOldConfigPanelWasOpened = false;
    private volatile boolean mySourceConfigFolderExists = false;
    private volatile @NotNull ImportOldConfigType myType = ImportOldConfigType.NOT_INITIALIZED;

    public enum InitialImportScenario {
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

    public void reportImportScenario(@NotNull InitialImportScenario strategy) {
      myInitialImportScenario = strategy;
    }

    private @Nullable InitialImportScenario getInitialImportScenario() {
      return myInitialImportScenario;
    }

    public void saveImportOldConfigType(@NotNull JRadioButton previous,
                                        @NotNull JRadioButton custom,
                                        @NotNull JRadioButton doNotImport,
                                        boolean configFolderExists) {
      myOldConfigPanelWasOpened = true;
      mySourceConfigFolderExists = configFolderExists;
      myType = getOldImportType(previous, custom, doNotImport);
    }

    private static @NotNull ImportOldConfigType getOldImportType(@NotNull JRadioButton previous,
                                                                 @NotNull JRadioButton custom,
                                                                 @NotNull JRadioButton doNotImport) {
      if (previous.isSelected()) return ImportOldConfigType.FROM_PREVIOUS;
      if (custom.isSelected()) return ImportOldConfigType.FROM_CUSTOM;
      if (doNotImport.isSelected()) return ImportOldConfigType.DO_NOT_IMPORT;
      return ImportOldConfigType.OTHER;
    }


    public boolean wasOldConfigPanelOpened() {
      return myOldConfigPanelWasOpened;
    }

    public boolean doesSourceConfigFolderExist() {
      return mySourceConfigFolderExists;
    }

    public @NotNull ImportOldConfigType getType() {
      return myType;
    }
  }

  public enum ImportOldConfigType {
    FROM_PREVIOUS, FROM_CUSTOM, DO_NOT_IMPORT, OTHER, NOT_INITIALIZED
  }
}