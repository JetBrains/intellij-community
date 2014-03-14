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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "GlobalMessageService",
  storages = {
    @Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/vcs.xml", scheme = StorageScheme.DIRECTORY_BASED)
  })
public class VcsGlobalMessageManager implements ProjectComponent, PersistentStateComponent<VcsGlobalMessage> {
  private VcsGlobalMessage myState;

  public static VcsGlobalMessageManager getInstance(final Project project) {
      return project.getComponent(VcsGlobalMessageManager.class);
  }

  @Nullable
  @Override
  public VcsGlobalMessage getState() {
    return myState;
  }

  @Override
  public void loadState(VcsGlobalMessage state) {
    myState = state == null ? new VcsGlobalMessage() : state;
  }

  @Override
  public void projectOpened() {

  }

  @Override
  public void projectClosed() {

  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "VcsGlobalMessageManager";
  }
}
