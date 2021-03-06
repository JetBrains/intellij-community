// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.appengine.build;

import com.intellij.appengine.rt.EnhancerRunner;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.appengine.model.JpsAppEngineExtensionService;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.appengine.model.PersistenceApi;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaBuilderUtil;
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
import java.util.function.Supplier;

public final class AppEngineEnhancerBuilder extends ModuleLevelBuilder {

  public static Supplier<@Nls String> NAME_SUPPLIER = JavaGoogleAppEngineJpsBundle.messagePointer("google.appengine.enhancer");

  public AppEngineEnhancerBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @Override
  public ExitCode build(final CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer)
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

  @NotNull
  @Override
  public List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  private static boolean processModule(final CompileContext context,
                                       DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                                       JpsAppEngineModuleExtension extension) throws IOException, ProjectBuildException {
    final Set<File> roots = FileCollectionFactory.createCanonicalFileSet();
    for (String path : extension.getFilesToEnhance()) {
      roots.add(new File(FileUtil.toSystemDependentName(path)));
    }

    final List<String> pathsToProcess = new ArrayList<>();
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
    JpsSdk<JpsDummyElement> sdk = JavaBuilderUtil.ensureModuleHasJdk(module, context, NAME_SUPPLIER.get());
    context.processMessage(new ProgressMessage(
      JavaGoogleAppEngineJpsBundle.message("enhancing.classes.in.module.0", module.getName())
    ));

    List<String> vmParams = Collections.singletonList("-Xmx256m");

    List<String> classpath = new ArrayList<>();
    classpath.add(extension.getToolsApiJarPath());
    classpath.add(PathManager.getJarPathForClass(EnhancerRunner.class));
    boolean removeOrmJars = Boolean.parseBoolean(System.getProperty("jps.appengine.enhancer.remove.orm.jars", "true"));
    for (File file : JpsJavaExtensionService.dependencies(module).recursively().compileOnly().productionOnly().classes().getRoots()) {
      if (removeOrmJars && FileUtil.isAncestor(new File(extension.getOrmLibPath()), file, true)) {
        continue;
      }
      classpath.add(file.getAbsolutePath());
    }

    List<String> programParams = new ArrayList<>();
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
                                                                        Collections.emptyList(), classpath, vmParams, programParams);

    Process process = new ProcessBuilder(commandLine).start();
    ExternalEnhancerProcessHandler handler = new ExternalEnhancerProcessHandler(process, commandLine, context);
    handler.startNotify();
    handler.waitFor();
    ProjectBuilderLogger logger = context.getLoggingManager().getProjectBuilderLogger();
    if (logger.isEnabled()) {
      logger.logCompiledPaths(pathsToProcess, NAME_SUPPLIER.get(), "Enhancing classes:");
    }
    return true;
  }


  @NotNull
  @Override
  public String getPresentableName() {
    //noinspection DialogTitleCapitalization
    return NAME_SUPPLIER.get();
  }

  private static class ExternalEnhancerProcessHandler extends EnhancerProcessHandlerBase {
    private final CompileContext myContext;

    ExternalEnhancerProcessHandler(Process process, List<String> commandLine, CompileContext context) {
      super(process, ParametersListUtil.join(commandLine), null);
      myContext = context;
    }

    @Override
    protected void reportInfo(String message) {
      //noinspection DialogTitleCapitalization
      myContext.processMessage(new CompilerMessage(NAME_SUPPLIER.get(), BuildMessage.Kind.INFO, message));
    }

    @Override
    protected void reportError(String message) {
      //noinspection DialogTitleCapitalization
      myContext.processMessage(new CompilerMessage(NAME_SUPPLIER.get(), BuildMessage.Kind.ERROR, message));
    }
  }
}
