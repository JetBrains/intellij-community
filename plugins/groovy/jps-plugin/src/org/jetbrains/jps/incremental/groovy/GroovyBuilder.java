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
package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.java.dependencyView.Mappings;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.ClassPostProcessor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.OutputFileObject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovyBuilder");
  private static final Key<Boolean> CHUNK_REBUILD_ORDERED = Key.create("CHUNK_REBUILD_ORDERED");
  private static final Key<Map<String, String>> STUB_TO_SRC = Key.create("STUB_TO_SRC");
  private static final Key<Boolean> FILES_MARKED_DIRTY_FOR_NEXT_ROUND = Key.create("SRC_MARKED_DIRTY");
  private static final String GROOVY_EXTENSION = "groovy";
  private static final String GPP_EXTENSION = "gpp";
  private final boolean myForStubs;
  private final String myBuilderName;

  public GroovyBuilder(boolean forStubs) {
    super(forStubs ? BuilderCategory.SOURCE_GENERATOR : BuilderCategory.OVERWRITING_TRANSLATOR);
    myForStubs = forStubs;
    myBuilderName = "Groovy " + (forStubs ? "stub generator" : "compiler");
  }

  static {
    JavaBuilder.registerClassPostProcessor(new RecompileStubSources());
  }

  public ModuleLevelBuilder.ExitCode build(final CompileContext context,
                                           ModuleChunk chunk,
                                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                           OutputConsumer outputConsumer) throws ProjectBuildException {
    try {
      JpsGroovySettings settings = JpsGroovySettings.getSettings(context.getProjectDescriptor().getProject());

      final List<File> toCompile = collectChangedFiles(context, dirtyFilesHolder, settings);
      if (toCompile.isEmpty()) {
        return hasFilesToCompileForNextRound(context) ? ExitCode.ADDITIONAL_PASS_REQUIRED : ExitCode.NOTHING_DONE;
      }
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("forStubs=" + myForStubs);
      }

      Map<ModuleBuildTarget, String> finalOutputs = getCanonicalModuleOutputs(context, chunk);
      if (finalOutputs == null) {
        return ExitCode.ABORT;
      }

      final Set<String> toCompilePaths = getPathsToCompile(toCompile);
      
      Map<String, String> class2Src = buildClassToSourceMap(chunk, context, toCompilePaths, finalOutputs);

      final String encoding = context.getProjectDescriptor().getEncodingConfiguration().getPreferredModuleChunkEncoding(chunk);
      List<String> patchers = new ArrayList<String>();

      for (GroovyBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(GroovyBuilderExtension.class)) {
        patchers.addAll(extension.getCompilationUnitPatchers(context, chunk));
      }

      Map<ModuleBuildTarget, String> generationOutputs = myForStubs ? getStubGenerationOutputs(chunk, context) : finalOutputs;
      String compilerOutput = generationOutputs.get(chunk.representativeTarget());

      String finalOutput = FileUtil.toSystemDependentName(finalOutputs.get(chunk.representativeTarget()));
      final File tempFile = GroovycOSProcessHandler.fillFileWithGroovycParameters(
        compilerOutput, toCompilePaths, finalOutput, class2Src, encoding, patchers
      );
      final GroovycOSProcessHandler handler = runGroovyc(context, chunk, tempFile, settings);

      Map<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>>
        compiled = processCompiledFiles(context, chunk, generationOutputs, compilerOutput, handler);

      if (checkChunkRebuildNeeded(context, handler)) {
        return ExitCode.CHUNK_REBUILD_REQUIRED;
      }

      if (myForStubs) {
        addStubRootsToJavacSourcePath(context, generationOutputs);
        rememberStubSources(context, compiled);
      }

      for (CompilerMessage message : handler.getCompilerMessages(chunk.representativeTarget().getModule().getName())) {
        context.processMessage(message);
      }

      if (!myForStubs && updateDependencies(context, chunk, dirtyFilesHolder, toCompile, compiled, outputConsumer)) {
        return ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
      return hasFilesToCompileForNextRound(context) ? ExitCode.ADDITIONAL_PASS_REQUIRED : ExitCode.OK;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
    finally {
      if (!myForStubs) {
        FILES_MARKED_DIRTY_FOR_NEXT_ROUND.set(context, null); 
      }
    }
  }

  private Boolean hasFilesToCompileForNextRound(CompileContext context) {
    return !myForStubs && FILES_MARKED_DIRTY_FOR_NEXT_ROUND.get(context, Boolean.FALSE);
  }

  private static Set<String> getPathsToCompile(List<File> toCompile) {
    final Set<String> toCompilePaths = new LinkedHashSet<String>();
    for (File file : toCompile) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Path to compile: " + file.getPath());
      }
      toCompilePaths.add(FileUtil.toSystemIndependentName(file.getPath()));
    }
    return toCompilePaths;
  }

  private GroovycOSProcessHandler runGroovyc(final CompileContext context,
                                             ModuleChunk chunk,
                                             File tempFile,
                                             final JpsGroovySettings settings) throws IOException {
    ArrayList<String> classpath = new ArrayList<String>(generateClasspath(context, chunk));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Groovyc classpath: " + classpath);
    }

    List<String> programParams = ContainerUtilRt.newArrayList(myForStubs ? "stubs" : "groovyc", tempFile.getPath());
    if (settings.invokeDynamic) {
      programParams.add("--indy");
    }

    List<String> vmParams = ContainerUtilRt.newArrayList();
    vmParams.add("-Xmx" + settings.heapSize + "m");
    vmParams.add("-Dfile.encoding=" + System.getProperty("file.encoding"));
    //vmParams.add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

    String grapeRoot = System.getProperty(GroovycOSProcessHandler.GRAPE_ROOT);
    if (grapeRoot != null) {
      vmParams.add("-D" + GroovycOSProcessHandler.GRAPE_ROOT + "=" + grapeRoot);
    }

    final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
      getJavaExecutable(chunk),
      "org.jetbrains.groovy.compiler.rt.GroovycRunner",
      Collections.<String>emptyList(), classpath,
      vmParams,
      programParams
    );

    final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(cmd));
    final Consumer<String> updater = new Consumer<String>() {
      public void consume(String s) {
        context.processMessage(new ProgressMessage(s));
      }
    };
    final GroovycOSProcessHandler handler = new GroovycOSProcessHandler(process, updater) {
      @Override
      protected Future<?> executeOnPooledThread(Runnable task) {
        return SharedThreadPool.getInstance().executeOnPooledThread(task);
      }
    };

    handler.startNotify();
    handler.waitFor();
    return handler;
  }

  private static boolean checkChunkRebuildNeeded(CompileContext context, GroovycOSProcessHandler handler) {
    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) || !handler.shouldRetry()) {
      return false;
    }

    if (CHUNK_REBUILD_ORDERED.get(context) != null) {
      CHUNK_REBUILD_ORDERED.set(context, null);
      return false;
    }

    CHUNK_REBUILD_ORDERED.set(context, Boolean.TRUE);
    LOG.info("Order chunk rebuild");
    return true;
  }

  private static void rememberStubSources(CompileContext context, Map<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>> compiled) {
    Map<String, String> stubToSrc = STUB_TO_SRC.get(context);
    if (stubToSrc == null) {
      STUB_TO_SRC.set(context, stubToSrc = new HashMap<String, String>());
    }
    for (Collection<GroovycOSProcessHandler.OutputItem> items : compiled.values()) {
      for (GroovycOSProcessHandler.OutputItem item : items) {
        stubToSrc.put(FileUtil.toSystemIndependentName(item.outputPath), item.sourcePath);
      }
    }
  }

  private static void addStubRootsToJavacSourcePath(CompileContext context, Map<ModuleBuildTarget, String> generationOutputs) {
    final BuildRootIndex rootsIndex = context.getProjectDescriptor().getBuildRootIndex();
    for (ModuleBuildTarget target : generationOutputs.keySet()) {
      File root = new File(generationOutputs.get(target));
      rootsIndex.associateTempRoot(context, target, new JavaSourceRootDescriptor(root, target, true, true, "", Collections.<File>emptySet()));
    }
  }

  private static Map<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>> processCompiledFiles(CompileContext context,
                                                                               ModuleChunk chunk,
                                                                               Map<ModuleBuildTarget, String> generationOutputs,
                                                                               String compilerOutput, GroovycOSProcessHandler handler)
    throws IOException {
    ProjectDescriptor pd = context.getProjectDescriptor();

    final Map<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>> compiled = new THashMap<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>>();
    for (final GroovycOSProcessHandler.OutputItem item : handler.getSuccessfullyCompiled()) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("compiled=" + item);
      }
      final JavaSourceRootDescriptor rd = pd.getBuildRootIndex().findJavaRootDescriptor(context, new File(item.sourcePath));
      if (rd != null) {
        final String outputPath = ensureCorrectOutput(chunk, item, generationOutputs, compilerOutput, rd.target);

        Collection<GroovycOSProcessHandler.OutputItem> items = compiled.get(rd.target);
        if (items == null) {
          items = new ArrayList<GroovycOSProcessHandler.OutputItem>();
          compiled.put(rd.target, items);
        }

        items.add(new GroovycOSProcessHandler.OutputItem(outputPath, item.sourcePath));
      }
      else {
        if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
          LOG.info("No java source root descriptor for th e item found =" + item);
        }
      }
    }
    return compiled;
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
    STUB_TO_SRC.set(context, null);
  }

  private static Map<ModuleBuildTarget, String> getStubGenerationOutputs(ModuleChunk chunk, CompileContext context) throws IOException {
    Map<ModuleBuildTarget, String> generationOutputs = new HashMap<ModuleBuildTarget, String>();
    File commonRoot = new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), "groovyStubs");
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File targetRoot = new File(commonRoot, target.getModule().getName() + File.separator + target.getTargetType().getTypeId());
      if (!FileUtil.delete(targetRoot)) {
        throw new IOException("External make cannot clean " + targetRoot.getPath());
      }
      if (!targetRoot.mkdirs()) {
        throw new IOException("External make cannot create " + targetRoot.getPath());
      }
      generationOutputs.put(target, targetRoot.getPath());
    }
    return generationOutputs;
  }

  @Nullable
  private Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk) {
    Map<ModuleBuildTarget, String> finalOutputs = new HashMap<ModuleBuildTarget, String>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File moduleOutputDir = target.getOutputDir();
      if (moduleOutputDir == null) {
        context.processMessage(new CompilerMessage(myBuilderName, BuildMessage.Kind.ERROR, "Output directory not specified for module " + target.getModule().getName()));
        return null;
      }
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
      assert moduleOutputPath != null;
      finalOutputs.put(target, moduleOutputPath.endsWith("/") ? moduleOutputPath : moduleOutputPath + "/");
    }
    return finalOutputs;
  }

  private static String ensureCorrectOutput(ModuleChunk chunk,
                                            GroovycOSProcessHandler.OutputItem item,
                                            Map<ModuleBuildTarget, String> generationOutputs,
                                            String compilerOutput,
                                            ModuleBuildTarget srcTarget) throws IOException {
    if (chunk.getModules().size() > 1 && !srcTarget.equals(chunk.representativeTarget())) {
      File output = new File(item.outputPath);

      //todo honor package prefixes
      File correctRoot = new File(generationOutputs.get(srcTarget));
      File correctOutput = new File(correctRoot, FileUtil.getRelativePath(new File(compilerOutput), output));

      FileUtil.rename(output, correctOutput);
      return correctOutput.getPath();
    }
    return item.outputPath;
  }

  private static String getJavaExecutable(ModuleChunk chunk) {
    JpsSdk<?> sdk = chunk.getModules().iterator().next().getSdk(JpsJavaSdkType.INSTANCE);
    if (sdk != null) {
      return JpsJavaSdkType.getJavaExecutable(sdk);
    }
    return SystemProperties.getJavaHome() + "/bin/java";
  }

  private List<File> collectChangedFiles(CompileContext context,
                                                DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder, final JpsGroovySettings settings) throws IOException {

    final JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
    assert configuration != null;

    final List<File> toCompile = new ArrayList<File>();
    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor sourceRoot) throws IOException {
        final String path = file.getPath();
        if (isGroovyFile(path) && !configuration.isResourceFile(file, sourceRoot.root)) { //todo file type check
          if (myForStubs && settings.isExcludedFromStubGeneration(file)) {
            return true;
          }

          toCompile.add(file);
        }
        return true;
      }
    });
    return toCompile;
  }

  private static boolean updateDependencies(CompileContext context,
                                            ModuleChunk chunk,
                                            DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                            List<File> toCompile,
                                            Map<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>> successfullyCompiled,
                                            OutputConsumer outputConsumer) throws IOException {
    final Mappings delta = context.getProjectDescriptor().dataManager.getMappings().createDelta();
    final List<File> successfullyCompiledFiles = new ArrayList<File>();
    if (!successfullyCompiled.isEmpty()) {

      final Callbacks.Backend callback = delta.getCallback();

      for (Map.Entry<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>> entry : successfullyCompiled.entrySet()) {
        final ModuleBuildTarget target = entry.getKey();
        final Collection<GroovycOSProcessHandler.OutputItem> compiled = entry.getValue();
        for (GroovycOSProcessHandler.OutputItem item : compiled) {
          final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
          final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
          final File outputFile = new File(outputPath);
          outputConsumer.registerOutputFile(target, outputFile, Collections.singleton(sourcePath));
          callback.associate(outputPath, sourcePath, new ClassReader(FileUtil.loadFileBytes(outputFile)));
          successfullyCompiledFiles.add(new File(sourcePath));
        }
      }
    }

    return JavaBuilderUtil.updateMappings(context, delta, dirtyFilesHolder, chunk, toCompile, successfullyCompiledFiles);
  }

  private static Collection<String> generateClasspath(CompileContext context, ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<String>();
    //groovy_rt.jar
    // IMPORTANT! must be the first in classpath
    cp.add(getGroovyRtRoot().getPath());

    for (File file : ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false)) {
      cp.add(FileUtil.toCanonicalPath(file.getPath()));
    }

    for (GroovyBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(GroovyBuilderExtension.class)) {
      cp.addAll(extension.getCompilationClassPath(context, chunk));
    }

    return cp;
  }

  private static File getGroovyRtRoot() {
    File root = ClasspathBootstrap.getResourceFile(GroovyBuilder.class);
    if (root.isFile()) {
      return new File(root.getParentFile(), "groovy_rt.jar");
    }
    return new File(root.getParentFile(), "groovy_rt");
  }

  public static boolean isGroovyFile(String path) {
    return path.endsWith("." + GROOVY_EXTENSION) || path.endsWith("." + GPP_EXTENSION);
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Arrays.asList(GROOVY_EXTENSION, GPP_EXTENSION);
  }

  private static Map<String, String> buildClassToSourceMap(ModuleChunk chunk, CompileContext context, Set<String> toCompilePaths, Map<ModuleBuildTarget, String> finalOutputs) throws IOException {
    final Map<String, String> class2Src = new HashMap<String, String>();
    JpsJavaCompilerConfiguration configuration = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(
      context.getProjectDescriptor().getProject());
    for (ModuleBuildTarget target : chunk.getTargets()) {
      String moduleOutputPath = finalOutputs.get(target);
      final SourceToOutputMapping srcToOut = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target);
      for (String src : srcToOut.getSources()) {
        if (!toCompilePaths.contains(src) && isGroovyFile(src) &&
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
        if (!FSOperations.isMarkedDirty(context, groovyFile)) {
          FSOperations.markDirty(context, groovyFile);
          FILES_MARKED_DIRTY_FOR_NEXT_ROUND.set(context, Boolean.TRUE);
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
