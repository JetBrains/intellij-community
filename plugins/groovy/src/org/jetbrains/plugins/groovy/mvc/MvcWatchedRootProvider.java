/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.WatchedRootsProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class MvcWatchedRootProvider implements WatchedRootsProvider {
  private final Project myProject;

  public MvcWatchedRootProvider(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public Set<String> getRootsToWatch() {
    return getRootsToWatch(myProject);
  }

  @NotNull
  public static Set<String> getRootsToWatch(Project project) {
    if (!project.isInitialized()) return Collections.emptySet();

    Set<String> result = null;

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final MvcFramework framework = MvcFramework.getInstance(module);
      if (framework == null) continue;

      if (result == null) result = new HashSet<>();

      File sdkWorkDir = framework.getCommonPluginsDir(module);
      if (sdkWorkDir != null) {
        result.add(sdkWorkDir.getAbsolutePath());
      }

      File globalPluginsDir = framework.getGlobalPluginsDir(module);
      if (globalPluginsDir != null) {
        result.add(globalPluginsDir.getAbsolutePath());
      }
    }

    return result == null ? Collections.<String>emptySet() : result;
  }

}
