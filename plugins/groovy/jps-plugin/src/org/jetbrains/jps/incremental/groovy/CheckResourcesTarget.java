// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.ResourceRootDescriptor;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ResourcesTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class CheckResourcesTarget extends BuildTarget<GroovyResourceRootDescriptor> {
  private final @NotNull JpsModule myModule;

  CheckResourcesTarget(@NotNull JpsModule module, @NotNull Type targetType) {
    super(targetType);
    myModule = module;
  }

  @Override
  public @NotNull String getId() {
    return myModule.getName();
  }

  @Override
  public @Nullable GroovyResourceRootDescriptor findRootDescriptor(@NotNull String rootId, @NotNull BuildRootIndex rootIndex) {
    List<GroovyResourceRootDescriptor> descriptors = rootIndex.getRootDescriptors(new File(rootId),
                                                                                  Collections.singletonList((Type)getTargetType()),
                                                                                  null);
    return ContainerUtil.getFirstItem(descriptors);

  }

  boolean isTests() {
    return ((Type)getTargetType()).myTests;
  }

  @Override
  public @NotNull String getPresentableName() {
    return "Check Groovy Resources for '" + myModule.getName() + "' " + (isTests() ? "tests" : "production");
  }

  @Override
  public @NotNull Collection<BuildTarget<?>> computeDependencies(@NotNull BuildTargetRegistry targetRegistry, @NotNull TargetOutputIndex outputIndex) {
    List<BuildTarget<?>> result = new ArrayList<>();

    ModuleBuildTarget compileTarget = new ModuleBuildTarget(myModule, JavaModuleBuildTargetType.getInstance(isTests()));
    result.add(compileTarget);
    for (BuildTarget<?> dep : compileTarget.computeDependencies(targetRegistry, outputIndex)) {
      if (dep instanceof ModuleBuildTarget) {
        result.add(new CheckResourcesTarget(((ModuleBuildTarget)dep).getModule(),
                                            ((ModuleBuildTarget)dep).isTests() ? TESTS : PRODUCTION));
      }
    }
    return result;
  }

  @Override
  public @NotNull List<GroovyResourceRootDescriptor> computeRootDescriptors(@NotNull JpsModel model,
                                                                            @NotNull ModuleExcludeIndex index,
                                                                            @NotNull IgnoredFileIndex ignoredFileIndex,
                                                                            @NotNull BuildDataPaths dataPaths) {
    ResourcesTarget target = new ResourcesTarget(myModule, ResourcesTargetType.getInstance(isTests()));
    List<ResourceRootDescriptor> resources = target.computeRootDescriptors(model, index, ignoredFileIndex, dataPaths);
    return ContainerUtil.map(resources, descriptor -> new GroovyResourceRootDescriptor(descriptor, this));
  }

  @Override
  public @NotNull Collection<File> getOutputRoots(@NotNull CompileContext context) {
    return List.of(getOutputRoot(context).toFile());
  }

  @NotNull Path getOutputRoot(CompileContext context) {
    Path commonRoot = context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageDir().resolve("groovyResources");
    return commonRoot.resolve(myModule.getName() + File.separator + getTargetType().getTypeId());
  }

  public static final Type PRODUCTION = new Type(false);
  public static final Type TESTS = new Type(true);
  static final List<Type> TARGET_TYPES = Arrays.asList(PRODUCTION, TESTS);

  public @NotNull JpsModule getModule() {
    return myModule;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CheckResourcesTarget)) return false;

    CheckResourcesTarget target = (CheckResourcesTarget)o;

    if (!myModule.equals(target.myModule)) return false;
    if (!getTargetType().equals(target.getTargetType())) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myModule.hashCode() + 31 * getTargetType().hashCode();
  }

  public static class Type extends BuildTargetType<CheckResourcesTarget> {
    private final boolean myTests;

    protected Type(boolean tests) {
      super("groovy-check-resources" + (tests ? "_tests" : ""), true);
      myTests = tests;
    }

    @Override
    public @NotNull List<CheckResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
      return ContainerUtil.map(model.getProject().getModules(), module -> new CheckResourcesTarget(module, this));
    }

    @Override
    public @NotNull BuildTargetLoader<CheckResourcesTarget> createLoader(@NotNull JpsModel model) {
      return new BuildTargetLoader<CheckResourcesTarget>() {
        @Override
        public @Nullable CheckResourcesTarget createTarget(@NotNull String targetId) {
          JpsModule module = model.getProject().findModuleByName(targetId);
          return module != null ? new CheckResourcesTarget(module, Type.this) : null;
        }
      };
    }
  }
}
