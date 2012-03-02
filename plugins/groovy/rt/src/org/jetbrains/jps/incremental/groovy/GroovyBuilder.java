package org.jetbrains.jps.incremental.groovy;


import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SystemProperties;
import groovy.util.CharsetToolkit;
import org.jetbrains.ether.dependencyView.Callbacks;
import org.jetbrains.ether.dependencyView.Mappings;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerWrapper;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.FileGeneratedEvent;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.objectweb.asm.ClassReader;

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
    ExitCode exitCode = ExitCode.OK;
    try {
      final List<File> toCompile = collectChangedFiles(context, chunk);
      if (toCompile.isEmpty()) {
        return exitCode;
      }

      String moduleOutput = getModuleOutput(context, chunk);
      String compilerOutput = getCompilerOutput(context, moduleOutput);

      final Set<String> toCompilePaths = new LinkedHashSet<String>();
      for (File file : toCompile) {
        toCompilePaths.add(FileUtil.toSystemIndependentName(file.getPath()));
      }
      
      Map<String, String> class2Src = buildClassToSourceMap(chunk, context, toCompilePaths, moduleOutput);

      String ideCharset = chunk.getProject().getProjectCharset();
      String encoding = !Comparing.equal(CharsetToolkit.getDefaultSystemCharset().name(), ideCharset) ? ideCharset : null;
      List<String> patchers = Collections.emptyList(); //todo patchers
      final File tempFile = GroovycOSProcessHandler.fillFileWithGroovycParameters(
        compilerOutput, toCompilePaths, FileUtil.toSystemDependentName(moduleOutput), class2Src, encoding, patchers
      );

      //todo different outputs in a chunk
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

      List<GroovycOSProcessHandler.OutputItem> successfullyCompiled = Collections.emptyList();
      try {
        final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(cmd));
        GroovycOSProcessHandler handler = GroovycOSProcessHandler.runGroovyc(process, new Consumer<String>() {
          public void consume(String s) {
            context.processMessage(new ProgressMessage(s));
          }
        });

        if (handler.shouldRetry()) {
          if (CHUNK_REBUILD_ORDERED.get(context) != null) {
            CHUNK_REBUILD_ORDERED.set(context, null);
          } else {
            CHUNK_REBUILD_ORDERED.set(context, Boolean.TRUE);
            exitCode = ExitCode.CHUNK_REBUILD_REQUIRED;
            return exitCode;
          }
        }

        successfullyCompiled = handler.getSuccessfullyCompiled();

        for (CompilerMessage message : handler.getCompilerMessages()) {
          context.processMessage(message);
        }
      }
      finally {
        if (!myForStubs) {
          if (updateDependencies(context, chunk, toCompile, moduleOutput, successfullyCompiled)) {
            exitCode = ExitCode.ADDITIONAL_PASS_REQUIRED;
          }
        }
      }

      return exitCode;
    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  private static String getJavaExecutable(ModuleChunk chunk) {
    Sdk sdk = chunk.getModules().iterator().next().getSdk();
    if (sdk instanceof JavaSdk) {
      return ((JavaSdk)sdk).getJavaExecutable();
    }
    return SystemProperties.getJavaHome() + "/bin/java";
  }

  private static String getModuleOutput(CompileContext context, ModuleChunk chunk) {
    final Module representativeModule = chunk.getModules().iterator().next();
    File moduleOutputDir = context.getProjectPaths().getModuleOutputDir(representativeModule, context.isCompilingTests());
    assert moduleOutputDir != null;
    String moduleOutputPath = FileUtil.toCanonicalPath(moduleOutputDir.getPath());
    return moduleOutputPath.endsWith("/") ? moduleOutputPath : moduleOutputPath + "/";
  }

  private String getCompilerOutput(CompileContext context, String moduleOutputDir) throws IOException {
    final File dir = myForStubs ? FileUtil.createTempDirectory("groovyStubs", null) : new File(moduleOutputDir);
    if (myForStubs) {
      JavaBuilder.addTempSourcePathRoot(context, dir);
    }
    return FileUtil.toCanonicalPath(dir.getPath());
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
                                  String moduleOutputPath,
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
          final String moduleName = moduleAndRoot.module.getName().toLowerCase(Locale.US);
          context.getDataManager().getSourceToOutputMap(moduleName, moduleAndRoot.isTestRoot).appendData(sourcePath, outputPath);
        }
        callback.associate(outputPath, Callbacks.getDefaultLookup(sourcePath), new ClassReader(FileUtil.loadFileBytes(new File(outputPath))));
        successfullyCompiledFiles.add(new File(sourcePath));

        generatedEvent.add(moduleOutputPath, FileUtil.getRelativePath(moduleOutputPath, outputPath, '/'));
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

  private static Map<String, String> buildClassToSourceMap(ModuleChunk chunk, CompileContext context, Set<String> toCompilePaths, String moduleOutputPath) throws IOException {
    final Map<String, String> class2Src = new HashMap<String, String>();
    for (Module module : chunk.getModules()) {
      final String moduleName = module.getName().toLowerCase(Locale.US);
      final SourceToOutputMapping srcToOut = context.getDataManager().getSourceToOutputMap(moduleName, context.isCompilingTests());
      for (String src : srcToOut.getKeys()) {
        if (!toCompilePaths.contains(src) && isGroovyFile(src)) {
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

  public String getDescription() {
    return "Groovy builder";
  }

}
