// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.WatchRoots;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Maintains the VFS file watches for VCS mappings
 */
@ApiStatus.Internal
public class VcsMappingsFileWatchesManager {
  private final VcsMappingsWatchRootsModifier myModifier;
  private final Alarm myAlarm;

  public VcsMappingsFileWatchesManager(@NotNull Project project, @NotNull NewMappings newMappings) {
    this(project, newMappings, WatchRoots.getInstance());
  }

  public VcsMappingsFileWatchesManager(@NotNull Project project,
                                       @NotNull NewMappings newMappings,
                                       @NotNull WatchRoots watchRoots) {
    myModifier = new VcsMappingsWatchRootsModifier(project, newMappings, watchRoots);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, newMappings);
  }

  public void ping() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(myModifier, 0);
  }

  @TestOnly
  protected void pingImmediately() {
    myModifier.run();
  }
}
