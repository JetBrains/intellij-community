/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.jps.incremental.groovy;


import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.JpsServiceManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author peter
 */
public abstract class JpsGroovycRunner<R extends BuildRootDescriptor, T extends BuildTarget<R>> {
  private static final int ourOptimizeThreshold = Integer.parseInt(System.getProperty("groovyc.optimized.class.loading.threshold", "10"));
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.JpsGroovycRunner");
  private static final Key<Boolean> CHUNK_REBUILD_ORDERED = Key.create("CHUNK_REBUILD_ORDERED");
  private static final Key<Map<ModuleChunk, GroovycContinuation>> CONTINUATIONS = Key.create("CONTINUATIONS");
  final boolean myForStubs;

  public JpsGroovycRunner(boolean forStubs) {
    myForStubs = forStubs;
  }

  @NotNull
  ExitCode doBuild(CompileContext context,
                   ModuleChunk chunk,
                   DirtyFilesHolder<R, T> dirtyFilesHolder,
                   Builder builder, GroovyOutputConsumer outputConsumer) throws ProjectBuildException {
    List<CompilerMessage> messages;
    long start = 0;
    try {
      JpsGroovySettings settings = JpsGroovySettings.getSettings(context.getProjectDescriptor().getProject());

      Ref<Boolean> hasStubExcludes = Ref.create(false);
      final List<File> toCompile = collectChangedFiles(context, dirtyFilesHolder, myForStubs, hasStubExcludes);
      if (toCompile.isEmpty()) {
        return ExitCode.NOTHING_DONE;
      }
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("forStubs=" + myForStubs);
      }

      Map<T, String> finalOutputs = getCanonicalOutputs(context, chunk, builder);
      if (finalOutputs == null) {
        return ExitCode.ABORT;
      }

      start = System.currentTimeMillis();

      Map<T, String> generationOutputs = getGenerationOutputs(context, chunk, finalOutputs);
      String compilerOutput = generationOutputs.get(representativeTarget(generationOutputs));

      GroovycOutputParser parser = runGroovycOrContinuation(context, chunk, settings, finalOutputs, compilerOutput, toCompile, hasStubExcludes.get());

      MultiMap<T, GroovycOutputParser.OutputItem> compiled = processCompiledFiles(context, chunk, generationOutputs, compilerOutput, parser.getSuccessfullyCompiled());

      if (checkChunkRebuildNeeded(context, parser)) {
        clearContinuation(context, chunk);
        return ExitCode.CHUNK_REBUILD_REQUIRED;
      }

      messages = parser.getCompilerMessages();
      for (CompilerMessage message : messages) {
        context.processMessage(message);
      }

      if (myForStubs) {
        stubsGenerated(context, generationOutputs, compiled);
      }
      else {
        updateDependencies(context, toCompile, compiled, outputConsumer, builder);
      }
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
    finally {
      if (start > 0 && LOG.isDebugEnabled()) {
        LOG.debug(builder.getPresentableName() + " took " + (System.currentTimeMillis() - start) + " on " + chunk.getName());
      }
    }

    if (ContainerUtil.exists(messages, message -> message.getKind() == BuildMessage.Kind.ERROR)) {
      throw new StopBuildException();
    }

    return ExitCode.OK;
  }

  protected void stubsGenerated(CompileContext context, Map<T, String> generationOutputs, MultiMap<T, GroovycOutputParser.OutputItem> compiled) {
  }

  protected Map<T, String> getGenerationOutputs(CompileContext context, ModuleChunk chunk, Map<T, String> finalOutputs) throws IOException {
    return finalOutputs;
  }

  protected abstract Map<T, String> getCanonicalOutputs(CompileContext context, ModuleChunk chunk, Builder builder);

