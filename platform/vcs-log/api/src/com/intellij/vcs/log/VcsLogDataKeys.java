/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log;

import com.intellij.openapi.actionSystem.DataKey;

import java.util.List;

/**
 * Provides {@link DataKey DataKeys} which can be used by actions to access data available in the VCS log.
 */
public class VcsLogDataKeys {

  public static final DataKey<VcsLog> VCS_LOG = DataKey.create("Vcs.Log");
  public static final DataKey<VcsLogUi> VCS_LOG_UI = DataKey.create("Vcs.Log.Ui");
  public static final DataKey<VcsLogDataProvider> VCS_LOG_DATA_PROVIDER = DataKey.create("Vcs.Log.DataProvider");
  public static final DataKey<List<VcsRef>> VCS_LOG_BRANCHES = DataKey.create("Vcs.Log.Branches");
  public static final DataKey<List<VcsRef>> VCS_LOG_REFS = DataKey.create("Vcs.Log.Refs");
}
