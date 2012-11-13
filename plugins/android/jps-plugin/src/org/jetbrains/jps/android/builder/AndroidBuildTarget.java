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
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class AndroidBuildTarget extends ModuleBasedTarget<BuildRootDescriptor> {
  private final TargetType myTargetType;

  public AndroidBuildTarget(@NotNull TargetType targetType, @NotNull JpsModule module) {
    super(targetType, module);
    myTargetType = targetType;
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(final BuildTargetRegistry registry) {
    final List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>(3);

    if (myTargetType == TargetType.PACKAGING) {
      result.add(new AndroidBuildTarget(TargetType.DEX, myModule));
    }
    result.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.PRODUCTION));

    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    if (extension != null && extension.isPackTestCode()) {
      result.add(new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.TEST));
    }
    return result;
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
    return "Android " + myTargetType.getPresentableName();
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.emptyList();
  }

  @Override
  public boolean isTests() {
    return false;
  }

  public static class TargetType extends BuildTargetType<AndroidBuildTarget> {
    public static final TargetType DEX = new TargetType(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, "DEX");
    public static final TargetType PACKAGING = new TargetType(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, "Packaging");

    private final String myPresentableName;

    private TargetType(@NotNull String typeId, @NotNull String presentableName) {
      super(typeId);
      myPresentableName = presentableName;
    }

    @NotNull
    public String getPresentableName() {
      return myPresentableName;
    }

    @NotNull
    @Override
    public List<AndroidBuildTarget> computeAllTargets(@NotNull JpsModel model) {
      if (!AndroidJpsUtil.containsAndroidFacet(model.getProject())) {
        return Collections.emptyList();
      }
      final List<AndroidBuildTarget> targets = new ArrayList<AndroidBuildTarget>();

      for (JpsModule module : model.getProject().getModules()) {
        final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

        if (extension != null) {
          targets.add(new AndroidBuildTarget(this, module));
        }
      }
      return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<AndroidBuildTarget> createLoader(@NotNull final JpsModel model) {
      final HashMap<String, AndroidBuildTarget> targetMap = new HashMap<String, AndroidBuildTarget>();

      for (AndroidBuildTarget target : computeAllTargets(model)) {
        targetMap.put(target.getId(), target);
      }
      return new BuildTargetLoader<AndroidBuildTarget>() {
        @Nullable
        @Override
        public AndroidBuildTarget createTarget(@NotNull String targetId) {
          return targetMap.get(targetId);
        }
      };
    }
  }
}
