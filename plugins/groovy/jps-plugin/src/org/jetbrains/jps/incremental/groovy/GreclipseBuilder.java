// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.groovy.compiler.rt.OutputItem;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Future;

public final class GreclipseBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(GreclipseBuilder.class);
  private static final Key<Boolean> COMPILER_VERSION_INFO = Key.create("_greclipse_compiler_info_");
  public static final @NlsSafe String ID = "Groovy-Eclipse";
  private static final Object ourGlobalEnvironmentLock = new String("GreclipseBuilder lock");

  private String myGreclipseJar;
  /**
   * All Groovy-Eclipse stuff is contained in a separate classLoader to avoid clashes with ecj.jar being in the classpath of the builder process
   */
  private ClassLoader myGreclipseLoader;
  private final CompilingGroovycRunner myHelper = new CompilingGroovycRunner(true) {
    @Override
    protected boolean acceptsFileType(String path) {
      return super.acceptsFileType(path) || path.endsWith(".java");
    }
  };

  GreclipseBuilder() {
    super(BuilderCategory.TRANSLATOR);
  }


  private @Nullable ClassLoader createGreclipseLoader(@Nullable String jar) {
    if (StringUtil.isEmpty(jar)) return null;

    if (jar.equals(myGreclipseJar)) {
      return myGreclipseLoader;
    }

    try {
      URL[] urls = {
        new File(jar).toURI().toURL(),
        Objects.requireNonNull(PathManager.getJarForClass(GreclipseMain.class)).toUri().toURL()
      };
      ClassLoader loader = new URLClassLoader(urls, StandardJavaFileManager.class.getClassLoader());
      Class.forName("org.eclipse.jdt.internal.compiler.batch.Main", false, loader);
      myGreclipseJar = jar;
      myGreclipseLoader = loader;
      return loader;
    }
    catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }


  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return Arrays.asList("groovy", "java");
  }

  @Override
  public ExitCode build(final CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException {
    if (!useGreclipse(context)) return ModuleLevelBuilder.ExitCode.NOTHING_DONE;

    try {
      List<File> toCompile = myHelper.collectChangedFiles(context, dirtyFilesHolder, false, Ref.create(false));
      if (toCompile.isEmpty()) {
        return ExitCode.NOTHING_DONE;
      }

      Map<ModuleBuildTarget, String> outputDirs = GroovyBuilder.getCanonicalModuleOutputs(context, chunk, this);
      if (outputDirs == null) {
        return ExitCode.ABORT;
      }

      JpsProject project = context.getProjectDescriptor().getProject();
      GreclipseSettings greclipseSettings = GreclipseJpsCompilerSettings.getSettings(project);
      if (greclipseSettings == null) {
        String message = GroovyJpsBundle.message("greclipse.not.initialized.for.project.0", project);
        LOG.error(message);
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, message));
        return ExitCode.ABORT;
      }

      ClassLoader loader = createGreclipseLoader(greclipseSettings.greclipsePath);
      if (loader == null) {
        context.processMessage(new CompilerMessage(
          getPresentableName(), BuildMessage.Kind.ERROR,
          GroovyJpsBundle.message("greclipse.invalid.jar.path.0", greclipseSettings.greclipsePath)
        ));
        return ExitCode.ABORT;
      }

      final Set<JpsModule> modules = chunk.getModules();
      ProcessorConfigProfile profile = null;
      if (modules.size() == 1) {
        final JpsJavaCompilerConfiguration compilerConfig = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project);
        assert compilerConfig != null;
        profile = compilerConfig.getAnnotationProcessingProfile(modules.iterator().next());
      }
      else {
        final String message = JavaBuilder.validateCycle(context, chunk);
        if (message != null) {
          context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, message));
          return ExitCode.ABORT;
        }
      }

      String mainOutputDir = outputDirs.get(chunk.representativeTarget());
      final List<String> args = createCommandLine(context, chunk, toCompile, mainOutputDir, profile, greclipseSettings);
      final List<String> vmOptions = discoverVmOptions(chunk, greclipseSettings.cmdLineParams, greclipseSettings.vmOptions);
      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.debug("Compiling with args: " + args);
      }

      Boolean notified = COMPILER_VERSION_INFO.get(context);
      if (notified != Boolean.TRUE) {
        context.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, GroovyJpsBundle.message("greclipse.info")));
        COMPILER_VERSION_INFO.set(context, Boolean.TRUE);
      }

      context.processMessage(new ProgressMessage(GroovyJpsBundle.message("greclipse.compiling.chunk.0", chunk.getPresentableShortName())));

      StringWriter out = new StringWriter();
      StringWriter err = new StringWriter();
      HashMap<String, List<String>> outputMap = new HashMap<>();

      boolean success = performCompilation(vmOptions, args, out, err, outputMap, context, chunk);

      List<OutputItem> items = new ArrayList<>();
      for (String src : outputMap.keySet()) {
        for (String classFile : outputMap.get(src)) {
          items.add(new OutputItem(FileUtil.toSystemIndependentName(mainOutputDir + classFile),
                                   FileUtil.toSystemIndependentName(src)));
        }
      }
      MultiMap<ModuleBuildTarget, OutputItem> successfullyCompiled =
        myHelper.processCompiledFiles(context, chunk, outputDirs, mainOutputDir, items);

      EclipseOutputParser parser = new EclipseOutputParser(getPresentableName(), chunk);
      List<CompilerMessage> messages = ContainerUtil.concat(parser.parseMessages(out.toString()), parser.parseMessages(err.toString()));
      boolean hasError = false;
      for (CompilerMessage message : messages) {
        if (message.getKind() == BuildMessage.Kind.ERROR) {
          hasError = true;
        }
        context.processMessage(message);
      }

      if (!success && !hasError) {
        context.processMessage(new CompilerMessage(
          getPresentableName(), BuildMessage.Kind.ERROR,
          GroovyJpsBundle.message("greclipse.compilation.failed")
        ));
      }

      myHelper.updateDependencies(context, toCompile, successfullyCompiled, new DefaultOutputConsumer(outputConsumer), this);
      return ExitCode.OK;

    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  static boolean useGreclipse(CompileContext context) {
    JpsProject project = context.getProjectDescriptor().getProject();
    return ID.equals(JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).getJavaCompilerId());
  }

  private boolean performCompilation(List<String> vmOptions,
                                     List<String> args,
                                     StringWriter out,
                                     StringWriter err,
                                     Map<String, List<String>> outputs,
                                     CompileContext context,
                                     ModuleChunk chunk) {
    String bytecodeTarget = JpsGroovycRunner.getBytecodeTarget(context, chunk);
    if (bytecodeTarget != null && System.getProperty(GroovyRtConstants.GROOVY_TARGET_BYTECODE) == null) {
      synchronized (ourGlobalEnvironmentLock) {
        try {
          System.setProperty(GroovyRtConstants.GROOVY_TARGET_BYTECODE, bytecodeTarget);
          return performCompilationInner(vmOptions, args, out, err, outputs, context, chunk);
        }
        finally {
          System.clearProperty(GroovyRtConstants.GROOVY_TARGET_BYTECODE);
        }
      }
    }

    return performCompilationInner(vmOptions, args, out, err, outputs, context, chunk);
  }

  private boolean performCompilationInner(List<String> vmOptions,
                                          List<String> args,
                                          StringWriter out,
                                          StringWriter err,
                                          Map<String, List<String>> outputs,
                                          CompileContext context,
                                          ModuleChunk chunk) {
    final ClassLoader jpsLoader = Thread.currentThread().getContextClassLoader();
    try {
      // We have to set context class loader in order because greclipse will create child GroovyClassLoader,
      // and will use context class loader as parent.
      //
      // Here's what happens if we leave jpsLoader:
      // 1. org.codehaus.groovy.transform.ASTTransformationCollectorCodeVisitor
      //    is loaded with GreclipseMain's class loader, i.e. myGreclipseLoader;
      // 2. org.codehaus.groovy.transform.ASTTransformation inside ASTTransformationCollectorCodeVisitor.verifyClass
      //    is loaded with ASTTransformationCollectorCodeVisitor' loader, i.e. myGreclipseLoader;
      // 3. transformation GroovyClassLoader is created with context class loader (jpsLoader) as a parent;
      // 4. some CoolTransform implements ASTTransformation is loaded with GroovyClassLoader;
      // 5. ASTTransformation supertype of CoolTransform is loaded with GroovyClassLoader too;
      // 6. GroovyClassLoader asks its parent, which is jpsLoader, it doesn't know about ASTTransformation
      //    => GroovyClassLoader loads ASTTransformation by itself;
      // 7. there are two different ASTTransformation class instances
      //    => we get ASTTransformation.class.isAssignableFrom(klass) = false
      //    => compilation fails with error.
      //
      // If we set context classloader here, then in the 6th step parent loader will be myGreclipseLoader,
      // and ASTTransformation class will be returned from myGreclipseLoader, and the compilation won't fail.
      Thread.currentThread().setContextClassLoader(myGreclipseLoader);

      if (vmOptions.isEmpty()) {
        Class<?> mainClass = Class.forName(GreclipseMain.class.getName(), true, myGreclipseLoader);
        Constructor<?> constructor = mainClass.getConstructor(PrintWriter.class, PrintWriter.class, Map.class);
        Method compileMethod = mainClass.getMethod("compile", String[].class);
        Object main = constructor.newInstance(new PrintWriter(out), new PrintWriter(err), outputs);
        return (Boolean)compileMethod.invoke(main, new Object[]{ArrayUtilRt.toStringArray(args)});
      }
      else {
        final List<String> cmd = ExternalProcessUtil.buildJavaCommandLine(
          ForkedGroovyc.getJavaExecutable(chunk),
          "org.jetbrains.jps.incremental.groovy.GreclipseMain",
          Collections.emptyList(), Arrays.asList(myGreclipseJar,
                                                 Objects.requireNonNull(PathManager.getJarForClass(GreclipseMain.class)).toAbsolutePath()
                                                   .toString()),
          vmOptions,
          args
        );
        final Process process = Runtime.getRuntime().exec(ArrayUtilRt.toStringArray(cmd));
        ProcessHandler handler = new BaseOSProcessHandler(process, StringUtil.join(cmd, " "), null) {
          @Override
          public @NotNull Future<?> executeTask(@NotNull Runnable task) {
            return SharedThreadPool.getInstance().submit(task);
          }

          @Override
          public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
            if (outputType == ProcessOutputType.STDERR) {
              err.append(text);
            }
            if (outputType == ProcessOutputType.STDOUT) {
              out.append(text);
            }
          }
        };
        handler.startNotify();
        handler.waitFor();
        return true;
      }
    }
    catch (Exception e) {
      context.processMessage(CompilerMessage.createInternalBuilderError(getPresentableName(), e));
      return false;
    }
    finally {
      Thread.currentThread().setContextClassLoader(jpsLoader);
    }
  }

  private static List<String> createCommandLine(CompileContext context,
                                                ModuleChunk chunk,
                                                List<File> srcFiles,
                                                String mainOutputDir, @Nullable ProcessorConfigProfile profile, GreclipseSettings settings) {
    final List<String> args = new ArrayList<>();

    args.add("-cp");
    args.add(getClasspathString(chunk));

    JavaBuilder.addCompilationOptions(args, context, chunk, profile);

    args.add("-d");
    args.add(mainOutputDir);

    //todo AjCompilerSettings exact duplicate, JavaBuilder.loadCommonJavacOptions inexact duplicate
    List<String> params = ParametersListUtil.parse(settings.cmdLineParams);
    for (Iterator<String> iterator = params.iterator(); iterator.hasNext(); ) {
      String option = iterator.next();
      if ("-javaAgentClass".equals(option)) {
        iterator.next();
        continue;
      }
      if ("-target".equals(option)) {
        iterator.next();
        continue;
      }
      else if (option.isEmpty() || "-g".equals(option) || "-verbose".equals(option)) {
        continue;
      }
      args.add(option);
    }

    if (settings.debugInfo) {
      args.add("-g");
    }

    for (File file : srcFiles) {
      args.add(file.getPath());
    }

    return args;
  }

  private static List<String> discoverVmOptions(ModuleChunk chunk, String args, String rawVmOptions) {
    List<String> params = ParametersListUtil.parse(args);
    List<String> vmOptions = new ArrayList<>(ParametersListUtil.parse(rawVmOptions));
    for (Iterator<String> iterator = params.iterator(); iterator.hasNext(); ) {
      String option = iterator.next();
      if ("-javaAgentClass".equals(option)) {
        String agentClass = iterator.next();
        vmOptions.add("-javaagent:" + locateAgentJar(chunk, agentClass));
      }
    }
    return vmOptions;
  }

  /**
   * @see <a href="https://github.com/groovy/groovy-eclipse/blob/18133707880e2169b7e3c0666e845dd008364d69/extras/groovy-eclipse-compiler/src/main/java/org/codehaus/groovy/eclipse/compiler/GroovyEclipseCompiler.java#L560">groovy-eclipse approach</a>
   */
  private static String locateAgentJar(ModuleChunk chunk, String agentClassName) {
    Collection<File> files = ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false);
    URL[] urls = new URL[files.size()];
    int i = 0;
    for (File file : files) {
      try {
        urls[i] = file.toURI().toURL();
        ++i;
      }
      catch (MalformedURLException e) {
        LOG.warn("Malformed dependency: " + file);
      }
    }
    try (URLClassLoader dependenciesLoader = new URLClassLoader(urls, StandardJavaFileManager.class.getClassLoader())) {
      Class<?> agentClass = Class.forName(agentClassName, false, dependenciesLoader);
      URL agentUrl = agentClass.getProtectionDomain().getCodeSource().getLocation();
      File agentClassFile = new File(URLDecoder.decode(agentUrl.getPath(), StandardCharsets.UTF_8));
      return agentClassFile.getAbsolutePath();
    }
    catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getClasspathString(ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<>();
    for (File file : ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false)) {
      if (file.exists()) {
        cp.add(FileUtil.toCanonicalPath(file.getPath()));
      }
    }
    return StringUtil.join(cp, File.pathSeparator);
  }

  @Override
  public @NotNull String getPresentableName() {
    return GroovyJpsBundle.message("compiler.name.greclipse");
  }

  @Override
  public long getExpectedBuildTime() {
    return 100;
  }
}
