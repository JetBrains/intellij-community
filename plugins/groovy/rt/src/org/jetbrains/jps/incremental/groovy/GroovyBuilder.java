package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import groovy.util.CharsetToolkit;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.server.ClasspathBootstrap;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/11
 */
public class GroovyBuilder extends ModuleLevelBuilder {
  public static final String BUILDER_NAME = "groovy";
  private static final Key<Boolean> CHUNK_REBUILD_ORDERED = Key.create("CHUNK_REBUILD_ORDERED");
  private final boolean myForStubs;
  private final String myBuilderName;

  public GroovyBuilder(boolean forStubs) {
    super(forStubs ? BuilderCategory.SOURCE_GENERATOR : BuilderCategory.OVERWRITING_TRANSLATOR);
    myForStubs = forStubs;
    myBuilderName = BUILDER_NAME + (forStubs ? "-stubs" : "-classes");
  }

  public String getName() {
    return myBuilderName;
  }

  public ModuleLevelBuilder.ExitCode build(final CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    try {
      final List<File> toCompile = collectChangedFiles(context, chunk);
      if (toCompile.isEmpty()) {
        return ExitCode.NOTHING_DONE;
      }

      Map<Module, String> finalOutputs = getCanonicalModuleOutputs(context, chunk);
      Map<Module, String> generationOutputs = getGenerationOutputs(chunk, finalOutputs);

      final Set<String> toCompilePaths = new LinkedHashSet<String>();
      for (File file : toCompile) {
        toCompilePaths.add(FileUtil.toSystemIndependentName(file.getPath()));
      }
      
      Map<String, String> class2Src = buildClassToSourceMap(chunk, context, toCompilePaths, finalOutputs);

      final String encoding = context.getProjectDescriptor().getEncodingConfiguration().getPreferredModuleChunkEncoding(chunk);
      List<String> patchers = Collections.emptyList(); //todo patchers
      String compilerOutput = generationOutputs.get(chunk.representativeModule());
      final File tempFile = GroovycOSProcessHandler.fillFileWithGroovycParameters(
        compilerOutput, toCompilePaths, finalOutputs.get(chunk.representativeModule()), class2Src, encoding, patchers
      );

      //todo xmx
      final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
        getJavaExecutable(chunk),
        "org.jetbrains.groovy.compiler.rt.GroovycRunner",
        Collections.<String>emptyList(), new ArrayList<String>(generateClasspath(context, chunk)),
        Arrays.asList("-Xmx384m",
                      "-Dfile.encoding=" + CharsetToolkit.getDefaultSystemCharset().name()/*,
                      "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239"*/),
        Arrays.<String>asList(myForStubs ? "stubs" : "groovyc",
                              tempFile.getPath())
      );

      final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(cmd));
      GroovycOSProcessHandler handler = GroovycOSProcessHandler.runGroovyc(process, new Consumer<String>() {
        public void consume(String s) {
          context.processMessage(new ProgressMessage(s));
        }
      });

      if (!context.isProjectRebuild() && handler.shouldRetry()) {
        if (CHUNK_REBUILD_ORDERED.get(context) != null) {
          CHUNK_REBUILD_ORDERED.set(context, null);
        } else {
          CHUNK_REBUILD_ORDERED.set(context, Boolean.TRUE);
          return ExitCode.CHUNK_REBUILD_REQUIRED;
        }
      }

      if (myForStubs) {
        for (Module module : generationOutputs.keySet()) {
          File root = new File(generationOutputs.get(module));
          context.getRootsIndex().associateRoot(root, module, context.isCompilingTests(), true);
          JavaBuilder.addTempSourcePathRoot(context, root);
        }
      }

      for (CompilerMessage message : handler.getCompilerMessages()) {
        context.processMessage(message);
      }


