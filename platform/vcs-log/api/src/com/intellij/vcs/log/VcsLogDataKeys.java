// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.actionSystem.DataKey;

import java.util.List;

/**
 * Provides {@link DataKey DataKeys} which can be used by actions to access data available in the VCS log.
 */
public final class VcsLogDataKeys {

  public static final DataKey<VcsLog> VCS_LOG = DataKey.create("Vcs.Log");
  public static final DataKey<VcsLogUi> VCS_LOG_UI = DataKey.create("Vcs.Log.Ui");
  public static final DataKey<VcsLogDataProvider> VCS_LOG_DATA_PROVIDER = DataKey.create("Vcs.Log.DataProvider");
  public static final DataKey<List<VcsRef>> VCS_LOG_BRANCHES = DataKey.create("Vcs.Log.Branches");
  public static final DataKey<List<VcsRef>> VCS_LOG_REFS = DataKey.create("Vcs.Log.Refs");
}
