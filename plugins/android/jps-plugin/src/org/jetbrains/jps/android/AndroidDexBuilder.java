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
import com.intellij.util.execution.ParametersListUtil;
import gnu.trove.THashSet;
import org.jetbrains.android.compiler.tools.AndroidDxRunner;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.builder.AndroidBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidDexCompilerConfiguration;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.StorageOwner;
import org.jetbrains.jps.incremental.storage.Timestamps;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDexBuilder extends TargetBuilder<BuildRootDescriptor, AndroidBuildTarget> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidDexBuilder");
  @NonNls private static final String BUILDER_NAME = "Android Dex";

  private static final Key<BuildListener> BUILD_LISTENER_KEY = Key.create("BUILD_LISTENER_KEY");
  public static final Key<Set<String>> DIRTY_OUTPUT_DIRS = Key.create("DIRTY_OUTPUT_DIRS");

  private static final StorageProvider<MyDirtyOutputDirsStorage> DIRTY_OUTPUT_DIRS_STORAGE_PROVIDER =
    new StorageProvider<MyDirtyOutputDirsStorage>() {
      @NotNull
      @Override
      public MyDirtyOutputDirsStorage createStorage(File targetDataDir) throws IOException {
        return new MyDirtyOutputDirsStorage(new File(targetDataDir, "dirty_output_dirs" + File.separator + "data"));
      }
    };

  public AndroidDexBuilder() {
    super(Collections.singletonList(AndroidBuildTarget.TargetType.DEX));
  }

  @Override
  public void build(@NotNull AndroidBuildTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException {
    try {
      final MyDirtyOutputDirsStorage storage = context.getProjectDescriptor().dataManager.getStorage(
        target, DIRTY_OUTPUT_DIRS_STORAGE_PROVIDER);
      final Set<String> dirtyOutputDirs = context.getUserData(DIRTY_OUTPUT_DIRS);
      assert dirtyOutputDirs != null;

      final Set<String> savedDirtyOutputs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
      savedDirtyOutputs.addAll(storage.loadDirtyOutputs());
      savedDirtyOutputs.addAll(dirtyOutputDirs);
      storage.saveDirtyOutputs(savedDirtyOutputs);

      if (AndroidJpsUtil.isLightBuild(context)) {
        return;
      }
      doBuild(target.getModule(), context, savedDirtyOutputs);
      storage.saveDirtyOutputs(Collections.<String>emptySet());
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static void doBuild(JpsModule module, CompileContext context, Set<String> dirtyOutputDirs) throws IOException, ProjectBuildException {
    final File root = context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot();

    AndroidFileSetStorage dexStateStorage = null;
    AndroidFileSetStorage proguardStateStorage = null;
    try {
      dexStateStorage = new AndroidFileSetStorage(root, "dex");
      proguardStateStorage = new AndroidFileSetStorage(root, "proguard");

      if (!doDexBuild(module, context, dexStateStorage, proguardStateStorage, dirtyOutputDirs)) {
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

  private static boolean doDexBuild(@NotNull JpsModule module,
                                    @NotNull CompileContext context,
                                    @NotNull AndroidFileSetStorage dexStateStorage,
                                    @NotNull AndroidFileSetStorage proguardStateStorage,
                                    @NotNull Set<String> dirtyOutputDirs) throws IOException {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    assert extension != null;
    if (extension.isLibrary()) {
      return true;
    }

    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
    if (platform == null) {
      return false;
    }

    File dexOutputDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
    dexOutputDir = AndroidJpsUtil.createDirIfNotExist(dexOutputDir, context, BUILDER_NAME);
    if (dexOutputDir == null) {
      return false;
    }

    final File classesDir = ProjectPaths.getModuleOutputDir(module, false);
    if (classesDir == null || !classesDir.isDirectory()) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO, AndroidJpsBundle
        .message("android.jps.warnings.dex.no.compiled.files", module.getName())));
      return true;
    }
    final Set<String> externalLibraries = AndroidJpsUtil.getExternalLibraries(context, module, platform);
    final ProGuardOptions proGuardOptions = AndroidJpsUtil.getProGuardConfigIfShouldRun(context, extension);

    if (proGuardOptions != null) {
      if (proGuardOptions.getCfgFile() == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle
                                                     .message("android.jps.errors.cannot.find.proguard.cfg", module.getName())));
        return false;
      }
    }
    final Set<String> fileSet;
    final Set<String> jars;
    final Set<String> outputDirs;

    try {
      if (proGuardOptions != null) {
        final File proguardCfgOutputFile = new File(dexOutputDir, AndroidCommonUtils.PROGUARD_CFG_OUTPUT_FILE_NAME);
        final String[] proguardCfgFilePaths = new String[]{proGuardOptions.getCfgFile().getAbsolutePath(), proguardCfgOutputFile.getPath()};
        final String outputJarPath =
          FileUtil.toSystemDependentName(dexOutputDir.getPath() + '/' + AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME);

        if (!runProguardIfNecessary(extension, classesDir, platform, externalLibraries, context, outputJarPath,
                                    proguardCfgFilePaths, proGuardOptions.isIncludeSystemCfgFile(), proguardStateStorage,
                                    dirtyOutputDirs)) {
          return false;
        }
        fileSet = jars = Collections.singleton(outputJarPath);
        outputDirs = Collections.emptySet();
      }
      else {
        fileSet = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
        jars = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
        outputDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

        AndroidJpsUtil.addSubdirectories(classesDir, fileSet);
        outputDirs.add(FileUtil.toSystemIndependentName(classesDir.getPath()));

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
            outputDirs.add(FileUtil.toSystemIndependentName(dir.getPath()));
          }

          @Override
          public boolean isToProcess(@NotNull AndroidDependencyType type) {
            return type == AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR ||
                   type == AndroidDependencyType.ANDROID_LIBRARY_PACKAGE ||
                   type == AndroidDependencyType.EXTERNAL_LIBRARY;
          }
        });

        if (extension.isPackTestCode()) {
          final File testsClassDir = ProjectPaths.getModuleOutputDir(module, true);

          if (testsClassDir != null && testsClassDir.isDirectory()) {
            AndroidJpsUtil.addSubdirectories(testsClassDir, fileSet);
            outputDirs.add(FileUtil.toSystemIndependentName(testsClassDir.getPath()));
          }
        }
      }
      final AndroidFileSetState newState = new AndroidFileSetState(jars, AndroidJpsUtil.CLASSES_AND_JARS_FILTER, true);

      if (context.isMake()) {
        final AndroidFileSetState oldState = dexStateStorage.getState(module.getName());
        if (oldState != null && oldState.equalsTo(newState)) {
          boolean outputDirsDirty = false;

          for (String outputDir : outputDirs) {
            if (dirtyOutputDirs.contains(outputDir)) {
              outputDirsDirty = true;
              break;
            }
          }
          if (!outputDirsDirty) {
            return true;
          }
        }
      }

      if (fileSet.size() == 0) {
        return true;
      }

      final String[] files = new String[fileSet.size()];
      int i = 0;
      for (String filePath : fileSet) {
        files[i++] = FileUtil.toSystemDependentName(filePath);
      }

      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.dex", module.getName())));

      if (!runDex(platform, dexOutputDir.getPath(), files, context, module)) {
        dexStateStorage.update(module.getName(), null);
        return false;
      }
      else {
        dexStateStorage.update(module.getName(), newState);
      }
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
    return true;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  @Override
  public void buildStarted(CompileContext context) {
    final Set<String> dirtyOutputDirs = Collections.synchronizedSet(new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY));

    final BuildListener listener = new BuildListener() {
      @Override
      public void filesGenerated(Collection<Pair<String, String>> paths) {
        // todo: remember dirty files for next sessions, because current one may be stopped before dexing
        for (Pair<String, String> path : paths) {
          dirtyOutputDirs.add(path.first);
        }
      }

      @Override
      public void filesDeleted(Collection<String> paths) {
      }
    };
    DIRTY_OUTPUT_DIRS.set(context, dirtyOutputDirs);
    BUILD_LISTENER_KEY.set(context, listener);
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

    final JpsAndroidDexCompilerConfiguration configuration =
      JpsAndroidExtensionService.getInstance().getDexCompilerConfiguration(module.getProject());
    final List<String> vmOptions;

    if (configuration != null) {
      vmOptions = new ArrayList<String>();
      vmOptions.addAll(ParametersListUtil.parse(configuration.getVmOptions()));

      if (!AndroidCommonUtils.hasXmxParam(vmOptions)) {
        vmOptions.add("-Xmx" + configuration.getMaxHeapSize() + "M");
      }
      programParamList.addAll(Arrays.asList("--optimize", Boolean.toString(configuration.isOptimize())));
    }
    else {
      vmOptions = Collections.singletonList("-Xmx1024M");
    }
    programParamList.addAll(Arrays.asList(compileTargets));
    programParamList.add("--exclude");

    final List<String> classPath = new ArrayList<String>();
    classPath.add(ClasspathBootstrap.getResourcePath(AndroidDxRunner.class));
    classPath.add(ClasspathBootstrap.getResourcePath(FileUtilRt.class));

    final File outFile = new File(outFilePath);
    if (outFile.exists() && !outFile.delete()) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING,
                                                 AndroidJpsBundle.message("android.jps.cannot.delete.file", outFilePath)));
    }

    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = platform.getSdk();
    final String jdkName = sdk.getSdkProperties().getData().getJdkName();
    final JpsLibrary javaSdk = context.getProjectDescriptor().getModel().getGlobal().getLibraryCollection().findLibrary(jdkName);
    if (javaSdk == null || !javaSdk.getType().equals(JpsJavaSdkType.INSTANCE)) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 AndroidJpsBundle.message("android.jps.errors.java.sdk.not.specified", jdkName)));
      return false;
    }

    final List<String> commandLine = ExternalProcessUtil
      .buildJavaCommandLine(JpsJavaSdkType.getJavaExecutable((JpsSdk<?>)javaSdk.getProperties()), AndroidDxRunner.class.getName(),
                            Collections.<String>emptyList(), classPath, vmOptions, programParamList);

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
                                                @NotNull AndroidFileSetStorage proguardStateStorage,
                                                @NotNull Set<String> dirtyOutputDirs) throws IOException {
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

    final Set<String> classFilesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> libClassFilesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> outputDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

    AndroidJpsUtil.addSubdirectories(classesDir, classFilesDirs);
    outputDirs.add(FileUtil.toSystemIndependentName(classesDir.getPath()));

    AndroidJpsUtil.processClasspath(context, module, new AndroidDependencyProcessor() {

      @Override
      public void processAndroidLibraryOutputDirectory(@NotNull File dir) {
        AndroidJpsUtil.addSubdirectories(dir, libClassFilesDirs);
        outputDirs.add(FileUtil.toSystemIndependentName(dir.getPath()));
      }

      @Override
      public void processJavaModuleOutputDirectory(@NotNull File dir) {
        AndroidJpsUtil.addSubdirectories(dir, classFilesDirs);
        outputDirs.add(FileUtil.toSystemIndependentName(dir.getPath()));
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

  private static class MyDirtyOutputDirsStorage implements StorageOwner {
    private final File myFile;

    MyDirtyOutputDirsStorage(@NotNull File file) {
      myFile = file;
    }

    @Override
    public void flush(boolean memoryCachesOnly) {
    }

    @Override
    public void clean() throws IOException {
      FileUtil.delete(myFile);
    }

    @Override
    public void close() throws IOException {
    }

    void saveDirtyOutputs(@NotNull Set<String> dirtyOutputs) {
      FileUtil.createParentDirs(myFile);
      try {
        final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(myFile)));

        try {
          stream.writeInt(dirtyOutputs.size());

          for (String path : dirtyOutputs) {
            stream.writeUTF(path);
          }
        }
        finally {
          stream.close();
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }

    @NotNull
    Set<String> loadDirtyOutputs() {
      try {
        final DataInputStream stream = new DataInputStream(new FileInputStream(myFile));
        try {
          final int size = stream.readInt();
          final Set<String> result = new HashSet<String>(size);

          for (int i = 0; i < size; i++) {
            result.add(stream.readUTF());
          }
          return result;
        }
        finally {
          stream.close();
        }
      }
      catch (FileNotFoundException ignored) {
      }
      catch (IOException e) {
        LOG.info(e);
      }
      return Collections.emptySet();
    }
  }
}
