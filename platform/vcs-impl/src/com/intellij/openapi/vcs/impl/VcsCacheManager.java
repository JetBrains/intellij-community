// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.openapi.vcs.update.UpdatedFilesListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public class VcsCacheManager {
  private final VcsHistoryCache myVcsHistoryCache;
  private final ContentRevisionCache myContentRevisionCache;

  public static VcsCacheManager getInstance(Project project) {
    return project.getService(VcsCacheManager.class);
  }

  public VcsCacheManager(@NotNull Project project) {
    myVcsHistoryCache = new VcsHistoryCache();
    myContentRevisionCache = new ContentRevisionCache();

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, myVcsHistoryCache::clearHistory);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, myVcsHistoryCache::clearHistory);
    connection.subscribe(UpdatedFilesListener.UPDATED_FILES, myContentRevisionCache::clearCurrent);
  }

  public VcsHistoryCache getVcsHistoryCache() {
    return myVcsHistoryCache;
  }

  public ContentRevisionCache getContentRevisionCache() {
    return myContentRevisionCache;
  }
}
