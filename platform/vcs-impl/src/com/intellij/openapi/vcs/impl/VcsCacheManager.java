// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class VcsCacheManager {
  private final VcsHistoryCache myVcsHistoryCache;
  private final ContentRevisionCache myContentRevisionCache;

  public static VcsCacheManager getInstance(@NotNull Project project) {
    return project.getService(VcsCacheManager.class);
  }

  public VcsCacheManager(@NotNull Project project) {
    myVcsHistoryCache = new VcsHistoryCache();
    myContentRevisionCache = new ContentRevisionCache();

    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, myVcsHistoryCache::clearAll);
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN, myVcsHistoryCache::clearAll);

    VcsEP.EP_NAME.addChangeListener(() -> {
      myVcsHistoryCache.clearAll();
      myContentRevisionCache.clearAll();
    }, project);
  }

  public VcsHistoryCache getVcsHistoryCache() {
    return myVcsHistoryCache;
  }

  public ContentRevisionCache getContentRevisionCache() {
    return myContentRevisionCache;
  }
}
