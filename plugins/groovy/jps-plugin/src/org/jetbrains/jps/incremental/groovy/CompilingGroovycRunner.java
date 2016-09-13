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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.Builder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
class CompilingGroovycRunner extends JpsGroovycRunner<JavaSourceRootDescriptor, ModuleBuildTarget> {
  public CompilingGroovycRunner(boolean forStubs) {
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
                                MultiMap<ModuleBuildTarget, GroovycOutputParser.OutputItem> compiled) {
    addStubRootsToJavacSourcePath(context, generationOutputs);
    rememberStubSources(context, compiled);
  }

  private static void addStubRootsToJavacSourcePath(CompileContext context, Map<ModuleBuildTarget, String> generationOutputs) {
    final BuildRootIndex rootsIndex = context.getProjectDescriptor().getBuildRootIndex();
    for (ModuleBuildTarget target : generationOutputs.keySet()) {
      File root = new File(generationOutputs.get(target));
      rootsIndex.associateTempRoot(context, target, new JavaSourceRootDescriptor(root, target, true, true, "", Collections.<File>emptySet()));
    }
  }

  private static void rememberStubSources(CompileContext context, MultiMap<ModuleBuildTarget, GroovycOutputParser.OutputItem> compiled) {
    Map<String, String> stubToSrc = GroovyBuilder.STUB_TO_SRC.get(context);
    if (stubToSrc == null) {
      GroovyBuilder.STUB_TO_SRC.set(context, stubToSrc = new HashMap<String, String>());
    }
    for (GroovycOutputParser.OutputItem item : compiled.values()) {
      stubToSrc.put(FileUtil.toSystemIndependentName(item.outputPath), item.sourcePath);
    }
  }

  @Override
  protected Map<ModuleBuildTarget, String> getGenerationOutputs(CompileContext context,
                                                                ModuleChunk chunk,
                                                                Map<ModuleBuildTarget, String> finalOutputs) throws IOException {
    if (!myForStubs) return super.getGenerationOutputs(context, chunk, finalOutputs);

    Map<ModuleBuildTarget, String> generationOutputs = new HashMap<ModuleBuildTarget, String>();
    File commonRoot = GroovyBuilder.getStubRoot(context);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File targetRoot = new File(commonRoot, target.getModule().getName() + File.separator + target.getTargetType().getTypeId());
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
