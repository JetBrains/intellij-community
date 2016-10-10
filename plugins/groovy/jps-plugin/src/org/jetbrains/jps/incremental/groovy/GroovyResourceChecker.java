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

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author peter
 */
public class GroovyResourceChecker extends TargetBuilder<GroovyResourceRootDescriptor, CheckResourcesTarget> {
  public static final Key<Boolean> CHECKING_RESOURCES_REBUILD = Key.create("CHECKING_RESOURCES");

  public GroovyResourceChecker() {
    super(CheckResourcesTarget.TARGET_TYPES);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Groovy Resource Checker";
  }

  @Override
  public void build(@NotNull final CheckResourcesTarget target,
                    @NotNull DirtyFilesHolder<GroovyResourceRootDescriptor, CheckResourcesTarget> holder,
                    @NotNull final BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (context.getBuilderParameter(CHECKING_RESOURCES_REBUILD.toString()) == null) {
      return;
    }

    new ResourceCheckingGroovycRunner(target).doBuild(context, singleModuleChunk(target.getModule()), holder, this, new GroovyOutputConsumer() {
      @Override
      public void registerCompiledClass(BuildTarget<?> target, File srcFile, File outputFile, byte[] bytes) throws IOException {
        outputConsumer.registerOutputFile(outputFile, Collections.singleton(srcFile.getPath()));
      }
    });
  }

  @NotNull
  private static ModuleChunk singleModuleChunk(final JpsModule module) {
    return new ModuleChunk(Collections.singleton(new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION)));
  }

  private static class ResourceCheckingGroovycRunner extends JpsGroovycRunner<GroovyResourceRootDescriptor, CheckResourcesTarget> {

    private final CheckResourcesTarget myTarget;

    public ResourceCheckingGroovycRunner(CheckResourcesTarget target) {
      super(false);
      myTarget = target;
    }

    @Override
    protected Map<CheckResourcesTarget, String> getCanonicalOutputs(CompileContext context, ModuleChunk chunk, Builder builder) {
      return Collections.singletonMap(myTarget, myTarget.getOutputRoot(context).getPath());
    }

    @Override
    protected boolean shouldProcessSourceFile(File file,
                                              GroovyResourceRootDescriptor sourceRoot,
                                              String path,
                                              JpsJavaCompilerConfiguration configuration) {
      return GroovyBuilder.isGroovyFile(path) && !configuration.getValidationExcludes().isExcluded(file);
    }

    @Override
    protected boolean checkChunkRebuildNeeded(CompileContext context, GroovycOutputParser parser) {
      return false;
    }

    @Override
    protected Collection<String> generateClasspath(CompileContext context, ModuleChunk chunk) {
      Collection<String> paths = super.generateClasspath(context, chunk);
      for (File resourceOutput : getVisibleResourceOutputs(context, myTarget.isTests())) {
        paths.add(resourceOutput.getPath());
      }
      return paths;
    }

    @NotNull
    private List<File> getVisibleResourceOutputs(CompileContext context, boolean tests) {
      List<File> resourceOutputs = new ArrayList<File>();
      JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myTarget.getModule()).
        includedIn(JpsJavaClasspathKind.compile(tests)).
        recursively();
      for (JpsModule module : enumerator.getModules()) {
        resourceOutputs.addAll(new CheckResourcesTarget(module, CheckResourcesTarget.PRODUCTION).getOutputRoots(context));
        if (tests) {
          resourceOutputs.addAll(new CheckResourcesTarget(module, CheckResourcesTarget.TESTS).getOutputRoots(context));
        }
      }
      return resourceOutputs;
    }

    @Override
    protected GroovyResourceRootDescriptor findRoot(CompileContext context, File srcFile) {
      return context.getProjectDescriptor().getBuildRootIndex().findParentDescriptor(srcFile, CheckResourcesTarget.TARGET_TYPES, context);
    }

    @Override
    protected Set<CheckResourcesTarget> getTargets(ModuleChunk chunk) {
      return Collections.singleton(myTarget);
    }
  }
}