      List<GroovycOSProcessHandler.OutputItem> compiled = new ArrayList<GroovycOSProcessHandler.OutputItem>();
      for (GroovycOSProcessHandler.OutputItem item : handler.getSuccessfullyCompiled()) {
        compiled.add(ensureCorrectOutput(context, chunk, item, generationOutputs, compilerOutput));
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

  private Map<Module, String> getGenerationOutputs(ModuleChunk chunk, Map<Module, String> finalOutputs) throws IOException {
    Map<Module, String> generationOutputs = new HashMap<Module, String>();
    for (Module module : chunk.getModules()) {
      generationOutputs.put(module, myForStubs ? FileUtil.createTempDirectory("groovyStubs", module.getName()).getPath() : finalOutputs.get(module));
    }
    return generationOutputs;
  }

  private static Map<Module, String> getCanonicalModuleOutputs(CompileContext context, ModuleChunk chunk) {
    Map<Module, String> finalOutputs = new HashMap<Module, String>();
    for (Module module : chunk.getModules()) {
      File moduleOutputDir = context.getProjectPaths().getModuleOutputDir(module, context.isCompilingTests());
      assert moduleOutputDir != null;
      String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
      assert moduleOutputPath != null;
      finalOutputs.put(module, moduleOutputPath.endsWith("/") ? moduleOutputPath : moduleOutputPath + "/");
    }
    return finalOutputs;
  }

  private static GroovycOSProcessHandler.OutputItem ensureCorrectOutput(CompileContext context,
                                                                        ModuleChunk chunk,
                                                                        GroovycOSProcessHandler.OutputItem item, Map<Module, String> generationOutputs, String compilerOutput) throws IOException {
    if (chunk.getModules().size() > 1) {
      RootDescriptor descriptor = context.getModuleAndRoot(new File(item.sourcePath));
      if (descriptor != null) {
        Module srcModule = context.getProject().getModules().get(descriptor.module);
        if (srcModule != null && srcModule != chunk.representativeModule()) {
          File output = new File(item.outputPath);

          //todo honor package prefixes
          File correctRoot = new File(generationOutputs.get(srcModule));
          File correctOutput = new File(correctRoot, FileUtil.getRelativePath(new File(compilerOutput), output));

          FileUtil.rename(output, correctOutput);
          return new GroovycOSProcessHandler.OutputItem(correctOutput.getPath(), item.sourcePath);
        }
      }
    }
    return item;
  }

  private static String getJavaExecutable(ModuleChunk chunk) {
    Sdk sdk = chunk.getModules().iterator().next().getSdk();
    if (sdk instanceof JavaSdk) {
      return ((JavaSdk)sdk).getJavaExecutable();
    }
    return SystemProperties.getJavaHome() + "/bin/java";
  }

  @Override
  public boolean shouldHonorFileEncodingForCompilation(File file) {
    return isGroovyFile(file.getAbsolutePath());
  }

  private static List<File> collectChangedFiles(CompileContext context, ModuleChunk chunk) throws IOException {
    final List<File> toCompile = new ArrayList<File>();
    context.processFilesToRecompile(chunk, new FileProcessor() {
      public boolean apply(Module module, File file, String sourceRoot) throws IOException {
        final String path = file.getPath();
        if (isGroovyFile(path)) { //todo file type check
          toCompile.add(file);
        }
        return true;
      }
    });
    return toCompile;
  }

  private boolean updateDependencies(CompileContext context,
                                  ModuleChunk chunk,
                                  List<File> toCompile,
                                  Map<Module, String> generationOutputs,
                                  List<GroovycOSProcessHandler.OutputItem> successfullyCompiled) throws IOException {
    final Mappings delta = context.createDelta();
    final List<File> successfullyCompiledFiles = new ArrayList<File>();
    if (!successfullyCompiled.isEmpty()) {

      final Callbacks.Backend callback = delta.getCallback();
      final FileGeneratedEvent generatedEvent = new FileGeneratedEvent();

      for (GroovycOSProcessHandler.OutputItem item : successfullyCompiled) {
        final String sourcePath = FileUtil.toSystemIndependentName(item.sourcePath);
        final String outputPath = FileUtil.toSystemIndependentName(item.outputPath);
        final RootDescriptor moduleAndRoot = context.getModuleAndRoot(new File(sourcePath));
        if (moduleAndRoot != null) {
          final String moduleName = moduleAndRoot.module;
          context.getDataManager().getSourceToOutputMap(moduleName, moduleAndRoot.isTestRoot).appendData(sourcePath, outputPath);
          String moduleOutputPath = generationOutputs.get(context.getProject().getModules().get(moduleName));
          generatedEvent.add(moduleOutputPath, FileUtil.getRelativePath(moduleOutputPath, outputPath, '/'));
        }
        callback.associate(outputPath, sourcePath, new ClassReader(FileUtil.loadFileBytes(new File(outputPath))));
        successfullyCompiledFiles.add(new File(sourcePath));
      }

      context.processMessage(generatedEvent);
    }


    return updateMappings(context, delta, chunk, toCompile, successfullyCompiledFiles);
  }

  private static List<String> generateClasspath(CompileContext context, ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<String>();
    //groovy_rt.jar
    // IMPORTANT! must be the first in classpath
    cp.add(ClasspathBootstrap.getResourcePath(GroovyCompilerWrapper.class).getPath());

    for (File file : context.getProjectPaths().getClasspathFiles(chunk, ClasspathKind.compile(context.isCompilingTests()), false)) {
      cp.add(FileUtil.toCanonicalPath(file.getPath()));
    }
    for (File file : context.getProjectPaths().getClasspathFiles(chunk, ClasspathKind.runtime(context.isCompilingTests()), false)) {
      cp.add(FileUtil.toCanonicalPath(file.getPath()));
    }
    return new ArrayList<String>(cp);
  }

  private static boolean isGroovyFile(String path) {
    return path.endsWith(".groovy") || path.endsWith(".gpp");
  }

  private static Map<String, String> buildClassToSourceMap(ModuleChunk chunk, CompileContext context, Set<String> toCompilePaths, Map<Module, String> finalOutputs) throws IOException {
    final Map<String, String> class2Src = new HashMap<String, String>();
    for (Module module : chunk.getModules()) {
      String moduleOutputPath = finalOutputs.get(module);
      final SourceToOutputMapping srcToOut = context.getDataManager().getSourceToOutputMap(module.getName(), context.isCompilingTests());
      for (String src : srcToOut.getKeys()) {
        if (!toCompilePaths.contains(src) && isGroovyFile(src) &&
            !context.getProject().getCompilerConfiguration().getExcludes().isExcluded(new File(src))) {
          final Collection<String> outs = srcToOut.getState(src);
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

}
