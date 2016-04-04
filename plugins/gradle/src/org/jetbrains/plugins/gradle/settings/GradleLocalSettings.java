/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Denis Zhdanov
 * @since 5/3/12 6:16 PM
 */
@State(name = "GradleLocalSettings", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)} )
public class GradleLocalSettings extends AbstractExternalSystemLocalSettings
  implements PersistentStateComponent<AbstractExternalSystemLocalSettings.State>
{

  public GradleLocalSettings(@NotNull Project project) {
    super(GradleConstants.SYSTEM_ID, project);
  }

  @NotNull
  public static GradleLocalSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleLocalSettings.class);
  }

  @Nullable
  @Override
  public State getState() {
    State state = new State();
    fillState(state);
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    super.loadState(state); 
  }
}
