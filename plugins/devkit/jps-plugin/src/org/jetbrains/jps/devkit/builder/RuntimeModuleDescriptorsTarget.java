/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.builder;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class RuntimeModuleDescriptorsTarget extends BuildTarget<BuildRootDescriptor> {
  public static final MyTargetType TARGET_TYPE = new MyTargetType();
  private final JpsProject myProject;

  public RuntimeModuleDescriptorsTarget(@NotNull JpsProject project) {
    super(TARGET_TYPE);
    myProject = project;
  }

  @Override
  public String getId() {
    return "project";
  }

  @NotNull
  public JpsProject getProject() {
    return myProject;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "IntelliJ Runtime Module Descriptors";
  }

  private static boolean isIntellijPlatformProject(@NotNull JpsProject project) {
    //todo[nik] improve
    List<JpsModule> modules = project.getModules();
    for (JpsModule module : modules) {
      if (module.getName().equals("idea") || module.getName().equals("community-main")) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    File descriptorsOutputDir = RuntimeModuleDescriptorsGenerator.getDescriptorsDirectory(project);
    return ContainerUtil.createMaybeSingletonList(descriptorsOutputDir);
  }

  private static class MyTargetType extends BuildTargetType<RuntimeModuleDescriptorsTarget> implements ModuleInducedTargetType {
    public MyTargetType() {
      super("intellij-module-descriptors");
    }

    @NotNull
    @Override
    public List<RuntimeModuleDescriptorsTarget> computeAllTargets(@NotNull JpsModel model) {
      if (isIntellijPlatformProject(model.getProject())) {
        return Collections.singletonList(new RuntimeModuleDescriptorsTarget(model.getProject()));
      }
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public BuildTargetLoader<RuntimeModuleDescriptorsTarget> createLoader(@NotNull final JpsModel model) {
      return new BuildTargetLoader<RuntimeModuleDescriptorsTarget>() {
        @Nullable
        @Override
        public RuntimeModuleDescriptorsTarget createTarget(@NotNull String targetId) {
          if (isIntellijPlatformProject(model.getProject())) {
            return new RuntimeModuleDescriptorsTarget(model.getProject());
          }
          return null;
        }
      };
    }
  }
}
