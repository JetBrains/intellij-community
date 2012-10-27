/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.android.builder;

import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class AndroidProjectBuildTarget extends BuildTarget<BuildRootDescriptor> {
  public enum AndroidBuilderKind {DEX, PACKAGING}
  private final AndroidBuilderKind myKind;
  private final JpsModel myModel;

  public AndroidProjectBuildTarget(@NotNull AndroidBuilderKind kind, JpsModel model) {
    super(TargetType.INSTANCE);
    myKind = kind;
    myModel = model;
  }

  @Override
  public String getId() {
    return myKind.name();
  }

  public AndroidBuilderKind getKind() {
    return myKind;
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry) {
    List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();
    if (myKind == AndroidBuilderKind.PACKAGING) {
      result.add(new AndroidProjectBuildTarget(AndroidBuilderKind.DEX, myModel));
    }
    for (JpsModule module : myModel.getProject().getModules()) {
      JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension != null) {
        result.addAll(targetRegistry.getModuleBasedTargets(module, extension.isPackTestCode()? BuildTargetRegistry.ModuleTargetSelector.ALL : BuildTargetRegistry.ModuleTargetSelector.PRODUCTION));
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myKind == ((AndroidProjectBuildTarget)o).myKind;
  }

  @Override
  public int hashCode() {
    return myKind.hashCode();
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
    return "Android " + myKind.name();
  }

  @NotNull
  @Override
  public Collection<File> getOutputDirs(BuildDataPaths paths) {
    return Collections.emptyList();
  }

  public static class TargetType extends BuildTargetType<AndroidProjectBuildTarget> {
    public static final TargetType INSTANCE = new TargetType();

    public TargetType() {
      super(AndroidCommonUtils.PROJECT_BUILD_TARGET_TYPE_ID);
    }

    @NotNull
    @Override
    public List<AndroidProjectBuildTarget> computeAllTargets(@NotNull JpsModel model) {
      if (!AndroidJpsUtil.containsAndroidFacet(model.getProject())) {
        return Collections.emptyList();
      }
      return Arrays.asList(new AndroidProjectBuildTarget(AndroidBuilderKind.DEX, model),
                           new AndroidProjectBuildTarget(AndroidBuilderKind.PACKAGING, model));
    }

    @NotNull
    @Override
    public BuildTargetLoader<AndroidProjectBuildTarget> createLoader(@NotNull final JpsModel model) {
      return new BuildTargetLoader<AndroidProjectBuildTarget>() {
        @Nullable
        @Override
        public AndroidProjectBuildTarget createTarget(@NotNull String targetId) {
          return new AndroidProjectBuildTarget(AndroidBuilderKind.valueOf(targetId), model);
        }
      };
    }
  }
}
