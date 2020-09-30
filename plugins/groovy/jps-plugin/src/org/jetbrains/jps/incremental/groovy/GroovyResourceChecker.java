// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    return GroovyJpsBundle.message("builder.resource.checker");
  }

  @Override
  public long getExpectedBuildTime() {
    return 50;
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

    ResourceCheckingGroovycRunner(CheckResourcesTarget target) {
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
    protected boolean checkChunkRebuildNeeded(CompileContext context, GroovyCompilerResult result) {
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
      List<File> resourceOutputs = new ArrayList<>();
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
