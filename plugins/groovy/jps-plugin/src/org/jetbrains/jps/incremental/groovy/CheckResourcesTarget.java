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
package org.jetbrains.jps.incremental.groovy;

import com.intellij.util.Function;
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
import java.util.*;

/**
 * @author peter
 */
public class CheckResourcesTarget extends BuildTarget<GroovyResourceRootDescriptor> {
  @NotNull private final JpsModule myModule;

  CheckResourcesTarget(@NotNull JpsModule module, Type targetType) {
    super(targetType);
    myModule = module;
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Nullable
  @Override
  public GroovyResourceRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    List<GroovyResourceRootDescriptor> descriptors = rootIndex.getRootDescriptors(new File(rootId),
                                                                                  Collections.singletonList((Type)getTargetType()),
                                                                                  null);
    return ContainerUtil.getFirstItem(descriptors);

  }

  boolean isTests() {
    return ((Type)getTargetType()).myTests;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Check Groovy Resources for '" + myModule.getName() + "' " + (isTests() ? "tests" : "production");
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(BuildTargetRegistry targetRegistry, TargetOutputIndex outputIndex) {
    List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();

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

  @NotNull
  @Override
  public List<GroovyResourceRootDescriptor> computeRootDescriptors(JpsModel model,
                                                             ModuleExcludeIndex index,
                                                             IgnoredFileIndex ignoredFileIndex,
                                                             BuildDataPaths dataPaths) {
    ResourcesTarget target = new ResourcesTarget(myModule, ResourcesTargetType.getInstance(isTests()));
    List<ResourceRootDescriptor> resources = target.computeRootDescriptors(model, index, ignoredFileIndex, dataPaths);
    return ContainerUtil.map(resources, new Function<ResourceRootDescriptor, GroovyResourceRootDescriptor>() {
      @Override
      public GroovyResourceRootDescriptor fun(ResourceRootDescriptor descriptor) {
        return new GroovyResourceRootDescriptor(descriptor, CheckResourcesTarget.this);
      }
    });
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputRoot(context));
  }

  @NotNull
  File getOutputRoot(CompileContext context) {
    File commonRoot = new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), "groovyResources");
    return new File(commonRoot, myModule.getName() + File.separator + getTargetType().getTypeId());
  }

  public static final Type PRODUCTION = new Type(false);
  public static final Type TESTS = new Type(true);
  static final List<Type> TARGET_TYPES = Arrays.asList(PRODUCTION, TESTS);

  @NotNull
  public JpsModule getModule() {
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
      super("groovy-check-resources" + (tests ? "_tests" : ""));
      myTests = tests;
    }

    @NotNull
    @Override
    public List<CheckResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
      return ContainerUtil.map(model.getProject().getModules(), new Function<JpsModule, CheckResourcesTarget>() {
        @Override
        public CheckResourcesTarget fun(JpsModule module) {
          return new CheckResourcesTarget(module, Type.this);
        }
      });
    }

    @NotNull
    @Override
    public BuildTargetLoader<CheckResourcesTarget> createLoader(@NotNull JpsModel model) {
      final Map<String, JpsModule> modules = new HashMap<String, JpsModule>();
      for (JpsModule module : model.getProject().getModules()) {
        modules.put(module.getName(), module);
      }
      return new BuildTargetLoader<CheckResourcesTarget>() {
        @Nullable
        @Override
        public CheckResourcesTarget createTarget(@NotNull String targetId) {
          JpsModule module = modules.get(targetId);
          return module != null ? new CheckResourcesTarget(module, Type.this) : null;
        }
      };
    }
  }
}
