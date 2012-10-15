package org.jetbrains.jps.android;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.build.BuildConfigGenerator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.compiler.tools.AndroidIdl;
import org.jetbrains.android.compiler.tools.AndroidRenderscript;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.FileProcessor;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.builders.storage.SourceToOutputMapping;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSourceGeneratingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.android.AndroidSourceGeneratingBuilder");

  @NonNls private static final String ANDROID_VALIDATOR = "android-validator";
  @NonNls private static final String ANDROID_IDL_COMPILER = "android-idl-compiler";
  @NonNls private static final String ANDROID_RENDERSCRIPT_COMPILER = "android-renderscript-compiler";
  @NonNls private static final String ANDROID_BUILD_CONFIG_GENERATOR = "android-buildconfig-generator";
  @NonNls private static final String ANDROID_APT_COMPILER = "android-apt-compiler";
  @NonNls private static final String BUILDER_NAME = "android-source-generator";

  @NonNls private static final String AIDL_EXTENSION = "aidl";
  @NonNls private static final String RENDERSCRIPT_EXTENSION = "rs";
  @NonNls private static final String MANIFEST_TAG = "manifest";
  @NonNls private static final String PERMISSION_TAG = "permission";
  @NonNls private static final String PERMISSION_GROUP_TAG = "permission-group";
  @NonNls private static final String NAME_ATTRIBUTE = "name";

  private static final int MIN_PLATFORM_TOOLS_REVISION = 11;
  private static final int MIN_SDK_TOOLS_REVISION = 19;

  public AndroidSourceGeneratingBuilder() {
    super(BuilderCategory.SOURCE_GENERATOR);
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public ModuleLevelBuilder.ExitCode build(CompileContext context,
                                           ModuleChunk chunk,
                                           DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws ProjectBuildException {
    if (chunk.containsTests() || !AndroidJpsUtil.containsAndroidFacet(chunk)) {
      return ExitCode.NOTHING_DONE;
    }

    try {
      return doBuild(context, chunk, dirtyFilesHolder);
    }
    catch (Exception e) {
      return AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static ModuleLevelBuilder.ExitCode doBuild(CompileContext context,
                                                     ModuleChunk chunk,
                                                     DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws IOException {
    final Map<JpsModule, MyModuleData> moduleDataMap = computeModuleDatas(chunk.getModules(), context);
    if (moduleDataMap == null || moduleDataMap.size() == 0) {
      return ExitCode.ABORT;
    }

    if (!checkVersions(moduleDataMap, context)) {
      return ExitCode.ABORT;
    }
    checkAndroidDependencies(moduleDataMap, context);

    if (context.isProjectRebuild()) {
      if (!clearAndroidStorages(context, chunk.getModules())) {
        return ExitCode.ABORT;
      }
    }

    final Map<File, ModuleBuildTarget> idlFilesToCompile = new HashMap<File, ModuleBuildTarget>();
    final Map<File, ModuleBuildTarget> rsFilesToCompile = new HashMap<File, ModuleBuildTarget>();

    dirtyFilesHolder.processDirtyFiles(new FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>() {
      @Override
      public boolean apply(ModuleBuildTarget target, File file, JavaSourceRootDescriptor sourceRoot) throws IOException {
        final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(target.getModule());

        if (extension == null) {
          return true;
        }
        final String ext = FileUtil.getExtension(file.getName());

        if (AIDL_EXTENSION.equals(ext)) {
          idlFilesToCompile.put(file, target);
        }
        else if (RENDERSCRIPT_EXTENSION.equals(ext)) {
          rsFilesToCompile.put(file, target);
        }

        return true;
      }
    });
    boolean success = true;

    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
    if (context.isProjectRebuild()) {
      for (JpsModule module : moduleDataMap.keySet()) {
        final File generatedSourcesStorage = AndroidJpsUtil.getGeneratedSourcesStorage(module, dataManager);
        if (generatedSourcesStorage.exists() &&
            !deleteAndMarkRecursively(generatedSourcesStorage, context, BUILDER_NAME)) {
          success = false;
        }

        final File generatedResourcesStorage = AndroidJpsUtil.getGeneratedResourcesStorage(module, dataManager);
        if (generatedResourcesStorage.exists() &&
            !deleteAndMarkRecursively(generatedResourcesStorage, context, BUILDER_NAME)) {
          success = false;
        }
      }
    }

    if (!success) {
      return ExitCode.ABORT;
    }

    if (!runAidlCompiler(context, idlFilesToCompile, moduleDataMap)) {
      success = false;
    }

    if (!runRenderscriptCompiler(context, rsFilesToCompile, moduleDataMap)) {
      success = false;
    }

    final File dataStorageRoot = dataManager.getDataPaths().getDataStorageRoot();

    final AndroidAptStateStorage aptStorage = new AndroidAptStateStorage(dataStorageRoot);
    try {
      if (!runAaptCompiler(context, moduleDataMap, aptStorage)) {
        success = false;
      }
    }
    finally {
      aptStorage.close();
    }

    final AndroidBuildConfigStateStorage buildConfigStorage = new AndroidBuildConfigStateStorage(dataStorageRoot);
    try {
      if (!runBuildConfigGeneration(context, moduleDataMap, buildConfigStorage)) {
        success = false;
      }
    }
    finally {
      buildConfigStorage.close();
    }

    return success ? ExitCode.OK : ExitCode.ABORT;
  }

  private static boolean clearAndroidStorages(@NotNull CompileContext context, @NotNull Collection<JpsModule> modules) {
    for (JpsModule module : modules) {
      final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
      if (dir.exists() && !FileUtil.delete(dir)) {
        context.processMessage(
          new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.cannot.delete", dir.getPath())));
        return false;
      }
    }
    return true;
  }

  private static boolean checkVersions(@NotNull Map<JpsModule, MyModuleData> dataMap, @NotNull CompileContext context) {
    for (Map.Entry<JpsModule, MyModuleData> entry : dataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final AndroidPlatform platform = entry.getValue().getPlatform();

      boolean success = true;

      final int platformToolsRevision = platform.getPlatformToolsRevision();
      if (platformToolsRevision >= 0 && platformToolsRevision < MIN_PLATFORM_TOOLS_REVISION) {
        final String message = '[' +
                               module.getName() +
                               "] Incompatible version of Android SDK Platform-tools package. Min version is " +
                               MIN_PLATFORM_TOOLS_REVISION +
                               ". Please, update it though SDK manager";
        context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, message));
        success = false;
      }

      final int sdkToolsRevision = platform.getSdkToolsRevision();
      if (sdkToolsRevision >= 0 && sdkToolsRevision < MIN_SDK_TOOLS_REVISION) {
        final String message = '[' +
                               module.getName() +
                               "] Incompatible version of Android SDK Tools package. Min version is " +
                               MIN_SDK_TOOLS_REVISION +
                               ". Please, update it though SDK manager";
        context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.ERROR, message));
        success = false;
      }

      // show error message only for first module, because all modules usualy have the same sdk specified
      if (!success) {
        return false;
      }
    }
    return true;
  }

  private static void checkAndroidDependencies(@NotNull Map<JpsModule, MyModuleData> moduleDataMap, @NotNull CompileContext context) {
    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final JpsAndroidModuleExtension extension = moduleData.getAndroidExtension();

      final Pair<String, File> manifestMergerProp =
        AndroidJpsUtil.getProjectPropertyValue(extension, AndroidCommonUtils.ANDROID_MANIFEST_MERGER_PROPERTY);
      if (manifestMergerProp != null && Boolean.parseBoolean(manifestMergerProp.getFirst())) {
        final String message = "[" + module.getName() + "] Manifest merging is not supported. Please, reconfigure your manifest files";
        final String propFilePath = manifestMergerProp.getSecond().getPath();
        context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.WARNING, message, propFilePath));
      }

      if (extension.isLibrary()) {
        continue;
      }

      for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false)) {
        if (item instanceof JpsModuleDependency) {
          final JpsModule depModule = ((JpsModuleDependency)item).getModule();
          if (depModule != null) {
            final JpsAndroidModuleExtension depExtension = AndroidJpsUtil.getExtension(depModule);

            if (depExtension != null && !depExtension.isLibrary()) {
              String message = "Suspicious module dependency " +
                               module.getName() +
                               " -> " +
                               depModule.getName() +
                               ": Android application module depends on other application module. Possibly, you should ";
              if (AndroidJpsUtil.isMavenizedModule(depModule)) {
                message += "change packaging type of module " + depModule.getName() + " to 'apklib' in pom.xml file or ";
              }
              message += "change dependency scope to 'Provided'.";
              context.processMessage(new CompilerMessage(ANDROID_VALIDATOR, BuildMessage.Kind.WARNING, message));
            }
          }
        }
      }
    }
  }

  private static boolean runBuildConfigGeneration(@NotNull CompileContext context,
                                                  @NotNull Map<JpsModule, MyModuleData> moduleDataMap,
                                                  @NotNull AndroidBuildConfigStateStorage storage) {
    boolean success = true;

    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module, context.getProjectDescriptor().dataManager);
      final File outputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.BUILD_CONFIG_GENERATED_SOURCE_ROOT_NAME);

      try {
        if (extension == null || isLibraryWithBadCircularDependency(extension)) {
          if (!clearDirectoryIfNotEmpty(outputDirectory, context, ANDROID_BUILD_CONFIG_GENERATOR)) {
            success = false;
          }
          continue;
        }
        final String packageName = moduleData.getPackage();
        final boolean debug = !AndroidJpsUtil.isReleaseBuild(context);
        final Set<String> libPackages = new HashSet<String>(getDepLibPackages(module).values());
        libPackages.remove(packageName);

        final AndroidBuildConfigState newState = new AndroidBuildConfigState(packageName, libPackages, debug);

        if (context.isMake()) {
          final AndroidBuildConfigState oldState = storage.getState(module.getName());
          if (newState.equalsTo(oldState)) {
            continue;
          }
        }

        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.build.config", module.getName())));

        // clear directory, because it may contain obsolete files (ex. if package name was changed)
        if (!clearDirectory(outputDirectory, context, ANDROID_BUILD_CONFIG_GENERATOR)) {
          success = false;
          continue;
        }

        if (doBuildConfigGeneration(packageName, libPackages, debug, outputDirectory, context)) {
          storage.update(module.getName(), newState);
          markDirtyRecursively(outputDirectory, context, ANDROID_BUILD_CONFIG_GENERATOR);
        }
        else {
          storage.update(module.getName(), null);
          success = false;
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_BUILD_CONFIG_GENERATOR);
        success = false;
      }
    }
    return success;
  }

  private static boolean doBuildConfigGeneration(@NotNull String packageName,
                                                 @NotNull Collection<String> libPackages,
                                                 boolean debug,
                                                 @NotNull File outputDirectory,
                                                 @NotNull CompileContext context) {
    if (!doBuildConfigGeneration(packageName, debug, outputDirectory.getPath(), context)) {
      return false;
    }

    for (String libPackage : libPackages) {
      if (!doBuildConfigGeneration(libPackage, debug, outputDirectory.getPath(), context)) {
        return false;
      }
    }
    return true;
  }

  private static boolean doBuildConfigGeneration(@NotNull String packageName,
                                                 boolean debug,
                                                 @NotNull String outputDirOsPath,
                                                 @NotNull CompileContext context) {
    final BuildConfigGenerator generator = new BuildConfigGenerator(outputDirOsPath, packageName, debug);
    try {
      generator.generate();
      return true;
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_BUILD_CONFIG_GENERATOR);
      return false;
    }
  }

  private static boolean runAidlCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<File, ModuleBuildTarget> files,
                                         @NotNull Map<JpsModule, MyModuleData> moduleDataMap) {
    if (files.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aidl")));
    }

    boolean success = true;

    for (Map.Entry<File, ModuleBuildTarget> entry : files.entrySet()) {
      final File file = entry.getKey();
      final ModuleBuildTarget buildTarget = entry.getValue();
      final String filePath = file.getPath();

      final MyModuleData moduleData = moduleDataMap.get(buildTarget.getModule());

      if (!LOG.assertTrue(moduleData != null)) {
        context.processMessage(
          new CompilerMessage(ANDROID_IDL_COMPILER, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.internal.error")));
        success = false;
        continue;
      }
      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(buildTarget.getModule(), context.getProjectDescriptor().dataManager);
      final File aidlOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.AIDL_GENERATED_SOURCE_ROOT_NAME);

      if (!aidlOutputDirectory.exists() && !aidlOutputDirectory.mkdirs()) {
        context.processMessage(
          new CompilerMessage(ANDROID_IDL_COMPILER, BuildMessage.Kind.ERROR,
                              AndroidJpsBundle.message("android.jps.cannot.create.directory", aidlOutputDirectory.getPath())));
        success = false;
        continue;
      }

      final IAndroidTarget target = moduleData.getPlatform().getTarget();

      try {
        final File[] sourceRoots = AndroidJpsUtil.getSourceRootsForModuleAndDependencies(buildTarget.getModule());
        final String[] sourceRootPaths = AndroidJpsUtil.toPaths(sourceRoots);
        final String packageName = computePackageForFile(context, file);

        if (packageName == null) {
          context.processMessage(new CompilerMessage(ANDROID_IDL_COMPILER, BuildMessage.Kind.ERROR,
                                                     AndroidJpsBundle.message("android.jps.errors.cannot.compute.package", filePath)));
          success = false;
          continue;
        }

        final File outputFile = new File(aidlOutputDirectory, packageName.replace('.', File.separatorChar) +
                                                              File.separator + FileUtil.getNameWithoutExtension(file) + ".java");
        final String outputFilePath = outputFile.getPath();
        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidIdl.execute(target, filePath, outputFilePath, sourceRootPaths);

        addMessages(context, messages, filePath, ANDROID_IDL_COMPILER);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else {
          final SourceToOutputMapping sourceToOutputMap = context.getProjectDescriptor().dataManager.getSourceToOutputMap(buildTarget);
          sourceToOutputMap.setOutput(filePath, outputFilePath);
          FSOperations.markDirty(context, outputFile);
        }
      }
      catch (final IOException e) {
        AndroidJpsUtil.reportExceptionError(context, filePath, e, ANDROID_IDL_COMPILER);
        success = false;
      }
    }
    return success;
  }

  private static boolean runRenderscriptCompiler(@NotNull final CompileContext context,
                                                 @NotNull Map<File, ModuleBuildTarget> files,
                                                 @NotNull Map<JpsModule, MyModuleData> moduleDataMap) {
    if (files.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.renderscript")));
    }

    boolean success = true;

    for (Map.Entry<File, ModuleBuildTarget> entry : files.entrySet()) {
      final File file = entry.getKey();
      final ModuleBuildTarget buildTarget = entry.getValue();

      final MyModuleData moduleData = moduleDataMap.get(buildTarget.getModule());
      if (!LOG.assertTrue(moduleData != null)) {
        context.processMessage(new CompilerMessage(ANDROID_RENDERSCRIPT_COMPILER, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.internal.error")));
        success = false;
        continue;
      }

      final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;
      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(buildTarget.getModule(), dataManager);
      final File rsOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME);
      if (!rsOutputDirectory.exists() && !rsOutputDirectory.mkdirs()) {
        context.processMessage(new CompilerMessage(ANDROID_RENDERSCRIPT_COMPILER, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.cannot.create.directory", rsOutputDirectory.getPath())));
        success = false;
        continue;
      }

      final File generatedResourcesDir = AndroidJpsUtil.getGeneratedResourcesStorage(buildTarget.getModule(), dataManager);
      final File rawDir = new File(generatedResourcesDir, "raw");

      if (!rawDir.exists() && !rawDir.mkdirs()) {
        context.processMessage(new CompilerMessage(ANDROID_RENDERSCRIPT_COMPILER, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", rawDir.getPath())));
        success = false;
        continue;
      }

      final AndroidPlatform platform = moduleData.getPlatform();
      final IAndroidTarget target = platform.getTarget();
      final String sdkLocation = platform.getSdk().getHomePath();
      final String filePath = file.getPath();

      File tmpOutputDirectory = null;

      try {
        tmpOutputDirectory = FileUtil.createTempDirectory("generated-rs-temp", null);
        final String depFolderPath = getDependencyFolder(context, file, tmpOutputDirectory);

        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidRenderscript.execute(sdkLocation, target, filePath, tmpOutputDirectory.getPath(), depFolderPath, rawDir.getPath());

        addMessages(context, messages, filePath, ANDROID_RENDERSCRIPT_COMPILER);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else {
          final List<File> newFiles = new ArrayList<File>();
          AndroidCommonUtils.moveAllFiles(tmpOutputDirectory, rsOutputDirectory, newFiles);

          final File bcFile = new File(rawDir, FileUtil.getNameWithoutExtension(file) + ".bc");
          if (bcFile.exists()) {
            newFiles.add(bcFile);
          }
          final List<String> newFilePaths = Arrays.asList(AndroidJpsUtil.toPaths(newFiles.toArray(new File[newFiles.size()])));

          final SourceToOutputMapping sourceToOutputMap = dataManager.getSourceToOutputMap(buildTarget);
          sourceToOutputMap.setOutputs(filePath, newFilePaths);

          for (File newFile : newFiles) {
            FSOperations.markDirty(context, newFile);
          }
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, filePath, e, ANDROID_RENDERSCRIPT_COMPILER);
        success = false;
      }
      finally {
        if (tmpOutputDirectory != null) {
          FileUtil.delete(tmpOutputDirectory);
        }
      }
    }
    return success;
  }

  private static boolean runAaptCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<JpsModule, MyModuleData> moduleDataMap,
                                         @NotNull AndroidAptStateStorage storage) {
    boolean success = true;

    for (Map.Entry<JpsModule, MyModuleData> entry : moduleDataMap.entrySet()) {
      final JpsModule module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final JpsAndroidModuleExtension extension = moduleData.getAndroidExtension();

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module, context.getProjectDescriptor().dataManager);
      final File aptOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.AAPT_GENERATED_SOURCE_ROOT_NAME);
      final IAndroidTarget target = moduleData.getPlatform().getTarget();

      try {
        if (!needToRunAaptCompilation(extension)) {
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
            success = false;
          }
          continue;
        }

        final String[] resPaths = AndroidJpsUtil.collectResourceDirsForCompilation(extension, false, context);
        if (resPaths.length == 0) {
          // there is no resources in the module
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
            success = false;
          }
          continue;
        }
        final String packageName = moduleData.getPackage();
        final File manifestFile = moduleData.getManifestFileForCompiler();

        if (isLibraryWithBadCircularDependency(extension)) {
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
            success = false;
          }
          continue;
        }
        final Map<JpsModule, String> packageMap = getDepLibPackages(module);
        packageMap.put(module, packageName);

        final JpsModule circularDepLibWithSamePackage = findCircularDependencyOnLibraryWithSamePackage(extension, packageMap);
        if (circularDepLibWithSamePackage != null && !extension.isLibrary()) {
          final String message = "Generated fields in " +
                                 packageName +
                                 ".R class in module '" +
                                 module.getName() +
                                 "' won't be final, because of circular dependency on module '" +
                                 circularDepLibWithSamePackage.getName() +
                                 "'";
          context.processMessage(new CompilerMessage(ANDROID_APT_COMPILER, BuildMessage.Kind.WARNING, message));
        }
        final boolean generateNonFinalFields = extension.isLibrary() || circularDepLibWithSamePackage != null;

        final Map<String, ResourceFileData> resources = collectResources(resPaths);
        final List<ResourceEntry> manifestElements = collectManifestElements(manifestFile);

        final Set<String> depLibPackagesSet = new HashSet<String>(packageMap.values());
        depLibPackagesSet.remove(packageName);
        final String proguardOutputCfgFilePath;

        if (toLaunchProGuard(context, extension)) {
          final File outputDirForArtifacts = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);

          if (AndroidJpsUtil.createDirIfNotExist(outputDirForArtifacts, context, BUILDER_NAME) == null) {
            success = false;
            continue;
          }
          proguardOutputCfgFilePath = new File(outputDirForArtifacts, AndroidCommonUtils.PROGUARD_CFG_OUTPUT_FILE_NAME).getPath();
        }
        else {
          proguardOutputCfgFilePath = null;
        }
        final AndroidAptValidityState newState =
          new AndroidAptValidityState(resources, manifestElements, depLibPackagesSet, packageName, proguardOutputCfgFilePath);

        if (context.isMake()) {
          final AndroidAptValidityState oldState = storage.getState(module.getName());
          if (newState.equalsTo(oldState)) {
            continue;
          }
        }
        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aapt", module.getName())));

        File tmpOutputDir = null;
        try {
          tmpOutputDir = FileUtil.createTempDirectory("android_apt_output", "tmp");
          final Map<AndroidCompilerMessageKind, List<String>> messages =
            AndroidApt.compile(target, -1, manifestFile.getPath(), packageName, tmpOutputDir.getPath(), resPaths,
                               ArrayUtil.toStringArray(depLibPackagesSet), generateNonFinalFields, proguardOutputCfgFilePath);

          AndroidJpsUtil.addMessages(context, messages, ANDROID_APT_COMPILER, module.getName());

          if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
            success = false;
            storage.update(module.getName(), null);
          }
          else {
            if (!AndroidCommonUtils.directoriesContainSameContent(tmpOutputDir, aptOutputDirectory, JavaFilesFilter.INSTANCE)) {
              if (!deleteAndMarkRecursively(aptOutputDirectory, context, ANDROID_APT_COMPILER)) {
                success = false;
                continue;
              }
              final File parent = aptOutputDirectory.getParentFile();
              if (parent != null && !parent.exists() && !parent.mkdirs()) {
                context.processMessage(new CompilerMessage(ANDROID_APT_COMPILER, BuildMessage.Kind.ERROR, AndroidJpsBundle.message(
                  "android.jps.cannot.create.directory", parent.getPath())));
                success = false;
                continue;
              }
              // we use copyDir instead of moveDirWithContent here, because tmp directory may be located on other disk and
              // moveDirWithContent doesn't work for such case
              FileUtil.copyDir(tmpOutputDir, aptOutputDirectory);
              markDirtyRecursively(aptOutputDirectory, context, ANDROID_APT_COMPILER);
            }
            storage.update(module.getName(), newState);
          }
        }
        finally {
          if (tmpOutputDir != null) {
            FileUtil.delete(tmpOutputDir);
          }
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, ANDROID_APT_COMPILER);
        success = false;
      }
    }
    return success;
  }

  private static boolean clearDirectory(File dir, CompileContext context, String compilerName) throws IOException {
    if (!deleteAndMarkRecursively(dir, context, compilerName)) {
      return false;
    }

    if (!dir.mkdirs()) {
      context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR,
                                                 AndroidJpsBundle.message("android.jps.cannot.create.directory", dir.getPath())));
      return false;
    }
    return true;
  }

  private static boolean clearDirectoryIfNotEmpty(@NotNull File dir, @NotNull CompileContext context, String compilerName)
    throws IOException {
    if (dir.isDirectory()) {
      final String[] list = dir.list();
      if (list != null && list.length > 0) {
        return clearDirectory(dir, context, compilerName);
      }
    }
    return true;
  }

  private static boolean needToRunAaptCompilation(JpsAndroidModuleExtension extension) {
    return !extension.isRunProcessResourcesMavenTask() || !AndroidJpsUtil.isMavenizedModule(extension.getModule());
  }

  private static boolean deleteAndMarkRecursively(@NotNull File dir, @NotNull CompileContext context, @NotNull String compilerName)
    throws IOException {
    if (dir.exists()) {
      final List<File> filesToDelete = collectJavaFilesRecursively(dir);
      if (!FileUtil.delete(dir)) {
        context.processMessage(
          new CompilerMessage(compilerName, BuildMessage.Kind.ERROR, AndroidJpsBundle.message("android.jps.cannot.delete", dir.getPath())));
        return false;
      }

      for (File file : filesToDelete) {
        FSOperations.markDeleted(context, file);
      }
    }
    return true;
  }

  private static boolean markDirtyRecursively(@NotNull File dir,
                                              @NotNull final CompileContext context,
                                              @NotNull final String compilerName) {
    final Ref<Boolean> success = Ref.create(true);

    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile() && "java".equals(FileUtil.getExtension(file.getName()))) {
          try {
            FSOperations.markDirty(context, file);
          }
          catch (IOException e) {
            AndroidJpsUtil.reportExceptionError(context, null, e, compilerName);
            success.set(false);
            return false;
          }
        }
        return true;
      }
    });
    return success.get();
  }

  @NotNull
  private static List<File> collectJavaFilesRecursively(@NotNull File dir) {
    final List<File> result = new ArrayList<File>();

    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile() && "java".equals(FileUtil.getExtension(file.getName()))) {
          result.add(file);
        }
        return true;
      }
    });
    return result;
  }

  @NotNull
  private static Map<JpsModule, String> getDepLibPackages(@NotNull JpsModule module) throws IOException {
    final Map<JpsModule, String> result = new HashMap<JpsModule, String>();

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(module, true)) {
      final File depManifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(depExtension);

      if (depManifestFile != null) {
        final String packageName = parsePackageNameFromManifestFile(depManifestFile);

        if (packageName != null) {
          result.put(depExtension.getModule(), packageName);
        }
      }
    }
    return result;
  }

  @Nullable
  private static String parsePackageNameFromManifestFile(@NotNull File manifestFile) throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(manifestFile));
    try {
      final Ref<String> packageName = new Ref<String>(null);
      FormsParsing.parse(inputStream, new FormsParsing.IXMLBuilderAdapter() {
        boolean processingManifestTagAttrs = false;

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          if (MANIFEST_TAG.equals(name)) {
            processingManifestTagAttrs = true;
          }
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
          throws Exception {
          if (value != null && AndroidCommonUtils.PACKAGE_MANIFEST_ATTRIBUTE.equals(key)) {
            packageName.set(value.trim());
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          stop();
        }
      });

      return packageName.get();
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  private static Map<String, ResourceFileData> collectResources(@NotNull String[] resPaths) throws IOException {
    final Map<String, ResourceFileData> result = new HashMap<String, ResourceFileData>();

    for (String resDirPath : resPaths) {
      final File[] resSubdirs = new File(resDirPath).listFiles();

      if (resSubdirs != null) {
        for (File resSubdir : resSubdirs) {
          final String resType = AndroidCommonUtils.getResourceTypeByDirName(resSubdir.getName());

          if (resType != null) {
            final File[] resFiles = resSubdir.listFiles();

            if (resFiles != null) {
              for (File resFile : resFiles) {
                if (ResourceFolderType.VALUES.getName().equals(resType) && "xml".equals(FileUtil.getExtension(resFile.getName()))) {
                  final ArrayList<ResourceEntry> entries = new ArrayList<ResourceEntry>();
                  collectValueResources(resFile, entries);
                  result.put(FileUtil.toSystemIndependentName(resFile.getPath()), new ResourceFileData(entries, 0));
                }
                else {
                  final ResourceType resTypeObj = ResourceType.getEnum(resType);
                  final boolean idProvidingType =
                    resTypeObj != null && ArrayUtil.find(AndroidCommonUtils.ID_PROVIDING_RESOURCE_TYPES, resTypeObj) >= 0;
                  final ResourceFileData data =
                    new ResourceFileData(Collections.<ResourceEntry>emptyList(), idProvidingType ? resFile.lastModified() : 0);
                  result.put(FileUtil.toSystemIndependentName(resFile.getPath()), data);
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  private static void collectValueResources(@NotNull File valueResXmlFile, @NotNull final List<ResourceEntry> result)
    throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(valueResXmlFile));
    try {

      FormsParsing.parse(inputStream, new ValueResourcesFileParser() {
        @Override
        protected void stop() {
          throw new FormsParsing.ParserStoppedException();
        }

        @Override
        protected void process(@NotNull ResourceEntry resourceEntry) {
          result.add(resourceEntry);
        }
      });
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  private static List<ResourceEntry> collectManifestElements(@NotNull File manifestFile) throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(manifestFile));
    try {
      final List<ResourceEntry> result = new ArrayList<ResourceEntry>();

      FormsParsing.parse(inputStream, new FormsParsing.IXMLBuilderAdapter() {
        String myLastName;

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          myLastName = null;
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
          throws Exception {
          if (value != null && NAME_ATTRIBUTE.equals(key)) {
            myLastName = value;
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          if (myLastName != null && PERMISSION_TAG.equals(name) || PERMISSION_GROUP_TAG.equals(name)) {
            assert myLastName != null;
            result.add(new ResourceEntry(name, myLastName, ""));
          }
        }
      });

      return result;
    }
    finally {
      inputStream.close();
    }
  }

  @Nullable
  private static String getDependencyFolder(@NotNull CompileContext context, @NotNull File sourceFile, @NotNull File genFolder) {
    final JavaSourceRootDescriptor descriptor = context.getProjectDescriptor().getBuildRootIndex().getModuleAndRoot(context, sourceFile);
    if (descriptor == null) {
      return null;
    }
    final File sourceRoot = descriptor.root;

    final File parent = FileUtilRt.getParentFile(sourceFile);
    if (parent == null) {
      return null;
    }

    if (FileUtil.filesEqual(parent, sourceRoot)) {
      return genFolder.getPath();
    }
    final String relativePath = FileUtil.getRelativePath(sourceRoot, parent);
    assert relativePath != null;
    return genFolder.getPath() + '/' + relativePath;
  }

  @Nullable
  private static Map<JpsModule, MyModuleData> computeModuleDatas(@NotNull Collection<JpsModule> modules, @NotNull CompileContext context)
    throws IOException {
    final Map<JpsModule, MyModuleData> moduleDataMap = new HashMap<JpsModule, MyModuleData>();

    boolean success = true;

    for (JpsModule module : modules) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension == null) {
        continue;
      }

      final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (platform == null) {
        success = false;
        continue;
      }

      final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(extension);
      if (manifestFile == null || !manifestFile.exists()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.manifest.not.found", module.getName())));
        success = false;
        continue;
      }

      final String packageName = parsePackageNameFromManifestFile(manifestFile);
      if (packageName == null || packageName.length() == 0) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.errors.package.not.specified", module.getName())));
        success = false;
        continue;
      }

      if (!AndroidCommonUtils.contains2Identifiers(packageName)) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, extension.isLibrary() ? BuildMessage.Kind.WARNING : BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle
                                                     .message("android.jps.errors.incorrect.package.name", module.getName())));
        success = false;
        continue;
      }

      moduleDataMap.put(module, new MyModuleData(platform, extension, manifestFile, packageName));
    }

    return success ? moduleDataMap : null;
  }

  @Nullable
  private static String computePackageForFile(@NotNull CompileContext context, @NotNull File file) throws IOException {
    final JavaSourceRootDescriptor descriptor = context.getProjectDescriptor().getBuildRootIndex().getModuleAndRoot(context, file);
    if (descriptor == null) {
      return null;
    }

    final String relPath = FileUtil.getRelativePath(descriptor.root, FileUtilRt.getParentFile(file));
    if (relPath == null) {
      return null;
    }

    return FileUtil.toSystemIndependentName(relPath).replace('/', '.');
  }

  @Override
  public String getDescription() {
    return "Android Source Generating Builder";
  }

  // support for lib<->lib and app<->lib circular dependencies
  // see IDEA-79737 for details
  private static boolean isLibraryWithBadCircularDependency(@NotNull JpsAndroidModuleExtension extension)
    throws IOException {
    if (!extension.isLibrary()) {
      return false;
    }
    final List<JpsAndroidModuleExtension> dependencies = AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), false);

    for (JpsAndroidModuleExtension depExtension : dependencies) {
      final List<JpsAndroidModuleExtension> depDependencies = AndroidJpsUtil.getAllAndroidDependencies(depExtension.getModule(), true);

      if (depDependencies.contains(extension) &&
          dependencies.contains(depExtension) &&
          (depExtension.getModule().getName().compareTo(extension.getModule().getName()) < 0 || !depExtension.isLibrary())) {
        return true;
      }
    }
    return false;
  }

  private static void addMessages(@NotNull CompileContext context,
                                  @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                  @NotNull String sourcePath,
                                  @NotNull String builderName) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      final AndroidCompilerMessageKind kind = entry.getKey();
      final BuildMessage.Kind buildMessageKind = AndroidJpsUtil.toBuildMessageKind(kind);

      if (buildMessageKind == null) {
        continue;
      }

      for (String message : entry.getValue()) {
        context.processMessage(new CompilerMessage(builderName, buildMessageKind, message, sourcePath));
      }
    }
  }

  private static boolean toLaunchProGuard(@NotNull CompileContext context, @NotNull JpsAndroidModuleExtension extension) {
    return extension.isRunProguard() ||
           context.getBuilderParameter(AndroidCommonUtils.PROGUARD_CFG_PATH_OPTION) != null;
  }

  @Nullable
  public static JpsModule findCircularDependencyOnLibraryWithSamePackage(@NotNull JpsAndroidModuleExtension extension,
                                                                      @NotNull Map<JpsModule, String> packageMap) {
    final String aPackage = packageMap.get(extension.getModule());
    if (aPackage == null || aPackage.length() == 0) {
      return null;
    }

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), true)) {
      if (aPackage.equals(packageMap.get(depExtension.getModule()))) {
        final List<JpsAndroidModuleExtension> depDependencies = AndroidJpsUtil.getAllAndroidDependencies(depExtension.getModule(), false);

        if (depDependencies.contains(extension)) {
          // circular dependency on library with the same package
          return depExtension.getModule();
        }
      }
    }
    return null;
  }

  private static class MyModuleData {
    private final AndroidPlatform myPlatform;
    private final JpsAndroidModuleExtension myAndroidExtension;
    private final File myManifestFileForCompiler;
    private final String myPackage;

    private MyModuleData(@NotNull AndroidPlatform platform,
                         @NotNull JpsAndroidModuleExtension extension,
                         @NotNull File manifestFileForCompiler,
                         @NotNull String aPackage) {
      myPlatform = platform;
      myAndroidExtension = extension;
      myManifestFileForCompiler = manifestFileForCompiler;
      myPackage = aPackage;
    }

    @NotNull
    public AndroidPlatform getPlatform() {
      return myPlatform;
    }

    @NotNull
    public JpsAndroidModuleExtension getAndroidExtension() {
      return myAndroidExtension;
    }

    @NotNull
    public File getManifestFileForCompiler() {
      return myManifestFileForCompiler;
    }

    @NotNull
    public String getPackage() {
      return myPackage;
    }
  }
}
