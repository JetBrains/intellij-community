package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.jps.ModuleChunk;
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
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.javac.OutputFileObject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovyBuilder");
  public static final String BUILDER_NAME = "groovy";
  private static final Key<Boolean> CHUNK_REBUILD_ORDERED = Key.create("CHUNK_REBUILD_ORDERED");
  private static final Key<Map<String, String>> STUB_TO_SRC = Key.create("STUB_TO_SRC");
  private final boolean myForStubs;
  private final String myBuilderName;

  public GroovyBuilder(boolean forStubs) {
    super(forStubs ? BuilderCategory.SOURCE_GENERATOR : BuilderCategory.OVERWRITING_TRANSLATOR);
    myForStubs = forStubs;
    myBuilderName = BUILDER_NAME + (forStubs ? "-stubs" : "-classes");
  }

  static {
    JavaBuilder.registerClassPostProcessor(new RecompileStubSources());
  }

  public String getName() {
    return myBuilderName;
  }

  public ModuleLevelBuilder.ExitCode build(final CompileContext context,
                                           ModuleChunk chunk,
                                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws ProjectBuildException {
    try {
      final List<File> toCompile = collectChangedFiles(context, dirtyFilesHolder);
      if (toCompile.isEmpty()) {
        return ExitCode.NOTHING_DONE;
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
      List<String> patchers = Collections.emptyList(); //todo patchers

      Map<ModuleBuildTarget, String> generationOutputs = myForStubs ? getStubGenerationOutputs(chunk, context) : finalOutputs;
      String compilerOutput = generationOutputs.get(chunk.representativeTarget());

      String finalOutput = FileUtil.toSystemDependentName(finalOutputs.get(chunk.representativeTarget()));
      final File tempFile = GroovycOSProcessHandler.fillFileWithGroovycParameters(
        compilerOutput, toCompilePaths, finalOutput, class2Src, encoding, patchers
      );
      final GroovycOSProcessHandler handler = runGroovyc(context, chunk, tempFile);

      List<GroovycOSProcessHandler.OutputItem> compiled = processCompiledFiles(context, chunk, generationOutputs, compilerOutput, handler);

      if (checkChunkRebuildNeeded(context, handler)) {
        return ExitCode.CHUNK_REBUILD_REQUIRED;
      }

      if (myForStubs) {
        addStubRootsToJavacSourcePath(context, generationOutputs);
        rememberStubSources(context, compiled);
      }

      for (CompilerMessage message : handler.getCompilerMessages()) {
        context.processMessage(message);
      }

      if (!myForStubs && updateDependencies(context, chunk, toCompile, generationOutputs, compiled)) {
        return ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
      return ExitCode.OK;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
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

  private GroovycOSProcessHandler runGroovyc(final CompileContext context, ModuleChunk chunk, File tempFile) throws IOException {
    //todo xmx
    final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
      getJavaExecutable(chunk),
      "org.jetbrains.groovy.compiler.rt.GroovycRunner",
      Collections.<String>emptyList(), new ArrayList<String>(generateClasspath(context, chunk)),
      Arrays.asList("-Xmx384m",
                    "-Dfile.encoding=" + System.getProperty("file.encoding")/*,
                    "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239"*/),
      Arrays.<String>asList(myForStubs ? "stubs" : "groovyc",
                            tempFile.getPath())
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
    if (context.isProjectRebuild() || !handler.shouldRetry()) {
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

  private static void rememberStubSources(CompileContext context, List<GroovycOSProcessHandler.OutputItem> compiled) {
    Map<String, String> stubToSrc = STUB_TO_SRC.get(context);
    if (stubToSrc == null) {
      STUB_TO_SRC.set(context, stubToSrc = new ConcurrentHashMap<String, String>());
    }
    for (GroovycOSProcessHandler.OutputItem item : compiled) {
      stubToSrc.put(FileUtil.toSystemIndependentName(item.outputPath), item.sourcePath);
    }
  }

  private static void addStubRootsToJavacSourcePath(CompileContext context, Map<ModuleBuildTarget, String> generationOutputs) {
    final BuildRootIndex rootsIndex = context.getProjectDescriptor().getBuildRootIndex();
    for (ModuleBuildTarget target : generationOutputs.keySet()) {
      File root = new File(generationOutputs.get(target));
      rootsIndex.associateTempRoot(context, target, new JavaSourceRootDescriptor(root, target, true, true, ""));
    }
  }

  private static List<GroovycOSProcessHandler.OutputItem> processCompiledFiles(CompileContext context,
                                                                               ModuleChunk chunk,
                                                                               Map<ModuleBuildTarget, String> generationOutputs,
                                                                               String compilerOutput, GroovycOSProcessHandler handler)
    throws IOException {
    ProjectDescriptor pd = context.getProjectDescriptor();

    List<GroovycOSProcessHandler.OutputItem> compiled = new ArrayList<GroovycOSProcessHandler.OutputItem>();
    for (GroovycOSProcessHandler.OutputItem item : handler.getSuccessfullyCompiled()) {
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.info("compiled=" + item);
      }
      final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);

      JavaSourceRootDescriptor rootDescriptor = pd.getBuildRootIndex().findJavaRootDescriptor(context, new File(sourcePath));
      if (rootDescriptor != null) {
        ModuleBuildTarget target = rootDescriptor.target;
        String outputPath = ensureCorrectOutput(chunk, item, generationOutputs, compilerOutput, target);
        pd.dataManager.getSourceToOutputMap(target).appendOutput(sourcePath, FileUtil.toSystemIndependentName(outputPath));
        item = new GroovycOSProcessHandler.OutputItem(outputPath, item.sourcePath);
      }

      compiled.add(item);
    }
    return compiled;
  }

  @Override
  public void cleanupChunkResources(CompileContext context) {
    JavaBuilderUtil.cleanupChunkResources(context);
    STUB_TO_SRC.set(context, null);
  }

  private static Map<ModuleBuildTarget, String> getStubGenerationOutputs(ModuleChunk chunk, CompileContext context) throws IOException {
    Map<ModuleBuildTarget, String> generationOutputs = new HashMap<ModuleBuildTarget, String>();
    File commonRoot = new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), "groovyStubs");
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File targetRoot = new File(commonRoot, target.getModuleName() + File.separator + target.getTargetType().getTypeId());
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
  private static Map<ModuleBuildTarget, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk) {
    Map<ModuleBuildTarget, String> finalOutputs = new HashMap<ModuleBuildTarget, String>();
    for (ModuleBuildTarget target : chunk.getTargets()) {
      File moduleOutputDir = target.getOutputDir();
      if (moduleOutputDir == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Output directory not specified for module " + target.getModuleName()));
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

  @Override
  public boolean shouldHonorFileEncodingForCompilation(File file) {
    return isGroovyFile(file.getAbsolutePath());
  }

  private static List<File> collectChangedFiles(CompileContext context,
                                                DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws IOException {
    final ResourcePatterns patterns = ResourcePatterns.KEY.get(context);
    assert patterns != null;
    final List<File> toCompile = new ArrayList<File>();
    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor sourceRoot) throws IOException {
        final String path = file.getPath();
        if (isGroovyFile(path) && !patterns.isResourceFile(file, sourceRoot.root)) { //todo file type check
          toCompile.add(file);
        }
        return true;
      }
    });
    return toCompile;
  }

  private static boolean updateDependencies(CompileContext context,
                                            ModuleChunk chunk,
                                            List<File> toCompile,
                                            Map<ModuleBuildTarget, String> generationOutputs,
                                            List<GroovycOSProcessHandler.OutputItem> successfullyCompiled) throws IOException {
    final Mappings delta = context.getProjectDescriptor().dataManager.getMappings().createDelta();
    final List<File> successfullyCompiledFiles = new ArrayList<File>();
    if (!successfullyCompiled.isEmpty()) {

      final Callbacks.Backend callback = delta.getCallback();
      final FileGeneratedEvent generatedEvent = new FileGeneratedEvent();

      for (GroovycOSProcessHandler.OutputItem item : successfullyCompiled) {
        final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
        final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
        final JavaSourceRootDescriptor moduleAndRoot = context.getProjectDescriptor().getBuildRootIndex().findJavaRootDescriptor(context,
                                                                                                                                 new File(
                                                                                                                                   sourcePath));
        if (moduleAndRoot != null) {
          String moduleOutputPath = generationOutputs.get(moduleAndRoot.target);
          generatedEvent.add(moduleOutputPath, FileUtil.getRelativePath(moduleOutputPath, outputPath, '/'));
        }
        callback.associate(outputPath, sourcePath, new ClassReader(FileUtil.loadFileBytes(new File(outputPath))));
        successfullyCompiledFiles.add(new File(sourcePath));
      }

      context.processMessage(generatedEvent);
    }


    return JavaBuilderUtil.updateMappings(context, delta, chunk, toCompile, successfullyCompiledFiles);
  }

  private static List<String> generateClasspath(CompileContext context, ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<String>();
    //groovy_rt.jar
    // IMPORTANT! must be the first in classpath
    cp.add(getGroovyRtRoot().getPath());

    for (File file : context.getProjectPaths().getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false)) {
      cp.add(FileUtil.toCanonicalPath(file.getPath()));
    }
    return new ArrayList<String>(cp);
  }

  private static File getGroovyRtRoot() {
    File root = ClasspathBootstrap.getResourcePath(GroovyBuilder.class);
    if (root.isFile()) {
      return new File(root.getParentFile(), "groovy_rt.jar");
    }
    return new File(root.getParentFile(), "groovy_rt");
  }

  private static boolean isGroovyFile(String path) {
    return path.endsWith(".groovy") || path.endsWith(".gpp");
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
    return "GroovyBuilder{" +
           "myForStubs=" + myForStubs +
           '}';
  }

  public String getDescription() {
    return "Groovy builder";
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
        FSOperations.markDirty(context, new File(groovy));
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