  @NotNull
  private GroovycOutputParser runGroovycOrContinuation(CompileContext context,
                                                       ModuleChunk chunk,
                                                       JpsGroovySettings settings,
                                                       Map<T, String> finalOutputs,
                                                       String compilerOutput, List<File> toCompile, boolean hasStubExcludes) throws Exception {
    if (myForStubs) {
      clearContinuation(context, chunk);
    }

    GroovycContinuation continuation = takeContinuation(context, chunk);
    if (continuation != null) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("using continuation for " + chunk);
      }
      return continuation.continueCompilation();
    }

    final Set<String> toCompilePaths = getPathsToCompile(toCompile);

    JpsSdk<JpsDummyElement> jdk = GroovyBuilder.getJdk(chunk);
    int version = jdk != null ? JpsJavaSdkType.getJavaVersion(jdk) : JavaVersion.current().feature;
    boolean inProcess = shouldRunGroovycInProcess(version);
    boolean mayDependOnUtilJar = version >= 6;
    boolean optimizeClassLoading = !inProcess && mayDependOnUtilJar && ourOptimizeThreshold != 0 && toCompilePaths.size() >= ourOptimizeThreshold;

    Map<String, String> class2Src = buildClassToSourceMap(chunk, context, toCompilePaths, finalOutputs);

    final String encoding = context.getProjectDescriptor().getEncodingConfiguration().getPreferredModuleChunkEncoding(chunk);
    List<String> patchers = new ArrayList<>();

    for (GroovyBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(GroovyBuilderExtension.class)) {
      patchers.addAll(extension.getCompilationUnitPatchers(context, chunk));
    }


    Collection<String> classpath = generateClasspath(context, chunk);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Optimized class loading: " + optimizeClassLoading);
      LOG.debug("Groovyc classpath: " + classpath);
    }

    final File tempFile = GroovycOutputParser.fillFileWithGroovycParameters(
      compilerOutput, toCompilePaths, finalOutputs.values(), class2Src, encoding, patchers,
      optimizeClassLoading ? StringUtil.join(classpath, File.pathSeparator) : ""
    );
    GroovycFlavor groovyc =
      inProcess ? new InProcessGroovyc(finalOutputs.values(), hasStubExcludes) : new ForkedGroovyc(optimizeClassLoading, chunk);

    GroovycOutputParser parser = new GroovycOutputParser(chunk, context);

    continuation = groovyc.runGroovyc(classpath, myForStubs, settings, tempFile, parser);
    setContinuation(context, chunk, continuation);
    return parser;
  }

  private static boolean shouldRunGroovycInProcess(int jdkVersion) {
    String explicitProperty = System.getProperty("groovyc.in.process");
    return explicitProperty != null ? "true".equals(explicitProperty) : jdkVersion == JavaVersion.current().feature;
  }

  static void clearContinuation(CompileContext context, ModuleChunk chunk) {
    GroovycContinuation continuation = takeContinuation(context, chunk);
    if (continuation != null) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("clearing continuation for " + chunk);
      }
      continuation.buildAborted();
    }
  }

  @Nullable
  private static GroovycContinuation takeContinuation(CompileContext context, ModuleChunk chunk) {
    Map<ModuleChunk, GroovycContinuation> map = CONTINUATIONS.get(context);
    return map == null ? null : map.remove(chunk);
  }

  private static void setContinuation(CompileContext context, ModuleChunk chunk, @Nullable GroovycContinuation continuation) {
    clearContinuation(context, chunk);
    if (continuation != null) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("registering continuation for " + chunk);
      }

      Map<ModuleChunk, GroovycContinuation> map = CONTINUATIONS.get(context);
      if (map == null) CONTINUATIONS.set(context, map = ContainerUtil.newConcurrentMap());
      map.put(chunk, continuation);
    }
  }

  private static Set<String> getPathsToCompile(List<File> toCompile) {
    final Set<String> toCompilePaths = new LinkedHashSet<>();
    for (File file : toCompile) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Path to compile: " + file.getPath());
      }
      toCompilePaths.add(FileUtil.toSystemIndependentName(file.getPath()));
    }
    return toCompilePaths;
  }

  protected boolean checkChunkRebuildNeeded(CompileContext context, GroovycOutputParser parser) {
    if (CHUNK_REBUILD_ORDERED.get(context) != null) {
      if (!myForStubs) {
        CHUNK_REBUILD_ORDERED.set(context, null);
      }
      return false;
    }

    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) || !parser.shouldRetry()) {
      return false;
    }

    CHUNK_REBUILD_ORDERED.set(context, Boolean.TRUE);
    LOG.info("Order chunk rebuild");
    return true;
  }

  protected abstract R findRoot(CompileContext context, File srcFile);

  MultiMap<T, GroovycOutputParser.OutputItem> processCompiledFiles(CompileContext context,
                                                                   ModuleChunk chunk,
                                                                   Map<T, String> generationOutputs,
                                                                   String compilerOutput,
                                                                   List<GroovycOutputParser.OutputItem> successfullyCompiled)
    throws IOException {
    final MultiMap<T, GroovycOutputParser.OutputItem> compiled = MultiMap.createSet();
    for (final GroovycOutputParser.OutputItem item : successfullyCompiled) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("compiled=" + item);
      }
      R rd = findRoot(context, new File(item.sourcePath));
      if (rd != null) {
        //noinspection unchecked
        T target = (T)rd.getTarget();
        String outputPath = ensureCorrectOutput(chunk, item, generationOutputs, compilerOutput, target);
        compiled.putValue(target, new GroovycOutputParser.OutputItem(outputPath, item.sourcePath));
      }
      else {
        if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
          LOG.info("No java source root descriptor for the item found =" + item);
        }
      }
    }
    if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
      LOG.info("Chunk " + chunk + " compilation finished");
    }
    return compiled;
  }

  protected abstract Set<T> getTargets(ModuleChunk chunk);

  private String ensureCorrectOutput(ModuleChunk chunk,
                                            GroovycOutputParser.OutputItem item,
                                            Map<T, String> generationOutputs,
                                            String compilerOutput,
                                            @NotNull T srcTarget) throws IOException {
    if (chunk.getModules().size() > 1 && !srcTarget.equals(representativeTarget(generationOutputs))) {
      File output = new File(item.outputPath);

      String srcTargetOutput = generationOutputs.get(srcTarget);
      if (srcTargetOutput == null) {
        LOG.info("No output for " + srcTarget + "; outputs=" + generationOutputs + "; targets = " + getTargets(chunk));
        return item.outputPath;
      }

      //todo honor package prefixes
      File correctRoot = new File(srcTargetOutput);
      File correctOutput = new File(correctRoot, ObjectUtils.assertNotNull(FileUtil.getRelativePath(new File(compilerOutput), output)));

      FileUtil.rename(output, correctOutput);
      return correctOutput.getPath();
    }
    return item.outputPath;
  }

  private T representativeTarget(Map<T, String> generationOutputs) {
    return generationOutputs.keySet().iterator().next();
  }

  List<File> collectChangedFiles(CompileContext context,
                                 DirtyFilesHolder<R, T> dirtyFilesHolder,
                                 boolean forStubs, Ref<Boolean> hasExcludes)
    throws IOException {

    final JpsJavaCompilerConfiguration configuration =
      JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
    assert configuration != null;

    final JpsGroovySettings settings = JpsGroovySettings.getSettings(context.getProjectDescriptor().getProject());

    final List<File> toCompile = new ArrayList<>();
    dirtyFilesHolder.processDirtyFiles(new FileProcessor<R, T>() {
      public boolean apply(T target, File file, R sourceRoot) throws IOException {
        if (shouldProcessSourceFile(file, sourceRoot, file.getPath(), configuration)) {
          if (forStubs && settings.isExcludedFromStubGeneration(file)) {
            hasExcludes.set(true);
            return true;
          }

          toCompile.add(file);
        }
        return true;
      }
    });
    return toCompile;
  }

  protected boolean shouldProcessSourceFile(File file,
                                            R sourceRoot,
                                            String path,
                                            JpsJavaCompilerConfiguration configuration) {
    return acceptsFileType(path) && !configuration.isResourceFile(file, sourceRoot.getRootFile());
  }

  protected boolean acceptsFileType(String path) {
    return GroovyBuilder.isGroovyFile(path);
  }

  void updateDependencies(CompileContext context,
                          List<File> toCompile,
                          MultiMap<T, GroovycOutputParser.OutputItem> successfullyCompiled,
                          final GroovyOutputConsumer outputConsumer, Builder builder) throws IOException {
    JavaBuilderUtil.registerFilesToCompile(context, toCompile);
    if (!successfullyCompiled.isEmpty()) {

      final Callbacks.Backend callback = JavaBuilderUtil.getDependenciesRegistrar(context);

      for (Map.Entry<T, Collection<GroovycOutputParser.OutputItem>> entry : successfullyCompiled.entrySet()) {
        final T target = entry.getKey();
        final Collection<GroovycOutputParser.OutputItem> compiled = entry.getValue();
        for (GroovycOutputParser.OutputItem item : compiled) {
          final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
          final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
          final File outputFile = new File(outputPath);
          final File srcFile = new File(sourcePath);
          try {
            final byte[] bytes = FileUtil.loadFileBytes(outputFile);
            if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
              LOG.info("registerCompiledClass " + outputFile + " from " + srcFile);
            }
            outputConsumer.registerCompiledClass(target, srcFile, outputFile, bytes);
            callback.associate(outputPath, sourcePath, new FailSafeClassReader(bytes));
          }
          catch (Throwable e) {
            // need this to make sure that unexpected errors in, for example, ASM will not ruin the compilation
            final String message = "Class dependency information may be incomplete! Error parsing generated class " + item.outputPath;
            LOG.info(message, e);
            context.processMessage(new CompilerMessage(
              builder.getPresentableName(), BuildMessage.Kind.WARNING, message + "\n" + CompilerMessage.getTextFromThrowable(e), sourcePath)
            );
          }
          JavaBuilderUtil.registerSuccessfullyCompiled(context, srcFile);
        }
      }
    }
  }

  protected Collection<String> generateClasspath(CompileContext context, ModuleChunk chunk) {
    //groovy_rt.jar
    // IMPORTANT! must be the first in classpath
    final Set<String> cp = new LinkedHashSet<>(GroovyBuilder.getGroovyRtRoots());

    for (File file : ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false)) {
      cp.add(FileUtil.toCanonicalPath(file.getPath()));
    }

    for (GroovyBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(GroovyBuilderExtension.class)) {
      cp.addAll(extension.getCompilationClassPath(context, chunk));
    }

    return cp;
  }

  private Map<String, String> buildClassToSourceMap(ModuleChunk chunk, CompileContext context, Set<String> toCompilePaths, Map<T, String> finalOutputs) throws IOException {
    final Map<String, String> class2Src = new HashMap<>();
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
      context.getProjectDescriptor().getProject());
    for (T target : getTargets(chunk)) {
      String moduleOutputPath = finalOutputs.get(target);
      final SourceToOutputMapping srcToOut = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
      for (String src : srcToOut.getSources()) {
        if (!toCompilePaths.contains(src) && GroovyBuilder.isGroovyFile(src) &&
            !configuration.getCompilerExcludes().isExcluded(new File(src))) {
          final Collection<String> outs = srcToOut.getOutputs(src);
          if (outs != null) {
            for (String out : outs) {
              if (out.endsWith(".class") && out.startsWith(moduleOutputPath)) {
                final String className = out.substring(moduleOutputPath.length(), out.length() - ".class".length()).replace('/', '.');
                class2Src.put(className, src);
              }
            }
          }
        }
      }
    }
    return class2Src;
  }

}
