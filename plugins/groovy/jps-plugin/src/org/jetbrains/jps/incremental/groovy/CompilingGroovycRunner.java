// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.groovy.compiler.rt.OutputItem;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class CompilingGroovycRunner extends JpsGroovycRunner<JavaSourceRootDescriptor, ModuleBuildTarget> {
  CompilingGroovycRunner(boolean forStubs) {
    super(forStubs);
  }

  @Override
  protected Set<ModuleBuildTarget> getTargets(ModuleChunk chunk) {
    return chunk.getTargets();
  }

  @Override
  protected Map<ModuleBuildTarget, String> getCanonicalOutputs(CompileContext context, ModuleChunk chunk, Builder builder) {
    return GroovyBuilder.getCanonicalModuleOutputs(context, chunk, builder);
  }

  @Override
  protected JavaSourceRootDescriptor findRoot(CompileContext context, File srcFile) {
    return context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context, srcFile);
  }

  @Override
  protected void stubsGenerated(CompileContext context,
                                Map<ModuleBuildTarget, String> generationOutputs,
                                MultiMap<ModuleBuildTarget, OutputItem> compiled) {
    addStubRootsToJavacSourcePath(context, generationOutputs);
    rememberStubSources(context, compiled);
  }

  private static void addStubRootsToJavacSourcePath(CompileContext context, Map<ModuleBuildTarget, String> generationOutputs) {
    final BuildRootIndex rootsIndex = context.getProjectDescriptor().getBuildRootIndex();
    for (ModuleBuildTarget target : generationOutputs.keySet()) {
      File root = new File(generationOutputs.get(target));
      rootsIndex.associateTempRoot(context, target, JavaSourceRootDescriptor.createJavaSourceRootDescriptor(root, target, true, true, "", Set.of(), FileFilters.EVERYTHING));
    }
  }

  private static void rememberStubSources(CompileContext context, MultiMap<ModuleBuildTarget, OutputItem> compiled) {
    Map<String, String> stubToSrc = GroovyBuilder.STUB_TO_SRC.get(context);
    if (stubToSrc == null) {
      GroovyBuilder.STUB_TO_SRC.set(context, stubToSrc = new HashMap<>());
    }
    for (OutputItem item : compiled.values()) {
      stubToSrc.put(FileUtilRt.toSystemIndependentName(item.outputPath), item.sourcePath);
    }
  }

  @Override
  protected Map<ModuleBuildTarget, String> getGenerationOutputs(CompileContext context,
                                                                ModuleChunk chunk,
                                                                Map<ModuleBuildTarget, String> finalOutputs) throws IOException {
    if (!myForStubs) return super.getGenerationOutputs(context, chunk, finalOutputs);

    Map<ModuleBuildTarget, String> generationOutputs = new HashMap<>();
    Path commonRoot = GroovyBuilder.getStubRoot(context);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File targetRoot = commonRoot.resolve(target.getModule().getName() + File.separator + target.getTargetType().getTypeId()).toFile();
      if (targetRoot.exists() && !FileUtil.deleteWithRenaming(targetRoot)) {
        throw new IOException("External build cannot clean " + targetRoot.getPath());
      }
      if (!targetRoot.mkdirs()) {
        throw new IOException("External build cannot create " + targetRoot.getPath());
      }
      generationOutputs.put(target, targetRoot.getPath());
    }
    return generationOutputs;
  }
}
