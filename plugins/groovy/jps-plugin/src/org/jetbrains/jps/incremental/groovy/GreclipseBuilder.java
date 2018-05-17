/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author peter
 */
public class GreclipseBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GreclipseBuilder");
  private static final Key<Boolean> COMPILER_VERSION_INFO = Key.create("_greclipse_compiler_info_");
  public static final String ID = "Groovy-Eclipse";

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

  protected GreclipseBuilder() {
    super(BuilderCategory.TRANSLATOR);
  }


  @Nullable
  private ClassLoader createGreclipseLoader(@Nullable String jar) {
    if (StringUtil.isEmpty(jar)) return null;
    
    if (jar.equals(myGreclipseJar)) {
      return myGreclipseLoader;
    }

    try {
      URL[] urls = {
        new File(jar).toURI().toURL(),
        new File(ObjectUtils.assertNotNull(PathManager.getJarPathForClass(GreclipseMain.class))).toURI().toURL()
      };
      ClassLoader loader = new URLClassLoader(urls, null);
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
  public List<String> getCompilableFileExtensions() {
    return Arrays.asList("groovy", "java");
  }

  @Override
  public ExitCode build(final CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
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
        String message = "Compiler settings component not initialized for " + project;
        LOG.error(message);
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, message));
        return ExitCode.ABORT;
      }

      ClassLoader loader = createGreclipseLoader(greclipseSettings.greclipsePath);
      if (loader == null) {
        context.processMessage(new CompilerMessage(
          getPresentableName(), BuildMessage.Kind.ERROR, "Invalid jar path in the compiler settings: '" + greclipseSettings.greclipsePath + "'")
        );
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

      if (Utils.IS_TEST_MODE || LOG.isDebugEnabled()) {
        LOG.debug("Compiling with args: " + args);
      }

      Boolean notified = COMPILER_VERSION_INFO.get(context);
      if (notified != Boolean.TRUE) {
        context.processMessage(new CompilerMessage("", BuildMessage.Kind.INFO, "Using Groovy-Eclipse to compile Java & Groovy sources"));
        COMPILER_VERSION_INFO.set(context, Boolean.TRUE);
      }

      context.processMessage(new ProgressMessage("Compiling java & groovy [" + chunk.getPresentableShortName() + "]"));

      StringWriter out = new StringWriter();
      StringWriter err = new StringWriter();
      HashMap<String, List<String>> outputMap = ContainerUtil.newHashMap();

      boolean success = performCompilation(args, out, err, outputMap, context, chunk);
      
      List<GroovycOutputParser.OutputItem> items = ContainerUtil.newArrayList();
      for (String src : outputMap.keySet()) {
        //noinspection ConstantConditions
        for (String classFile : outputMap.get(src)) {
          items.add(new GroovycOutputParser.OutputItem(FileUtil.toSystemIndependentName(mainOutputDir + classFile),
                                                           FileUtil.toSystemIndependentName(src)));
        }
      }
      MultiMap<ModuleBuildTarget, GroovycOutputParser.OutputItem> successfullyCompiled =
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
        context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, "Compilation failed"));
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
    return ID.equals(JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getJavaCompilerId());
  }

  private boolean performCompilation(List<String> args, StringWriter out, StringWriter err, Map<String, List<String>> outputs, CompileContext context, ModuleChunk chunk) {
    String bytecodeTarget = JpsGroovycRunner.getBytecodeTarget(context, chunk);
    if (bytecodeTarget != null) {
      System.setProperty(JpsGroovycRunner.GROOVY_TARGET_BYTECODE, bytecodeTarget);
    }

    try {
      Class<?> mainClass = Class.forName(GreclipseMain.class.getName(), true, myGreclipseLoader);
      Constructor<?> constructor = mainClass.getConstructor(PrintWriter.class, PrintWriter.class, Map.class, Map.class);
      Method compileMethod = mainClass.getMethod("compile", String[].class);

      HashMap<String, Object> customDefaultOptions = ContainerUtil.newHashMap();
      // without this greclipse won't load AST transformations
      customDefaultOptions.put("org.eclipse.jdt.core.compiler.groovy.groovyClassLoaderPath", getClasspathString(chunk));

      // used by greclipse to cache transform loaders
      // names should be different for production & tests
      customDefaultOptions.put("org.eclipse.jdt.core.compiler.groovy.groovyProjectName", chunk.getPresentableShortName());

      Object main = constructor.newInstance(new PrintWriter(out), new PrintWriter(err), customDefaultOptions, outputs);
      return (Boolean)compileMethod.invoke(main, new Object[]{ArrayUtil.toStringArray(args)});
    }
    catch (Exception e) {
      context.processMessage(new CompilerMessage(getPresentableName(), e));
      return false;
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

  private static String getClasspathString(ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<>();
    for (File file : ProjectPaths.getCompilationClasspathFiles(chunk, chunk.containsTests(), false, false)) {
      if (file.exists()) {
        cp.add(FileUtil.toCanonicalPath(file.getPath()));
      }
    }
    return StringUtil.join(cp, File.pathSeparator);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return ID;
  }
}
