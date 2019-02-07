// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ImportOldConfigsUsagesCollector  {
  public static class Trigger implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
      final ImportOldConfigsState state = ImportOldConfigsState.getInstance();
      if (state.isOldConfigPanelWasOpened()) {
        FeatureUsageLogger.INSTANCE.log("statistics.import.old.config", state.getType().name());
      }
    }
  }

  public static class ImportOldConfigsState {
    private static final ImportOldConfigsState ourInstance = new ImportOldConfigsState();

    public static ImportOldConfigsState getInstance() {
      return ourInstance;
    }

    private volatile boolean myOldConfigPanelWasOpened = false;
    @NotNull
    private ImportOldConfigType myType = ImportOldConfigType.NOT_INITIALIZED;

    public void saveImportOldConfigType(@NotNull JRadioButton previous, @NotNull JRadioButton custom, @NotNull JRadioButton doNotImport) {
      myOldConfigPanelWasOpened = true;
      myType = getOldImportType(previous, custom, doNotImport);
    }

    @NotNull
    private static ImportOldConfigType getOldImportType(@NotNull JRadioButton previous,
                                                        @NotNull JRadioButton custom,
                                                        @NotNull JRadioButton doNotImport) {
      if (previous.isSelected()) return ImportOldConfigType.FROM_PREVIOUS;
      if (custom.isSelected()) return ImportOldConfigType.FROM_CUSTOM;
      if (doNotImport.isSelected()) return ImportOldConfigType.DO_NOT_IMPORT;
      return ImportOldConfigType.OTHER;
    }


    public boolean isOldConfigPanelWasOpened() {
      return myOldConfigPanelWasOpened;
    }

    @NotNull
    public ImportOldConfigType getType() {
      return myType;
    }
  }

  public enum ImportOldConfigType {
    FROM_PREVIOUS, FROM_CUSTOM, DO_NOT_IMPORT, OTHER, NOT_INITIALIZED
  }
}