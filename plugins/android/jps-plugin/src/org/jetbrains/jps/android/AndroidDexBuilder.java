package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidDxRunner;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.server.ClasspathBootstrap;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
// todo: save validity state
// todo: support light builds (for tests)

public class AndroidDexBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidDexBuilder");

  @NonNls private static final String BUILDER_NAME = "android-dex";

  protected AndroidDexBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    if (context.isCompilingTests() || !AndroidJpsUtil.containsAndroidFacet(chunk)) {
      return ModuleLevelBuilder.ExitCode.OK;
    }
    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.dex")));

    try {
      return doBuild(context, chunk);
    }
    catch (Exception e) {
      return AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static ExitCode doBuild(CompileContext context, ModuleChunk chunk) {
    boolean success = true;

    for (Module module : chunk.getModules()) {
      final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
      if (facet == null || facet.getLibrary()) {
        continue;
      }

      final Pair<AndroidSdk, IAndroidTarget> pair = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (pair == null) {
        success = false;
        continue;
      }
      final AndroidSdk androidSdk = pair.getFirst();
      final IAndroidTarget target = pair.getSecond();

      final ProjectPaths projectPaths = context.getProjectPaths();
      final File dexOutputDir = AndroidJpsUtil.getOutputDirectoryForPackagedFiles(projectPaths, module);

      if (dexOutputDir == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.errors.output.dir.not.specified", module.getName())));
        success = false;
        continue;
      }

      // todo: support proguard

      final File classesDir = projectPaths.getModuleOutputDir(module, false);

      if (classesDir == null || !classesDir.isDirectory()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, AndroidJpsBundle
          .message("android.jps.warnings.dex.no.compiled.files", module.getName())));
        continue;
      }

      final Set<String> fileSet = new HashSet<String>();
      AndroidJpsUtil.addSubdirectories(classesDir, fileSet);
      fileSet.addAll(AndroidJpsUtil.getExternalLibraries(projectPaths, module));

      for (String filePath : AndroidJpsUtil.getClassdirsOfDependentModules(projectPaths, module)) {
        if (!classesDir.getPath().equals(filePath)) {
          fileSet.add(filePath);
        }
      }

      if (facet.isLibrary()) {
        final File testsClassDir = projectPaths.getModuleOutputDir(module, true);

        if (testsClassDir != null && testsClassDir.isDirectory()) {
          AndroidJpsUtil.addSubdirectories(testsClassDir, fileSet);
        }
      }

      final String[] files = new String[fileSet.size()];
      int i = 0;
      for (String filePath : fileSet) {
        files[i++] = FileUtil.toSystemDependentName(filePath);
      }

      if (!runDex(androidSdk, target, dexOutputDir.getPath(), files, context)) {
        success = false;
      }
    }
    return success ? ExitCode.OK : ExitCode.ABORT;
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return "Android Dex Builder";
  }

  public static boolean runDex(@NotNull AndroidSdk sdk,
                               @NotNull IAndroidTarget target,
                               @NotNull String outputDir,
                               @NotNull String[] compileTargets,
                               @NotNull CompileContext context) {
    @SuppressWarnings("deprecation")
    final String dxJarPath = FileUtil.toSystemDependentName(target.getPath(IAndroidTarget.DX_JAR));

    final File dxJar = new File(dxJarPath);
    if (!dxJar.isFile()) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot find file " + dxJarPath));
      return false;
    }

    final String outFilePath = outputDir + File.separatorChar + AndroidCommonUtils.CLASSES_FILE_NAME;

    final List<String> programParamList = new ArrayList<String>();
    programParamList.add(dxJarPath);
    programParamList.add(outFilePath);
    programParamList.addAll(Arrays.asList(compileTargets));
    programParamList.add("--exclude");

    final List<String> classPath = new ArrayList<String>();
    classPath.add(ClasspathBootstrap.getResourcePath(AndroidDxRunner.class).getPath());
    classPath.add(ClasspathBootstrap.getResourcePath(FileUtil.class).getPath());

    final File outFile = new File(outFilePath);
    if (outFile.exists() && !outFile.delete()) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING,
                                                 AndroidJpsBundle.message("android.jps.errors.cannot.delete.file", outFilePath)));
    }

    // todo: pass additional vm params and max heap size from settings

    final List<String> commandLine = ExternalProcessUtil
      .buildJavaCommandLine(sdk.getJavaExecutable(), AndroidDxRunner.class.getName(), Collections.<String>emptyList(), classPath,
                            Arrays.asList("-Xmx1024M"), programParamList);

    LOG.info(AndroidCommonUtils.command2string(commandLine));

    try {
      final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(commandLine));

      final HashMap<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>(3);
      messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
      messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());
      messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

      AndroidCommonUtils.handleDexCompilationResult(process, outFilePath, messages);

      AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);

      return messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
  }
}
