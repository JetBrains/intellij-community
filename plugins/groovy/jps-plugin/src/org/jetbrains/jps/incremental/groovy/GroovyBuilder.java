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

 import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.java.ClassPostProcessor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.javac.OutputFileObject;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class GroovyBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovyBuilder");
  static final Key<Map<String, String>> STUB_TO_SRC = Key.create("STUB_TO_SRC");
  private static final Key<Boolean> FILES_MARKED_DIRTY_FOR_NEXT_ROUND = Key.create("SRC_MARKED_DIRTY");
  private static final String GROOVY_EXTENSION = "groovy";
  private final JpsGroovycRunner<JavaSourceRootDescriptor, ModuleBuildTarget> myHelper;
  private final boolean myForStubs;
  private final String myBuilderName;

  public GroovyBuilder(boolean forStubs) {
    super(forStubs ? BuilderCategory.SOURCE_GENERATOR : BuilderCategory.OVERWRITING_TRANSLATOR);
    myForStubs = forStubs;
    myBuilderName = "Groovy " + (forStubs ? "stub generator" : "compiler");
    myHelper = new CompilingGroovycRunner(forStubs);
  }

  static {
    JavaBuilder.registerClassPostProcessor(new RecompileStubSources());
  }

  public ModuleLevelBuilder.ExitCode build(final CompileContext context,
                                           final ModuleChunk chunk,
                                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                           OutputConsumer outputConsumer) throws ProjectBuildException {
    if (GreclipseBuilder.useGreclipse(context)) return ExitCode.NOTHING_DONE;

    try {
      ExitCode code = myHelper.doBuild(context, chunk, dirtyFilesHolder, this, new DefaultOutputConsumer(outputConsumer));
      if ((code == ExitCode.OK || code == ExitCode.NOTHING_DONE) && hasFilesToCompileForNextRound(context)) {
        return ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
      return code;
    }
    finally {
      if (!myForStubs) {
        FILES_MARKED_DIRTY_FOR_NEXT_ROUND.set(context, null);
      }
    }
  }

  private boolean hasFilesToCompileForNextRound(CompileContext context) {
    return !myForStubs && FILES_MARKED_DIRTY_FOR_NEXT_ROUND.get(context, Boolean.FALSE);
  }

  @Override
  public void buildStarted(CompileContext context) {
    if (myForStubs) {
      File stubRoot = getStubRoot(context);
      if (stubRoot.exists() && !FileUtil.deleteWithRenaming(stubRoot)) {
        context.processMessage(new CompilerMessage(myBuilderName, BuildMessage.Kind.ERROR, "External build cannot clean " + stubRoot.getPath()));
      }
    }
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
    JpsGroovycRunner.clearContinuation(context, chunk);
    STUB_TO_SRC.set(context, null);
  }

  static File getStubRoot(CompileContext context) {
    return new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), "groovyStubs");
  }

  @Nullable
  static Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk, Builder builder) {
    Map<ModuleBuildTarget, String> finalOutputs = new LinkedHashMap<ModuleBuildTarget, String>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File moduleOutputDir = target.getOutputDir();
      if (moduleOutputDir == null) {
        context.processMessage(new CompilerMessage(builder.getPresentableName(), BuildMessage.Kind.ERROR, "Output directory not specified for module " + target.getModule().getName()));
        return null;
      }
      //noinspection ResultOfMethodCallIgnored
      moduleOutputDir.mkdirs();
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
      assert moduleOutputPath != null;
      finalOutputs.put(target, moduleOutputPath.endsWith("/") ? moduleOutputPath : moduleOutputPath + "/");
    }
    return finalOutputs;
  }

  static JpsSdk<JpsDummyElement> getJdk(ModuleChunk chunk) {
    return chunk.getModules().iterator().next().getSdk(JpsJavaSdkType.INSTANCE);
  }

  static List<String> getGroovyRtRoots() {
    File rt = ClasspathBootstrap.getResourceFile(GroovyBuilder.class);
    File constants = ClasspathBootstrap.getResourceFile(GroovyRtConstants.class);
    return Arrays.asList(new File(rt.getParentFile(), rt.isFile() ? "groovy_rt.jar" : "groovy_rt").getPath(),
                         new File(constants.getParentFile(), constants.isFile() ? "groovy-rt-constants.jar" : "groovy-rt-constants").getPath());
  }

  public static boolean isGroovyFile(String path) {
    //todo file type check
    return path.endsWith("." + GROOVY_EXTENSION);
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.singletonList(GROOVY_EXTENSION);
  }

  @Override
  public String toString() {
    return myBuilderName;
  }

  @NotNull
  public String getPresentableName() {
    return myBuilderName;
  }

  private static class RecompileStubSources implements ClassPostProcessor {

    public void process(CompileContext context, OutputFileObject out) {
      Map<String, String> stubToSrc = STUB_TO_SRC.get(context);
      if (stubToSrc == null) {
        return;
      }
      File src = out.getSourceFile();
      if (src == null) {
        return;
      }
      String groovy = stubToSrc.get(FileUtil.toSystemIndependentName(src.getPath()));
      if (groovy == null) {
        return;
      }
      try {
        final File groovyFile = new File(groovy);
        if (!FSOperations.isMarkedDirty(context, CompilationRound.CURRENT, groovyFile)) {
          FSOperations.markDirty(context, CompilationRound.NEXT, groovyFile);
          FILES_MARKED_DIRTY_FOR_NEXT_ROUND.set(context, Boolean.TRUE);
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
