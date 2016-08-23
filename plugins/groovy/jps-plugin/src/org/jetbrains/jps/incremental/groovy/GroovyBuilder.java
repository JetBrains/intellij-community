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


import com.intellij.compiler.instrumentation.FailSafeClassReader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.java.dependencyView.Callbacks;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.CompilationRound;
import org.jetbrains.jps.incremental.java.ClassPostProcessor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.javac.OutputFileObject;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends ModuleLevelBuilder {
  private static final int ourOptimizeThreshold = Integer.parseInt(System.getProperty("groovyc.optimized.class.loading.threshold", "10"));
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovyBuilder");
  private static final Key<Boolean> CHUNK_REBUILD_ORDERED = Key.create("CHUNK_REBUILD_ORDERED");
  private static final Key<Map<String, String>> STUB_TO_SRC = Key.create("STUB_TO_SRC");
  private static final Key<Map<ModuleChunk, GroovycContinuation>> CONTINUATIONS = Key.create("CONTINUATIONS");
  private static final Key<Boolean> FILES_MARKED_DIRTY_FOR_NEXT_ROUND = Key.create("SRC_MARKED_DIRTY");
  private static final String GROOVY_EXTENSION = "groovy";
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
                                           final ModuleChunk chunk,
                                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                           OutputConsumer outputConsumer) throws ProjectBuildException {
    if (GreclipseBuilder.useGreclipse(context)) return ExitCode.NOTHING_DONE;
    
    long start = 0;
    try {
      JpsGroovySettings settings = JpsGroovySettings.getSettings(context.getProjectDescriptor().getProject());

      Ref<Boolean> hasStubExcludes = Ref.create(false);
      final List<File> toCompile = collectChangedFiles(context, dirtyFilesHolder, myForStubs, false, hasStubExcludes);
      if (toCompile.isEmpty()) {
        return hasFilesToCompileForNextRound(context) ? ExitCode.ADDITIONAL_PASS_REQUIRED : ExitCode.NOTHING_DONE;
      }
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("forStubs=" + myForStubs);
      }

      Map<ModuleBuildTarget, String> finalOutputs = getCanonicalModuleOutputs(context, chunk, this);
      if (finalOutputs == null) {
        return ExitCode.ABORT;
      }

      start = System.currentTimeMillis();

      Map<ModuleBuildTarget, String> generationOutputs = myForStubs ? getStubGenerationOutputs(chunk, context) : finalOutputs;
      String compilerOutput = generationOutputs.get(chunk.representativeTarget());

      GroovycOutputParser parser = runGroovycOrContinuation(context, chunk, settings, finalOutputs, compilerOutput, toCompile, hasStubExcludes.get());

      Map<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>>
        compiled = processCompiledFiles(context, chunk, generationOutputs, compilerOutput, parser.getSuccessfullyCompiled());

      if (checkChunkRebuildNeeded(context, parser)) {
        clearContinuation(context, chunk);
        return ExitCode.CHUNK_REBUILD_REQUIRED;
      }

      if (myForStubs) {
        addStubRootsToJavacSourcePath(context, generationOutputs);
        rememberStubSources(context, compiled);
      }

      for (CompilerMessage message : parser.getCompilerMessages()) {
        context.processMessage(message);
      }

      if (!myForStubs) {
        updateDependencies(context, toCompile, compiled, outputConsumer, this);
      }
      return hasFilesToCompileForNextRound(context) ? ExitCode.ADDITIONAL_PASS_REQUIRED : ExitCode.OK;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
    finally {
      if (start > 0 && LOG.isDebugEnabled()) {
        LOG.debug(myBuilderName + " took " + (System.currentTimeMillis() - start) + " on " + chunk.getName());
      }
      if (!myForStubs) {
        FILES_MARKED_DIRTY_FOR_NEXT_ROUND.set(context, null);
      }
    }
  }

  @NotNull
  private GroovycOutputParser runGroovycOrContinuation(CompileContext context,
                                                       ModuleChunk chunk,
                                                       JpsGroovySettings settings,
                                                       Map<ModuleBuildTarget, String> finalOutputs,
                                                       String compilerOutput, List<File> toCompile, boolean hasStubExcludes) throws Exception {
    GroovycContinuation continuation = takeContinuation(context, chunk);
    if (continuation != null) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("using continuation for " + chunk);
      }
      return continuation.continueCompilation();
    }

    final Set<String> toCompilePaths = getPathsToCompile(toCompile);

    JpsSdk<JpsDummyElement> jdk = getJdk(chunk);
    String version = jdk == null ? SystemInfo.JAVA_RUNTIME_VERSION : jdk.getVersionString();
    boolean inProcess = "true".equals(System.getProperty("groovyc.in.process", "true"));
    boolean mayDependOnUtilJar = version != null && StringUtil.compareVersionNumbers(version, "1.6") >= 0;
    boolean optimizeClassLoading = !inProcess && mayDependOnUtilJar && ourOptimizeThreshold != 0 && toCompilePaths.size() >= ourOptimizeThreshold;

    Map<String, String> class2Src = buildClassToSourceMap(chunk, context, toCompilePaths, finalOutputs);

    final String encoding = context.getProjectDescriptor().getEncodingConfiguration().getPreferredModuleChunkEncoding(chunk);
    List<String> patchers = new ArrayList<String>();

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

  private static void clearContinuation(CompileContext context, ModuleChunk chunk) {
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

  private static boolean checkChunkRebuildNeeded(CompileContext context, GroovycOutputParser parser) {
    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) || !parser.shouldRetry()) {
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

  private static void rememberStubSources(CompileContext context, Map<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>> compiled) {
    Map<String, String> stubToSrc = STUB_TO_SRC.get(context);
    if (stubToSrc == null) {
      STUB_TO_SRC.set(context, stubToSrc = new HashMap<String, String>());
    }
    for (Collection<GroovycOutputParser.OutputItem> items : compiled.values()) {
      for (GroovycOutputParser.OutputItem item : items) {
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

  public static Map<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>> processCompiledFiles(CompileContext context,
                                                                                                             ModuleChunk chunk,
                                                                                                             Map<ModuleBuildTarget, String> generationOutputs,
                                                                                                             String compilerOutput,
                                                                                                             List<GroovycOutputParser.OutputItem> successfullyCompiled)
    throws IOException {
    ProjectDescriptor pd = context.getProjectDescriptor();

    final Map<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>> compiled = new THashMap<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>>();
    for (final GroovycOutputParser.OutputItem item : successfullyCompiled) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("compiled=" + item);
      }
      final JavaSourceRootDescriptor rd = pd.getBuildRootIndex().findJavaRootDescriptor(context, new File(item.sourcePath));
      if (rd != null) {
        final String outputPath = ensureCorrectOutput(chunk, item, generationOutputs, compilerOutput, rd.target);

        Collection<GroovycOutputParser.OutputItem> items = compiled.get(rd.target);
        if (items == null) {
          items = new ArrayList<GroovycOutputParser.OutputItem>();
          compiled.put(rd.target, items);
        }

        items.add(new GroovycOutputParser.OutputItem(outputPath, item.sourcePath));
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

  @Override
  public void buildStarted(CompileContext context) {
    if (myForStubs) {
      File stubRoot = getStubRoot(context);
      if (stubRoot.exists() && !FileUtil.deleteWithRenaming(stubRoot)) {
        context.processMessage(new CompilerMessage(myBuilderName, BuildMessage.Kind.ERROR, "External make cannot clean " + stubRoot.getPath()));
      }
    }
  }

  @Override
  public void chunkBuildFinished(CompileContext context, ModuleChunk chunk) {
    JavaBuilderUtil.cleanupChunkResources(context);
    clearContinuation(context, chunk);
    STUB_TO_SRC.set(context, null);
  }

  private static Map<ModuleBuildTarget, String> getStubGenerationOutputs(ModuleChunk chunk, CompileContext context) throws IOException {
    Map<ModuleBuildTarget, String> generationOutputs = new HashMap<ModuleBuildTarget, String>();
    File commonRoot = getStubRoot(context);
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File targetRoot = new File(commonRoot, target.getModule().getName() + File.separator + target.getTargetType().getTypeId());
      if (targetRoot.exists() && !FileUtil.deleteWithRenaming(targetRoot)) {
        throw new IOException("External make cannot clean " + targetRoot.getPath());
      }
      if (!targetRoot.mkdirs()) {
        throw new IOException("External make cannot create " + targetRoot.getPath());
      }
      generationOutputs.put(target, targetRoot.getPath());
    }
    return generationOutputs;
  }

  private static File getStubRoot(CompileContext context) {
    return new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), "groovyStubs");
  }

  @Nullable
  public static Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk, Builder builder) {
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

  private static String ensureCorrectOutput(ModuleChunk chunk,
                                            GroovycOutputParser.OutputItem item,
                                            Map<ModuleBuildTarget, String> generationOutputs,
                                            String compilerOutput,
                                            @NotNull ModuleBuildTarget srcTarget) throws IOException {
    if (chunk.getModules().size() > 1 && !srcTarget.equals(chunk.representativeTarget())) {
      File output = new File(item.outputPath);

      String srcTargetOutput = generationOutputs.get(srcTarget);
      if (srcTargetOutput == null) {
        LOG.info("No output for " + srcTarget + "; outputs=" + generationOutputs + "; targets = " + chunk.getTargets());
        return item.outputPath;
      }

      //todo honor package prefixes
      File correctRoot = new File(srcTargetOutput);
      File correctOutput = new File(correctRoot, FileUtil.getRelativePath(new File(compilerOutput), output));

      FileUtil.rename(output, correctOutput);
      return correctOutput.getPath();
    }
    return item.outputPath;
  }

  static JpsSdk<JpsDummyElement> getJdk(ModuleChunk chunk) {
    return chunk.getModules().iterator().next().getSdk(JpsJavaSdkType.INSTANCE);
  }

  static List<File> collectChangedFiles(CompileContext context,
                                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                        final boolean forStubs, final boolean forEclipse, final Ref<Boolean> hasExcludes)
    throws IOException {

    final JpsJavaCompilerConfiguration configuration =
      JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.getProjectDescriptor().getProject());
    assert configuration != null;

    final JpsGroovySettings settings = JpsGroovySettings.getSettings(context.getProjectDescriptor().getProject());

    final List<File> toCompile = new ArrayList<File>();
    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor sourceRoot) throws IOException {
        final String path = file.getPath();
        //todo file type check
        if ((isGroovyFile(path) || forEclipse && path.endsWith(".java")) &&
            !configuration.isResourceFile(file, sourceRoot.root)) {
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

  public static void updateDependencies(CompileContext context,
                                        List<File> toCompile,
                                        Map<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>> successfullyCompiled,
                                        OutputConsumer outputConsumer, Builder builder) throws IOException {
    JavaBuilderUtil.registerFilesToCompile(context, toCompile);
    if (!successfullyCompiled.isEmpty()) {

      final Callbacks.Backend callback = JavaBuilderUtil.getDependenciesRegistrar(context);

      for (Map.Entry<ModuleBuildTarget, Collection<GroovycOutputParser.OutputItem>> entry : successfullyCompiled.entrySet()) {
        final ModuleBuildTarget target = entry.getKey();
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
            outputConsumer.registerCompiledClass(
              target,
              new CompiledClass(outputFile, srcFile, readClassName(bytes), new BinaryContent(bytes))
            );
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

  private static String readClassName(byte[] classBytes) throws IOException{
    final Ref<String> nameRef = Ref.create(null);
    new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.API_VERSION) {
      public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        nameRef.set(name.replace('/', '.'));
      }
    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return nameRef.get();
  }

  private static Collection<String> generateClasspath(CompileContext context, ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<String>();
    //groovy_rt.jar
    // IMPORTANT! must be the first in classpath
    cp.addAll(getGroovyRtRoots());

    for (File file : ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false)) {
      cp.add(FileUtil.toCanonicalPath(file.getPath()));
    }

    for (GroovyBuilderExtension extension : JpsServiceManager.getInstance().getExtensions(GroovyBuilderExtension.class)) {
      cp.addAll(extension.getCompilationClassPath(context, chunk));
    }

    return cp;
  }

  static List<String> getGroovyRtRoots() {
    File rt = ClasspathBootstrap.getResourceFile(GroovyBuilder.class);
    File constants = ClasspathBootstrap.getResourceFile(GroovyRtConstants.class);
    return Arrays.asList(new File(rt.getParentFile(), rt.isFile() ? "groovy_rt.jar" : "groovy_rt").getPath(), 
                         new File(constants.getParentFile(), constants.isFile() ? "groovy-rt-constants.jar" : "groovy-rt-constants").getPath());
  }

  public static boolean isGroovyFile(String path) {
    return path.endsWith("." + GROOVY_EXTENSION);
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.singletonList(GROOVY_EXTENSION);
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
