package org.jetbrains.jps.appengine.build;

import com.intellij.appengine.rt.EnhancerRunner;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.execution.ParametersListUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.appengine.model.JpsAppEngineExtensionService;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.appengine.model.PersistenceApi;
import org.jetbrains.jps.builders.ChunkBuildOutputConsumer;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.logging.ProjectBuilderLogger;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author nik
 */
public class AppEngineEnhancerBuilder extends ModuleLevelBuilder {
  public static final String NAME = "Google AppEngine Enhancer";

  public AppEngineEnhancerBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @Override
  public ExitCode build(final CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        ChunkBuildOutputConsumer outputConsumer)
    throws ProjectBuildException, IOException {
    
    boolean doneSomething = false;
    for (final JpsModule module : chunk.getModules()) {
      JpsAppEngineModuleExtension extension = JpsAppEngineExtensionService.getInstance().getExtension(module);
      if (extension != null && extension.isRunEnhancerOnMake()) {
        doneSomething |= processModule(context, dirtyFilesHolder, extension);
      }
    }

    return doneSomething ? ExitCode.OK : ExitCode.NOTHING_DONE;
  }

  private static boolean processModule(final CompileContext context,
                                       DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                       JpsAppEngineModuleExtension extension) throws IOException {
    final Set<File> roots = new THashSet<File>(FileUtil.FILE_HASHING_STRATEGY);
    for (String path : extension.getFilesToEnhance()) {
      roots.add(new File(FileUtil.toSystemDependentName(path)));
    }

    final List<String> pathsToProcess = new ArrayList<String>();
    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      @Override
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor root) throws IOException {
        if (JpsPathUtil.isUnder(roots, file)) {
          Collection<String> outputs = context.getProjectDescriptor().dataManager.getSourceToOutputMap(target).getOutputs(file.getAbsolutePath());
          if (outputs != null) {
            pathsToProcess.addAll(outputs);
          }
        }
        return true;
      }
    });
    if (pathsToProcess.isEmpty()) {
      return false;
    }

    JpsModule module = extension.getModule();
    JpsSdk<JpsDummyElement> sdk = module.getSdk(JpsJavaSdkType.INSTANCE);
    if (sdk == null) {
      context.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.ERROR, "JDK isn't specified for module '" + module.getName() + "'"));
      return true;
    }
    context.processMessage(new ProgressMessage("Enhancing classes in module '" + module.getName() + "'..."));

    List<String> vmParams = Collections.singletonList("-Xmx256m");

    List<String> classpath = new ArrayList<String>();
    classpath.add(extension.getToolsApiJarPath());
    classpath.add(PathManager.getJarPathForClass(EnhancerRunner.class));
    for (File file : JpsJavaExtensionService.dependencies(module).recursively().compileOnly().productionOnly().classes().getRoots()) {
      classpath.add(file.getAbsolutePath());
    }

    List<String> programParams = new ArrayList<String>();
    final File argsFile = FileUtil.createTempFile("appEngineEnhanceFiles", ".txt");
    PrintWriter writer = new PrintWriter(argsFile);
    try {
      for (String path : pathsToProcess) {
        writer.println(FileUtil.toSystemDependentName(path));
      }
    }
    finally {
      writer.close();
    }

    programParams.add(argsFile.getAbsolutePath());
    programParams.add("com.google.appengine.tools.enhancer.Enhance");
    programParams.add("-api");
    PersistenceApi api = extension.getPersistenceApi();
    programParams.add(api.getEnhancerApiName());
    if (api.getEnhancerVersion() == 2) {
      programParams.add("-enhancerVersion");
      programParams.add("v2");
    }
    programParams.add("-v");
    List<String> commandLine = ExternalProcessUtil.buildJavaCommandLine(JpsJavaSdkType.getJavaExecutable(sdk), EnhancerRunner.class.getName(),
                                                                    Collections.<String>emptyList(), classpath, vmParams, programParams);

    Process process = new ProcessBuilder(commandLine).start();
    ExternalEnhancerProcessHandler handler = new ExternalEnhancerProcessHandler(process, commandLine, context);
    handler.startNotify();
    handler.waitFor();
    ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
    if (logger.isEnabled()) {
      logger.logCompiledPaths(pathsToProcess, NAME, "Enhancing classes:");
    }
    return true;
  }


  @NotNull
  @Override
  public String getPresentableName() {
    return NAME;
  }

  private static class ExternalEnhancerProcessHandler extends EnhancerProcessHandlerBase {
    private final CompileContext myContext;

    public ExternalEnhancerProcessHandler(Process process, List<String> commandLine, CompileContext context) {
      super(process, ParametersListUtil.join(commandLine), null);
      myContext = context;
    }

    @Override
    protected void reportInfo(String message) {
      myContext.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.INFO, message));
    }

    @Override
    protected void reportError(String message) {
      myContext.processMessage(new CompilerMessage(NAME, BuildMessage.Kind.ERROR, message));
    }
  }
}
