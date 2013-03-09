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
package com.intellij.lang.ant.config;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author VISTALL
 * @since 12:26/09.03.13
 */
public abstract class AntBuildFileGroupManager {
  public static AntBuildFileGroupManager getInstance(final Project project) {
    return ServiceManager.getService(project, AntBuildFileGroupManager.class);
  }

  @NotNull
  public abstract AntBuildFileGroup createGroup(@Nullable AntBuildFileGroup parent, @NotNull String name);

  public abstract void moveToGroup(@NotNull AntBuildFile file, @Nullable AntBuildFileGroup group);

  public abstract AntBuildFile[] getFilesForGroup(@NotNull AntBuildFileGroup group);

  @NotNull
  public abstract AntBuildFileGroup[] getFirstLevelGroups();

  public abstract AntBuildFileGroup findGroup(@NotNull AntBuildFile buildFile);

  public abstract void removeGroup(@NotNull AntBuildFileGroup buildGroup);
}
