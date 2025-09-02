// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import org.jetbrains.jps.util.Iterators;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GroovyBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(GroovyBuilder.class);
  static final Key<Map<String, String>> STUB_TO_SRC = Key.create("STUB_TO_SRC");
  private static final Key<Boolean> FILES_MARKED_DIRTY_FOR_NEXT_ROUND = Key.create("SRC_MARKED_DIRTY");
  private static final String GROOVY_EXTENSION = "groovy";
  private final JpsGroovycRunner<JavaSourceRootDescriptor, ModuleBuildTarget> myHelper;
  private final boolean myForStubs;
  private final @Nls(capitalization = Nls.Capitalization.Sentence) String myBuilderName;

  public GroovyBuilder(boolean forStubs) {
    super(forStubs ? BuilderCategory.SOURCE_GENERATOR : BuilderCategory.OVERWRITING_TRANSLATOR);
    myForStubs = forStubs;
    myBuilderName = forStubs ? GroovyJpsBundle.message("builder.stub.generator")
                             : GroovyJpsBundle.message("builder.compiler");
    myHelper = new CompilingGroovycRunner(forStubs);
  }

  static {
    JavaBuilder.registerClassPostProcessor(new RecompileStubSources());
  }

  @Override
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
      Path stubRoot = getStubRoot(context);
      if (Files.exists(stubRoot) && !FileUtil.deleteWithRenaming(stubRoot)) {
        context.processMessage(new CompilerMessage(
          myBuilderName, BuildMessage.Kind.ERROR,
          GroovyJpsBundle.message("external.build.cannot.clean.path.0", stubRoot.toString())
        ));
      }
    }
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
    JpsGroovycRunner.clearContinuation(context, chunk);
    STUB_TO_SRC.set(context, null);
  }

  static @NotNull Path getStubRoot(CompileContext context) {
    return context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageDir().resolve("groovyStubs");
  }

  static @Nullable Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk, Builder builder) {
    Map<ModuleBuildTarget, String> finalOutputs = new LinkedHashMap<>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File moduleOutputDir = target.getOutputDir();
      if (moduleOutputDir == null) {
        context.processMessage(new CompilerMessage(
          builder.getPresentableName(), BuildMessage.Kind.ERROR,
          GroovyJpsBundle.message("no.output.0", target.getModule().getName())
        ));
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

  static List<String> getGroovyRtRoots(boolean addClassLoaderJar) {
    List<String> roots = GroovyRtJarPaths.getGroovyRtRoots(ClasspathBootstrap.getResourceFile(GroovyBuilder.class), addClassLoaderJar);
    if (addClassLoaderJar) {
      return ContainerUtil.append(roots, ClasspathBootstrap.getResourcePath(Ref.class)); // intellij.platform.util.rt
    }
    return roots;
  }

  public static boolean isGroovyFile(String path) {
    //todo file type check
    return path.endsWith("." + GROOVY_EXTENSION);
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return Collections.singletonList(GROOVY_EXTENSION);
  }

  @Override
  public String toString() {
    return myBuilderName;
  }

  @Override
  public @NotNull String getPresentableName() {
    return myBuilderName;
  }

  @Override
  public long getExpectedBuildTime() {
    return 100;
  }

  private static final class RecompileStubSources implements ClassPostProcessor {
    @Override
    public void process(CompileContext context, OutputFileObject out) {
      final Map<String, String> stubToSrc = STUB_TO_SRC.get(context);
      if (stubToSrc != null) {
        for (String groovy : Iterators.filter(Iterators.map(out.getSourceFiles(), file -> stubToSrc.get(FileUtil.toSystemIndependentName(file.getPath()))), Iterators.notNullFilter())) {
          try {
            Path groovyFile = Path.of(groovy);
            if (!FSOperations.isMarkedDirty(context, CompilationRound.CURRENT, groovyFile)) {
              FSOperations.markDirty(context, CompilationRound.NEXT, groovyFile.toFile());
              FILES_MARKED_DIRTY_FOR_NEXT_ROUND.set(context, Boolean.TRUE);
            }
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    }
  }
}
