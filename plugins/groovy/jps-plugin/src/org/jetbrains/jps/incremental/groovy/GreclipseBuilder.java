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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
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
import java.util.*;

/**
 * @author peter
 */
public class GreclipseBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GreclipseBuilder");
  private static final Key<Boolean> COMPILER_VERSION_INFO = Key.create("_greclipse_compiler_info_");
  private final ClassLoader myGreclipseLoader;

  protected GreclipseBuilder(@NotNull ClassLoader greclipseLoader) {
    super(BuilderCategory.TRANSLATOR);
    myGreclipseLoader = greclipseLoader;
  }

  @Override
  public List<String> getCompilableFileExtensions() {
    return Arrays.asList("groovy", "java");
  }

  @Override
  public void buildStarted(CompileContext context) {
    JavaBuilder.IS_ENABLED.set(context, Boolean.FALSE);
  }

  @Override
  public ExitCode build(final CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws ProjectBuildException, IOException {
    try {
      final List<File> toCompile = GroovyBuilder.collectChangedFiles(context, dirtyFilesHolder, false, true);
      if (toCompile.isEmpty()) {
        return ExitCode.NOTHING_DONE;
      }

      Map<ModuleBuildTarget, String> outputDirs = GroovyBuilder.getCanonicalModuleOutputs(context, chunk, this);
      if (outputDirs == null) {
        return ExitCode.ABORT;
      }

      final JpsJavaExtensionService javaExt = JpsJavaExtensionService.getInstance();
      final JpsJavaCompilerConfiguration compilerConfig = javaExt.getCompilerConfiguration(context.getProjectDescriptor().getProject());
      assert compilerConfig != null;

      final Set<JpsModule> modules = chunk.getModules();
      ProcessorConfigProfile profile = null;
      if (modules.size() == 1) {
        profile = compilerConfig.getAnnotationProcessingProfile(modules.iterator().next());
      }
      else {
        String message = JavaBuilder.validateCycle(chunk, javaExt, compilerConfig, modules);
        if (message != null) {
          context.processMessage(new CompilerMessage(getPresentableName(), BuildMessage.Kind.ERROR, message));
          return ExitCode.ABORT;
        }
      }


      String mainOutputDir = outputDirs.get(chunk.representativeTarget());
      final List<String> args = createCommandLine(context, chunk, toCompile, mainOutputDir, profile);

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
      
      List<GroovycOSProcessHandler.OutputItem> items = ContainerUtil.newArrayList();
      for (String src : outputMap.keySet()) {
        //noinspection ConstantConditions
        for (String classFile : outputMap.get(src)) {
          items.add(new GroovycOSProcessHandler.OutputItem(FileUtil.toSystemIndependentName(mainOutputDir + classFile),
                                                           FileUtil.toSystemIndependentName(src)));
        }
      }
      Map<ModuleBuildTarget, Collection<GroovycOSProcessHandler.OutputItem>> successfullyCompiled =
        GroovyBuilder.processCompiledFiles(context, chunk, outputDirs, mainOutputDir, items);

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

      if (GroovyBuilder.updateDependencies(context, chunk, dirtyFilesHolder, toCompile, successfullyCompiled, outputConsumer, this)) {
        return ExitCode.ADDITIONAL_PASS_REQUIRED;
      }
      return ExitCode.OK;

    }
    catch (Exception e) {
      throw new ProjectBuildException(e);
    }
  }

  private boolean performCompilation(List<String> args, StringWriter out, StringWriter err, Map<String, List<String>> outputs, CompileContext context, ModuleChunk chunk) {
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
                                                String mainOutputDir, ProcessorConfigProfile profile) {
    final List<String> args = new ArrayList<String>();

    args.add("-cp");
    args.add(getClasspathString(chunk));

    JavaBuilder.addCompilationOptions(args, context, chunk, profile);

    args.add("-d");
    args.add(mainOutputDir);

    for (File file : srcFiles) {
      args.add(file.getPath());
    }

    return args;
  }

  private static String getClasspathString(ModuleChunk chunk) {
    final Set<String> cp = new LinkedHashSet<String>();
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
    return "Groovy-Eclipse";
  }
}
