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
package org.jetbrains.plugins.gradle.model.impl;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModelBuilderService;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class ModuleExtendedModelBuilderImpl implements ModelBuilderService {

  @Override
  public boolean canBuild(String modelName) {
    return ModuleExtendedModel.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(String modelName, Project project) {

    final String moduleName = project.getName();
    final String moduleGroup = project.getGroup().toString();
    final String moduleVersion = project.getVersion().toString();

    final ModuleExtendedModelImpl moduleVersionModel = new ModuleExtendedModelImpl(moduleName, moduleGroup, moduleVersion);

    final List<File> artifacts = new ArrayList<File>();
    for (Task task : project.getTasks()) {
      if (task instanceof Jar) {
        Jar jar = (Jar)task;
        artifacts.add(jar.getArchivePath());
      }
    }

    moduleVersionModel.setArtifacts(artifacts);
    return moduleVersionModel;
  }
}
