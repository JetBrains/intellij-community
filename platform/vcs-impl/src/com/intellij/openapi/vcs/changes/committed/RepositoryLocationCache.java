/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.*;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED;
import static com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED_IN_PLUGIN;
import static com.intellij.util.containers.ContainerUtil.newHashMap;
import static java.util.Collections.synchronizedMap;

public class RepositoryLocationCache {

  @NotNull private final Project myProject;
  @NotNull private final Map<Couple<String>, RepositoryLocation> myMap = synchronizedMap(newHashMap());

  public RepositoryLocationCache(@NotNull Project project) {
    myProject = project;

    MessageBusConnection connection = myProject.getMessageBus().connect();
    VcsListener listener = () -> reset();
    connection.subscribe(VCS_CONFIGURATION_CHANGED, listener);
    connection.subscribe(VCS_CONFIGURATION_CHANGED_IN_PLUGIN, listener);
  }

  @Nullable
  public RepositoryLocation getLocation(@NotNull AbstractVcs vcs, @NotNull FilePath filePath, boolean silent) {
    Couple<String> key = Couple.of(vcs.getName(), filePath.getPath());
    RepositoryLocation location = myMap.get(key);

    if (location == null) {
      location = getLocationUnderProgress(vcs, filePath, silent);
      myMap.put(key, location);
    }

    return location;
  }

  @Nullable
  private RepositoryLocation getLocationUnderProgress(@NotNull AbstractVcs vcs, @NotNull FilePath filePath, boolean silent) {
    ThrowableComputable<RepositoryLocation, RuntimeException> result = () -> {
      CommittedChangesProvider committedChangesProvider = vcs.getCommittedChangesProvider();
      return committedChangesProvider != null ? committedChangesProvider.getLocationFor(filePath) : null;
    };

    return !silent && ApplicationManager.getApplication().isDispatchThread()
           ? ProgressManager.getInstance()
             .runProcessWithProgressSynchronously(result, "Discovering location of " + filePath.getPresentableUrl(), true, myProject)
           : result.compute();
  }

  public void reset() {
    myMap.clear();
  }
}
