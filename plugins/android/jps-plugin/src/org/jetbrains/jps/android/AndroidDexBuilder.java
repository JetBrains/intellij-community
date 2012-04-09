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
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ExternalProcessUtil;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;
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
public class AndroidDexBuilder extends ProjectLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidDexBuilder");

  @NonNls private static final String BUILDER_NAME = "android-dex";

  @Override
  public void build(CompileContext context) throws ProjectBuildException {
    if (!AndroidJpsUtil.containsAndroidFacet(context.getProject()) || AndroidJpsUtil.isLightBuild(context)) {
      return;
    }

    try {
      doBuild(context);
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static void doBuild(CompileContext context) throws IOException, ProjectBuildException {
    final File root = context.getDataManager().getDataStorageRoot();

    AndroidFileSetStorage dexStateStorage = null;
    AndroidFileSetStorage proguardStateStorage = null;
    try {
      dexStateStorage = new AndroidFileSetStorage(root, "dex");
      proguardStateStorage = new AndroidFileSetStorage(root, "proguard");

      if (!doDexBuild(context, dexStateStorage, proguardStateStorage)) {
        throw new ProjectBuildException();
      }
    }
    finally {
      if (proguardStateStorage != null) {
        proguardStateStorage.close();
      }
      if (dexStateStorage != null) {
        dexStateStorage.close();
      }
    }
  }

  private static boolean doDexBuild(@NotNull CompileContext context,
                                    @NotNull AndroidFileSetStorage dexStateStorage,
                                    @NotNull AndroidFileSetStorage proguardStateStorage) {
    boolean success = true;

    for (Module module : context.getProject().getModules().values()) {
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

      final File classesDir = projectPaths.getModuleOutputDir(module, false);
      if (classesDir == null || !classesDir.isDirectory()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, AndroidJpsBundle
          .message("android.jps.warnings.dex.no.compiled.files", module.getName())));
        continue;
      }
      final Set<String> externalLibraries = AndroidJpsUtil.getExternalLibraries(projectPaths, module);

      // todo: read proguard options from Android facet settings if there is no settings in the context

      final String proguardCfgPath = context.getBuilderParameter(AndroidCommonUtils.PROGUARD_CFG_PATH_OPTION);
      final String includeSystemProguardCfgOption = context.getBuilderParameter(AndroidCommonUtils.INCLUDE_SYSTEM_PROGUARD_FILE_OPTION);
      final boolean includeSystemProguardCfg = Boolean.parseBoolean(includeSystemProguardCfgOption);
      final Set<String> fileSet;

      try {
        if (proguardCfgPath != null) {
          final String outputJarPath =
            FileUtil.toSystemDependentName(dexOutputDir.getPath() + '/' + AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME);

          if (!runProguardIfNecessary(facet, classesDir, androidSdk, target, externalLibraries, context,
                                      outputJarPath, proguardCfgPath, includeSystemProguardCfg, proguardStateStorage)) {
            success = false;
            continue;
          }
          fileSet = Collections.singleton(outputJarPath);
        }
        else {
          fileSet = new HashSet<String>();
          AndroidJpsUtil.addSubdirectories(classesDir, fileSet);
          fileSet.addAll(externalLibraries);

          for (String filePath : AndroidJpsUtil.getClassdirsOfDependentModulesAndPackagesLibraries(projectPaths, module)) {
            if (!classesDir.getPath().equals(filePath)) {
              fileSet.add(filePath);
            }
          }

          if (facet.isPackTestCode()) {
            final File testsClassDir = projectPaths.getModuleOutputDir(module, true);

            if (testsClassDir != null && testsClassDir.isDirectory()) {
              AndroidJpsUtil.addSubdirectories(testsClassDir, fileSet);
            }
          }
        }
        final AndroidFileSetState newState = new AndroidFileSetState(fileSet, AndroidJpsUtil.CLASSES_AND_JARS_FILTER, true);

        if (context.isMake()) {
          final AndroidFileSetState oldState = dexStateStorage.getState(module.getName());
          if (oldState != null && oldState.equalsTo(newState)) {
            continue;
          }
        }

        if (fileSet.size() == 0) {
          continue;
        }

        final String[] files = new String[fileSet.size()];
        int i = 0;
        for (String filePath : fileSet) {
          files[i++] = FileUtil.toSystemDependentName(filePath);
        }

        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.dex", module.getName())));

        if (!runDex(androidSdk, target, dexOutputDir.getPath(), files, context)) {
          success = false;
          dexStateStorage.update(module.getName(), null);
        }
        else {
          dexStateStorage.update(module.getName(), newState);
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
        return false;
      }
    }
    return success;
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return "Android Dex Builder";
  }

  private static boolean runDex(@NotNull AndroidSdk sdk,
                                @NotNull IAndroidTarget target,
                                @NotNull String outputDir,
                                @NotNull String[] compileTargets,
                                @NotNull CompileContext context) throws IOException {
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

    final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(commandLine));

    final HashMap<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>(3);
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

    AndroidCommonUtils.handleDexCompilationResult(process, outFilePath, messages);

    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);

    return messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;
  }

  private static boolean runProguardIfNecessary(@NotNull AndroidFacet facet,
                                                @NotNull File classesDir,
                                                @NotNull AndroidSdk sdk,
                                                @NotNull IAndroidTarget target,
                                                @NotNull Set<String> externalJars,
                                                @NotNull CompileContext context,
                                                @NotNull String outputJarPath,
                                                @NotNull String proguardCfgPath,
                                                boolean includeSystemProguardCfg,
                                                @NotNull AndroidFileSetStorage proguardStateStorage) throws IOException {
    final Module module = facet.getModule();

    final File proguardCfgFile = new File(proguardCfgPath);
    if (!proguardCfgFile.exists()) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot find file " + proguardCfgPath));
      return false;
    }

    final File mainContentRoot = AndroidJpsUtil.getMainContentRoot(facet);
    if (mainContentRoot == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.main.content.root.not.found", module.getName())));
      return false;
    }

    final ProjectPaths paths = context.getProjectPaths();
    final Set<String> classFilesDirs = new HashSet<String>();
    final Set<String> libClassFilesDirs = new HashSet<String>();

    AndroidJpsUtil.addSubdirectories(classesDir, classFilesDirs);

    for (String depPath : AndroidJpsUtil.getClassdirsOfDependentModulesAndPackagesLibraries(paths, module)) {
      final File depFile = new File(depPath);
      if (depFile.isDirectory()) {
        AndroidJpsUtil.addSubdirectories(depFile, classFilesDirs);
      }
      else {
        AndroidJpsUtil.addSubdirectories(depFile.getParentFile(), libClassFilesDirs);
      }
    }

    final String logsDirOsPath =
          FileUtil.toSystemDependentName(mainContentRoot.getPath() + '/' + AndroidCommonUtils.DIRECTORY_FOR_LOGS_NAME);

    final Set<String> allFiles = new HashSet<String>();
    allFiles.addAll(classFilesDirs);
    allFiles.addAll(libClassFilesDirs);
    allFiles.addAll(externalJars);

    final AndroidFileSetState newState = new AndroidFileSetState(allFiles, AndroidJpsUtil.CLASSES_AND_JARS_FILTER, true);
    final AndroidFileSetState oldState = proguardStateStorage.getState(module.getName());
    if (context.getTimestampStorage().getStamp(proguardCfgFile) == proguardCfgFile.lastModified() &&
        newState.equalsTo(oldState)) {
      return true;
    }

    final String[] classFilesDirOsPaths = ArrayUtil.toStringArray(classFilesDirs);
    final String[] libClassFilesDirOsPaths = ArrayUtil.toStringArray(libClassFilesDirs);
    final String[] externalJarOsPaths = ArrayUtil.toStringArray(externalJars);
    final String inputJarOsPath = AndroidCommonUtils.buildTempInputJar(classFilesDirOsPaths, libClassFilesDirOsPaths);

    final File logsDir = new File(logsDirOsPath);
    if (!logsDir.exists()) {
      if (!logsDir.mkdirs()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot create directory " + logsDirOsPath));
        return false;
      }
    }

    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.proguard", module.getName())));

    // todo: pass sdk revision
    final Map<AndroidCompilerMessageKind, List<String>> messages =
      AndroidCommonUtils.launchProguard(target, -1, sdk.getSdkPath(), proguardCfgPath, includeSystemProguardCfg, inputJarOsPath,
                                        externalJarOsPaths, outputJarPath, logsDirOsPath);

    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);
    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();

    proguardStateStorage.update(module.getName(), success ? newState : null);

    if (success) {
      context.getTimestampStorage().saveStamp(proguardCfgFile, proguardCfgFile.lastModified());
    }
    return success;
  }
}
