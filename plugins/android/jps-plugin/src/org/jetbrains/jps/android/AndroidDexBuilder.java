/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidDxRunner;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.builder.AndroidProjectBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDexBuilder extends TargetBuilder<BuildRootDescriptor,AndroidProjectBuildTarget> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidDexBuilder");

  @NonNls private static final String BUILDER_NAME = "android-dex";

  private static final Key<BuildListener> BUILD_LISTENER_KEY = Key.create("BUILD_LISTENER_KEY");
  public static final Key<Set<String>> DIRTY_OUTPUT_DIRS = Key.create("DIRTY_OUTPUT_DIRS");

  public AndroidDexBuilder() {
    super(Collections.singletonList(AndroidProjectBuildTarget.TargetType.DEX));
  }

  @Override
  public void build(@NotNull AndroidProjectBuildTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidProjectBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException {
    if (AndroidJpsUtil.isLightBuild(context)) {
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
    final File root = context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot();

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
                                    @NotNull AndroidFileSetStorage proguardStateStorage) throws IOException {
    boolean success = true;

    for (JpsModule module : context.getProjectDescriptor().getProject().getModules()) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension == null || extension.isLibrary()) {
        continue;
      }

      final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (platform == null) {
        success = false;
        continue;
      }

      final ProjectPaths projectPaths = context.getProjectPaths();
      File dexOutputDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
      dexOutputDir = AndroidJpsUtil.createDirIfNotExist(dexOutputDir, context, BUILDER_NAME);
      if (dexOutputDir == null) {
        success = false;
        continue;
      }

      final File classesDir = projectPaths.getModuleOutputDir(module, false);
      if (classesDir == null || !classesDir.isDirectory()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, AndroidJpsBundle
          .message("android.jps.warnings.dex.no.compiled.files", module.getName())));
        continue;
      }
      final Set<String> externalLibraries = AndroidJpsUtil.getExternalLibraries(context, module, platform);

      boolean includeSystemProguardCfg = false;
      String proguardCfgPath = context.getBuilderParameter(AndroidCommonUtils.PROGUARD_CFG_PATH_OPTION);

      if (proguardCfgPath != null) {
        final String includeSystemProguardCfgOption = context.getBuilderParameter(AndroidCommonUtils.INCLUDE_SYSTEM_PROGUARD_FILE_OPTION);
        includeSystemProguardCfg = Boolean.parseBoolean(includeSystemProguardCfgOption);
      }
      else if (extension.isRunProguard()) {
        final File proguardCfgFile = extension.getProguardConfigFile();
        if (proguardCfgFile == null) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                     AndroidJpsBundle.message("android.jps.errors.cannot.find.proguard.cfg", module.getName())));
          success = false;
          continue;
        }

        proguardCfgPath = proguardCfgFile != null ? proguardCfgFile.getPath() : null;
        includeSystemProguardCfg = extension.isIncludeSystemProguardCfgFile();
      }

      final Set<String> fileSet;
      final Set<String> jars;
      final Set<String> outputDirs;

      try {
        if (proguardCfgPath != null) {
          final File proguardCfgOutputFile = new File(dexOutputDir, AndroidCommonUtils.PROGUARD_CFG_OUTPUT_FILE_NAME);
          final String[] proguardCfgFilePaths = new String[] {proguardCfgPath, proguardCfgOutputFile.getPath()};
          final String outputJarPath =
            FileUtil.toSystemDependentName(dexOutputDir.getPath() + '/' + AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME);

          if (!runProguardIfNecessary(extension, classesDir, platform, externalLibraries, context, outputJarPath,
                                      proguardCfgFilePaths, includeSystemProguardCfg, proguardStateStorage)) {
            success = false;
            continue;
          }
          fileSet = jars = Collections.singleton(outputJarPath);
          outputDirs = Collections.emptySet();
        }
        else {
          fileSet = new HashSet<String>();
          jars = new HashSet<String>();
          outputDirs = new HashSet<String>();

          AndroidJpsUtil.addSubdirectories(classesDir, fileSet);
          outputDirs.add(classesDir.getPath());

          fileSet.addAll(externalLibraries);
          jars.addAll(externalLibraries);

          AndroidJpsUtil.processClasspath(context, module, new AndroidDependencyProcessor() {
            @Override
            public void processExternalLibrary(@NotNull File file) {
              fileSet.add(file.getPath());
              jars.add(file.getPath());
            }

            @Override
            public void processAndroidLibraryPackage(@NotNull File file) {
              fileSet.add(file.getPath());
              jars.add(file.getPath());
            }

            @Override
            public void processJavaModuleOutputDirectory(@NotNull File dir) {
              fileSet.add(dir.getPath());
              outputDirs.add(dir.getPath());
            }

            @Override
            public boolean isToProcess(@NotNull AndroidDependencyType type) {
              return type == AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR ||
                     type == AndroidDependencyType.ANDROID_LIBRARY_PACKAGE ||
                     type == AndroidDependencyType.EXTERNAL_LIBRARY;
            }
          });

          if (extension.isPackTestCode()) {
            final File testsClassDir = projectPaths.getModuleOutputDir(module, true);

            if (testsClassDir != null && testsClassDir.isDirectory()) {
              AndroidJpsUtil.addSubdirectories(testsClassDir, fileSet);
              outputDirs.add(testsClassDir.getPath());
            }
          }
        }
        final AndroidFileSetState newState = new AndroidFileSetState(jars, AndroidJpsUtil.CLASSES_AND_JARS_FILTER, true);

        if (context.isMake()) {
          final AndroidFileSetState oldState = dexStateStorage.getState(module.getName());
          if (oldState != null && oldState.equalsTo(newState)) {
            final Set<String> dirtyOutputDirs = context.getUserData(DIRTY_OUTPUT_DIRS);
            boolean outputDirsDirty = false;

            for (String outputDir : outputDirs) {
              if (dirtyOutputDirs.contains(outputDir)) {
                outputDirsDirty = true;
                break;
              }
            }
            if (!outputDirsDirty) {
              continue;
            }
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

        if (!runDex(platform, dexOutputDir.getPath(), files, context, module)) {
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

  @Override
  public void buildStarted(CompileContext context) {
    final HashSet<String> dirtyOutputDirs = new HashSet<String>();
    final BuildListener listener = new BuildListener() {
      @Override
      public void filesGenerated(Collection<Pair<String, String>> paths) {
        for (Pair<String, String> path : paths) {
          dirtyOutputDirs.add(path.first);
        }
      }

      @Override
      public void filesDeleted(Collection<String> paths) {
      }
    };
    context.putUserData(DIRTY_OUTPUT_DIRS, dirtyOutputDirs);
    context.putUserData(BUILD_LISTENER_KEY, listener);
    context.addBuildListener(listener);
  }

  @Override
  public void buildFinished(CompileContext context) {
    final BuildListener listener = context.getUserData(BUILD_LISTENER_KEY);
    assert listener != null;
    context.removeBuildListener(listener);
  }

  private static boolean runDex(@NotNull AndroidPlatform platform,
                                @NotNull String outputDir,
                                @NotNull String[] compileTargets,
                                @NotNull CompileContext context,
                                @NotNull JpsModule module) throws IOException {
    @SuppressWarnings("deprecation")
    final String dxJarPath = FileUtil.toSystemDependentName(platform.getTarget().getPath(IAndroidTarget.DX_JAR));

    final File dxJar = new File(dxJarPath);
    if (!dxJar.isFile()) {
      context.processMessage(
        new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.cannot.find.file", dxJarPath)));
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
    classPath.add(ClasspathBootstrap.getResourcePath(FileUtilRt.class).getPath());

    final File outFile = new File(outFilePath);
    if (outFile.exists() && !outFile.delete()) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING,
                                                 AndroidJpsBundle.message("android.jps.cannot.delete.file", outFilePath)));
    }

    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = platform.getSdk();
    final String jdkName = sdk.getSdkProperties().getData().getJdkName();
    final JpsLibrary javaSdk = context.getProjectDescriptor().getModel().getGlobal().getLibraryCollection().findLibrary(jdkName);
    if (javaSdk == null || !javaSdk.getType().equals(JpsJavaSdkType.INSTANCE)) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.errors.java.sdk.not.specified", jdkName)));
      return false;
    }

    // todo: pass additional vm params and max heap size from settings

    final List<String> commandLine = ExternalProcessUtil.buildJavaCommandLine(JpsJavaSdkType.getJavaExecutable((JpsSdk<?>)javaSdk.getProperties()),
                                                                               AndroidDxRunner.class.getName(),
                                                                               Collections.<String>emptyList(), classPath,
                                                                               Arrays.asList("-Xmx1024M"), programParamList);

    LOG.info(AndroidCommonUtils.command2string(commandLine));

    final Process process = Runtime.getRuntime().exec(ArrayUtil.toStringArray(commandLine));

    final HashMap<AndroidCompilerMessageKind, List<String>> messages = new HashMap<AndroidCompilerMessageKind, List<String>>(3);
    messages.put(AndroidCompilerMessageKind.ERROR, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.WARNING, new ArrayList<String>());
    messages.put(AndroidCompilerMessageKind.INFORMATION, new ArrayList<String>());

    AndroidCommonUtils.handleDexCompilationResult(process, outFilePath, messages);

    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, module.getName());

    return messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;
  }

  private static boolean runProguardIfNecessary(@NotNull JpsAndroidModuleExtension extension,
                                                @NotNull File classesDir,
                                                @NotNull AndroidPlatform platform,
                                                @NotNull Set<String> externalJars,
                                                @NotNull CompileContext context,
                                                @NotNull String outputJarPath,
                                                @NotNull String[] proguardCfgPaths,
                                                boolean includeSystemProguardCfg,
                                                @NotNull AndroidFileSetStorage proguardStateStorage) throws IOException {
    final JpsModule module = extension.getModule();
    final File[] proguardCfgFiles = new File[proguardCfgPaths.length];

    for (int i = 0; i < proguardCfgFiles.length; i++) {
      proguardCfgFiles[i] = new File(proguardCfgPaths[i]);

      if (!proguardCfgFiles[i].exists()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.find.file", proguardCfgPaths[i])));
        return false;
      }
    }

    final File mainContentRoot = AndroidJpsUtil.getMainContentRoot(extension);
    if (mainContentRoot == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.main.content.root.not.found", module.getName())));
      return false;
    }

    final Set<String> classFilesDirs = new HashSet<String>();
    final Set<String> libClassFilesDirs = new HashSet<String>();
    final Set<String> outputDirs = new HashSet<String>();

    AndroidJpsUtil.addSubdirectories(classesDir, classFilesDirs);
    outputDirs.add(classesDir.getPath());

    AndroidJpsUtil.processClasspath(context, module, new AndroidDependencyProcessor() {

      @Override
      public void processAndroidLibraryOutputDirectory(@NotNull File dir) {
        AndroidJpsUtil.addSubdirectories(dir, libClassFilesDirs);
        outputDirs.add(dir.getPath());
      }

      @Override
      public void processJavaModuleOutputDirectory(@NotNull File dir) {
        AndroidJpsUtil.addSubdirectories(dir, classFilesDirs);
        outputDirs.add(dir.getPath());
      }

      @Override
      public boolean isToProcess(@NotNull AndroidDependencyType type) {
        return type == AndroidDependencyType.ANDROID_LIBRARY_OUTPUT_DIRECTORY ||
               type == AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR;
      }
    });

    final String logsDirOsPath =
          FileUtil.toSystemDependentName(mainContentRoot.getPath() + '/' + AndroidCommonUtils.DIRECTORY_FOR_LOGS_NAME);
    final AndroidFileSetState newState = new AndroidFileSetState(externalJars, AndroidJpsUtil.CLASSES_AND_JARS_FILTER, true);

    if (context.isMake()) {
      final AndroidFileSetState oldState = proguardStateStorage.getState(module.getName());

      if (!areFilesChanged(proguardCfgFiles, module, context) && newState.equalsTo(oldState)) {
        final Set<String> dirtyOutputDirs = context.getUserData(DIRTY_OUTPUT_DIRS);
        assert dirtyOutputDirs != null;
        boolean outputDirsDirty = false;
        for (String outputDir : outputDirs) {
          if (dirtyOutputDirs.contains(outputDir)) {
            outputDirsDirty = true;
          }
        }
        if (!outputDirsDirty) {
          return true;
        }
      }
    }
    final String[] classFilesDirOsPaths = ArrayUtil.toStringArray(classFilesDirs);
    final String[] libClassFilesDirOsPaths = ArrayUtil.toStringArray(libClassFilesDirs);
    final String[] externalJarOsPaths = ArrayUtil.toStringArray(externalJars);
    final String inputJarOsPath = AndroidCommonUtils.buildTempInputJar(classFilesDirOsPaths, libClassFilesDirOsPaths);

    final File logsDir = new File(logsDirOsPath);
    if (!logsDir.exists()) {
      if (!logsDir.mkdirs()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", logsDirOsPath)));
        return false;
      }
    }

    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.proguard", module.getName())));

    final Map<AndroidCompilerMessageKind, List<String>> messages =
      AndroidCommonUtils.launchProguard(platform.getTarget(), platform.getSdkToolsRevision(), platform.getSdk().getHomePath(),
                                        proguardCfgPaths, includeSystemProguardCfg, inputJarOsPath, externalJarOsPaths,
                                        outputJarPath, logsDirOsPath);
    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, module.getName());
    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();

    proguardStateStorage.update(module.getName(), success ? newState : null);

    if (success) {
      final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();

      for (File file : proguardCfgFiles) {
        timestamps.saveStamp(file, new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION), file.lastModified());
      }
    }
    return success;
  }

  private static boolean areFilesChanged(@NotNull File[] files, JpsModule module, @NotNull CompileContext context) throws IOException {
    final Timestamps timestamps = context.getProjectDescriptor().timestamps.getStorage();

    for (File file : files) {
      if (timestamps.getStamp(file, new ModuleBuildTarget(module, JavaModuleBuildTargetType.PRODUCTION)) != file.lastModified()) {
        return true;
      }
    }
    return false;
  }
}
