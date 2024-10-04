// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vcs.history.FileHistoryRefresherI;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface VcsInternalDataKeys {
  DataKey<FileHistoryRefresherI> FILE_HISTORY_REFRESHER = DataKey.create("FILE_HISTORY_REFRESHER");
}
