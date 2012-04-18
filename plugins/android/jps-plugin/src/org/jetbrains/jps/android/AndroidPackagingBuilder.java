package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApkBuilder;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPackagingBuilder extends ProjectLevelBuilder {
  @NonNls private static final String BUILDER_NAME = "android-packager";
  @NonNls private static final String RELEASE_SUFFIX = ".release";
  @NonNls private static final String UNSIGNED_SUFFIX = ".unsigned";

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return "Android Packaging Builder";
  }

  @Override
  public void build(CompileContext context) throws ProjectBuildException {
    if (!AndroidJpsUtil.containsAndroidFacet(context.getProject()) || AndroidJpsUtil.isLightBuild(context)) {
      return;
    }
    final Collection<Module> modules = context.getProject().getModules().values();
    final Map<Module, AndroidFileSetState> resourcesStates = new HashMap<Module, AndroidFileSetState>();
    final Map<Module, AndroidFileSetState> assetsStates = new HashMap<Module, AndroidFileSetState>();

    try {
      fillStates(modules, resourcesStates, assetsStates);

      if (!doCaching(context, modules, resourcesStates)) {
        throw new ProjectBuildException();
      }

      if (!doResourcePackaging(context, modules, resourcesStates, assetsStates)) {
        throw new ProjectBuildException();
      }

      if (!doPackaging(context, modules)) {
        throw new ProjectBuildException();
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  @SuppressWarnings("unchecked")
  private static void fillStates(@NotNull Collection<Module> modules,
                                 @NotNull Map<Module, AndroidFileSetState> resourcesStates,
                                 @NotNull Map<Module, AndroidFileSetState> assetsStates) throws IOException {
    for (Module module : modules) {
      final AndroidFacet facet = AndroidJpsUtil.getFacet(module);

      if (facet != null) {
        final File resourceDir = facet.getResourceDir();
        final List<String> resourceDirs = resourceDir != null
                                          ? Arrays.asList(resourceDir.getPath())
                                          : Collections.<String>emptyList();
        resourcesStates.put(module, new AndroidFileSetState(resourceDirs, Condition.TRUE, true));

        final File assetsDir = facet.getAssetsDir();
        final List<String> assetDirs = assetsDir != null
                                       ? Arrays.asList(assetsDir.getPath())
                                       : Collections.<String>emptyList();
        assetsStates.put(module, new AndroidFileSetState(assetDirs, Condition.TRUE, true));
      }
    }
  }

  private static boolean doCaching(@NotNull CompileContext context,
                                   @NotNull Collection<Module> modules,
                                   @NotNull Map<Module, AndroidFileSetState> module2state) throws IOException {
    boolean success = true;
    final File dataStorageRoot = context.getDataManager().getDataStorageRoot();
    final AndroidFileSetStorage storage = new AndroidFileSetStorage(dataStorageRoot, "resource_caching");

    try {
      for (Module module : modules) {
        final AndroidFileSetState state = module2state.get(module);

        try {
          if (!runPngCaching(context, module, storage, state)) {
            success = false;
          }
        }
        catch (IOException e) {
          AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
        }
      }
    }
    finally {
      storage.close();
    }
    return success;
  }

  private static boolean runPngCaching(@NotNull CompileContext context,
                                       @NotNull Module module,
                                       @NotNull AndroidFileSetStorage storage,
                                       @Nullable AndroidFileSetState state)
    throws IOException {
    final AndroidFileSetState savedState = storage.getState(module.getName());
    if (context.isMake() && savedState != null && savedState.equalsTo(state)) {
      return true;
    }

    final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
    if (facet == null) {
      return true;
    }

    context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO,
                                               AndroidJpsBundle.message("android.jps.progress.res.caching", module.getName())));

    final File resourceDir = AndroidJpsUtil.getResourceDirForCompilationPath(facet);
    if (resourceDir == null) {
      return true;
    }

    final Pair<AndroidSdk, IAndroidTarget> pair = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
    if (pair == null) {
      return false;
    }

    final File resCacheDir = AndroidJpsUtil.getResourcesCacheDir(context, module);

    if (!resCacheDir.exists()) {
      if (!resCacheDir.mkdirs()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   "Cannot create directory " + resCacheDir.getPath()));
        return false;    
      }
    }

    final IAndroidTarget target = pair.second;

    final Map<AndroidCompilerMessageKind, List<String>> messages =
      AndroidApt.crunch(target, Collections.singletonList(resourceDir.getPath()), resCacheDir.getPath());

    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);

    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();
    storage.update(module.getName(), success ? state : null);
    return success;
  }

  private static boolean doResourcePackaging(@NotNull CompileContext context,
                                             @NotNull Collection<Module> modules,
                                             @NotNull Map<Module, AndroidFileSetState> resourcesStates,
                                             @NotNull Map<Module, AndroidFileSetState> assetsStates) throws IOException {
    boolean success = true;

    final File dataStorageRoot = context.getDataManager().getDataStorageRoot();
    final boolean releaseBuild = AndroidJpsUtil.isReleaseBuild(context);
    AndroidFileSetStorage resourcesStorage = null;
    AndroidFileSetStorage assetsStorage = null;

    try {
      final String resourcesStorageName = releaseBuild ? "resources_packaging_release" : "resources_packaging_dev";
      resourcesStorage = new AndroidFileSetStorage(dataStorageRoot, resourcesStorageName);

      final String assetsStorageName = releaseBuild ? "assets_packaging_release" : "assets_packaging_dev";
      assetsStorage = new AndroidFileSetStorage(dataStorageRoot, assetsStorageName);

      final Set<Module> modulesToUpdateState = new HashSet<Module>();

      for (Module module : modules) {
        final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
        if (facet == null) {
          continue;
        }

        boolean updateState = true;

        if (!facet.isLibrary() &&
            !(context.isMake() &&
              checkUpToDate(module, resourcesStates, resourcesStorage) &&
              checkUpToDate(module, assetsStates, assetsStorage))) {

          updateState = packageResources(facet, context);

          if (!updateState) {
            success = false;
          }
        }
        if (updateState) {
          modulesToUpdateState.add(module);
        }
      }

      for (Module module : modules) {
        final boolean updateState = modulesToUpdateState.contains(module);
        resourcesStorage.update(module.getName(), updateState ? resourcesStates.get(module) : null);
        assetsStorage.update(module.getName(), updateState ? assetsStates.get(module) : null);
      }
    }
    finally {
      if (resourcesStorage != null) {
        resourcesStorage.close();
      }

      if (assetsStorage != null) {
        assetsStorage.close();
      }
    }
    return success;
  }

  private static boolean doPackaging(@NotNull CompileContext context, @NotNull Collection<Module> modules) throws IOException {
    final boolean release = AndroidJpsUtil.isReleaseBuild(context);
    final File dataStorageRoot = context.getDataManager().getDataStorageRoot();

    boolean success = true;

    AndroidFileSetStorage apkFileSetStorage = null;
    AndroidApkBuilderConfigStateStorage apkBuilderConfigStateStorage = null;
    try {
      final String apkFileSetStorageName = "apk_builder_file_set" + (release ? "_release" : "_dev");
      apkFileSetStorage = new AndroidFileSetStorage(dataStorageRoot, apkFileSetStorageName);

      final String apkBuilderStateStorageName = "apk_builder_config" + (release ? "_release" : "_dev");
      apkBuilderConfigStateStorage = new AndroidApkBuilderConfigStateStorage(dataStorageRoot, apkBuilderStateStorageName);

      for (Module module : modules) {
        try {
          if (!doPackagingForModule(context, module, apkFileSetStorage, apkBuilderConfigStateStorage, release)) {
            success = false;
          }
        }
        catch (IOException e) {
          AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
          success = false;
        }
      }
    }
    finally {
      if (apkFileSetStorage != null) {
        apkFileSetStorage.close();
      }

      if (apkBuilderConfigStateStorage != null) {
        apkBuilderConfigStateStorage.close();
      }
    }

    return success;
  }

  private static boolean doPackagingForModule(@NotNull CompileContext context,
                                              @NotNull Module module,
                                              @NotNull AndroidFileSetStorage apkFileSetStorage,
                                              @NotNull AndroidApkBuilderConfigStateStorage apkBuilderConfigStateStorage,
                                              boolean release) throws IOException {
    final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
    if (facet == null || facet.isLibrary()) {
      return true;
    }

    final String[] sourceRoots = AndroidJpsUtil.toPaths(AndroidJpsUtil.getSourceRootsForModuleAndDependencies(module));
    final ProjectPaths paths = context.getProjectPaths();

    final File outputDir = AndroidJpsUtil.getOutputDirectoryForPackagedFiles(paths, module);
    if (outputDir == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.output.dir.not.specified", module.getName())));
      return false;
    }

    final Pair<AndroidSdk, IAndroidTarget> pair = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
    if (pair == null) {
      return false;
    }

    final Set<String> externalJarsSet = AndroidJpsUtil.getExternalLibraries(paths, module);
    final File resPackage = getPackagedResourcesFile(module, outputDir);

    final File classesDexFile = new File(outputDir.getPath(), AndroidCommonUtils.CLASSES_FILE_NAME);

    final String sdkPath = pair.getFirst().getSdkPath();
    final String outputPath = AndroidJpsUtil.getApkPath(facet, outputDir);
    if (outputPath == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 "Cannot compute output path for file " + AndroidJpsUtil.getApkName(module)));
      return false;
    }
    final String customKeyStorePath = FileUtil.toSystemDependentName(facet.getCustomDebugKeyStorePath());
    final String[] nativeLibDirs = collectNativeLibsFolders(facet);

    final String resPackagePath = release ? resPackage.getPath() + RELEASE_SUFFIX : resPackage.getPath();
    final String outputApkPath = release ? outputPath + UNSIGNED_SUFFIX : outputPath;
    final String classesDexFilePath = classesDexFile.getPath();
    final String[] externalJars = ArrayUtil.toStringArray(externalJarsSet);

    final AndroidFileSetState currentFileSetState =
      buildCurrentApkBuilderState(context.getProject(), resPackagePath, classesDexFilePath, nativeLibDirs,
                                  sourceRoots, externalJars, release);

    final AndroidApkBuilderConfigState currentApkBuilderConfigState =
      new AndroidApkBuilderConfigState(outputApkPath, customKeyStorePath);

    final AndroidFileSetState savedApkFileSetState = apkFileSetStorage.getState(module.getName());
    final AndroidApkBuilderConfigState savedApkBuilderConfigState = apkBuilderConfigStateStorage.getState(module.getName());

    if (context.isMake() &&
        currentFileSetState.equalsTo(savedApkFileSetState) &&
        currentApkBuilderConfigState.equalsTo(savedApkBuilderConfigState)) {
      return true;
    }
    context
      .processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.packaging", AndroidJpsUtil.getApkName(module))));

    final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApkBuilder
      .execute(resPackagePath, classesDexFilePath, sourceRoots, externalJars, nativeLibDirs, outputApkPath,
               release, sdkPath, customKeyStorePath, new MyExcludedSourcesFilter(context.getProject()));

    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);
    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();

    apkFileSetStorage.update(module.getName(), success ? currentFileSetState : null);
    apkBuilderConfigStateStorage.update(module.getName(), success ? currentApkBuilderConfigState : null);
    return success;
  }

  @SuppressWarnings("unchecked")
  private static AndroidFileSetState buildCurrentApkBuilderState(@NotNull Project project,
                                                                 @NotNull String resPackagePath,
                                                                 @NotNull String classesDexFilePath,
                                                                 @NotNull String[] nativeLibDirs,
                                                                 @NotNull String[] sourceRoots,
                                                                 @NotNull String[] externalJars,
                                                                 boolean release) {
    final List<String> roots = new ArrayList<String>();
    roots.add(resPackagePath);
    roots.add(classesDexFilePath);
    roots.addAll(Arrays.asList(externalJars));

    for (String sourceRootPath : sourceRoots) {
      final List<File> files = new ArrayList<File>();
      AndroidApkBuilder.collectStandardSourceFolderResources(new File(sourceRootPath), files, new MyExcludedSourcesFilter(project));
      roots.addAll(AndroidJpsUtil.toPaths(files));
    }

    for (String nativeLibDir : nativeLibDirs) {
      final List<File> files = new ArrayList<File>();
      AndroidApkBuilder.collectNativeLibraries(new File(nativeLibDir), files, !release);
      roots.addAll(AndroidJpsUtil.toPaths(files));
    }

    return new AndroidFileSetState(roots, Condition.TRUE, false);
  }

  @NotNull
  private static String[] collectNativeLibsFolders(@NotNull AndroidFacet facet) throws IOException {
    final List<String> result = new ArrayList<String>();
    final File libsDir = facet.getNativeLibsDir();

    if (libsDir != null) {
      result.add(libsDir.getPath());
    }

    for (AndroidFacet depFacet : AndroidJpsUtil.getAllDependentAndroidLibraries(facet.getModule())) {
      final File depLibsDir = depFacet.getNativeLibsDir();
      if (depLibsDir != null) {
        result.add(depLibsDir.getPath());
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  private static boolean checkUpToDate(@NotNull Module module,
                                       @NotNull Map<Module, AndroidFileSetState> module2state,
                                       @NotNull AndroidFileSetStorage storage) throws IOException {
    final AndroidFileSetState moduleState = module2state.get(module);
    final AndroidFileSetState savedState = storage.getState(module.getName());
    if (savedState == null || !savedState.equalsTo(moduleState)) {
      return false;
    }

    for (AndroidFacet libFacet : AndroidJpsUtil.getAllDependentAndroidLibraries(module)) {
      final Module libModule = libFacet.getModule();
      final AndroidFileSetState currentLibState = module2state.get(libModule);
      final AndroidFileSetState savedLibState = storage.getState(libModule.getName());

      if (savedLibState == null || !savedLibState.equalsTo(currentLibState)) {
        return false;
      }
    }
    return true;
  }

  private static boolean packageResources(@NotNull AndroidFacet facet, @NotNull CompileContext context) {
    final Module module = facet.getModule();

    try {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.packaging.resources", module.getName())));

      final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(facet);
      if (manifestFile == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.manifest.not.found", module.getName())));
        return false;
      }
      final ArrayList<String> assetsDirPaths = new ArrayList<String>();
      collectAssetDirs(facet, assetsDirPaths);

      final File outputDir = AndroidJpsUtil.getOutputDirectoryForPackagedFiles(context.getProjectPaths(), module);
      if (outputDir == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.errors.output.dir.not.specified", module.getName())));
        return false;
      }

      final Pair<AndroidSdk, IAndroidTarget> pair = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (pair == null) {
        return false;
      }
      final IAndroidTarget target = pair.getSecond();

      final String outputFilePath = getPackagedResourcesFile(module, outputDir).getPath();
      final String[] resourceDirPaths = AndroidJpsUtil.collectResourceDirsForCompilation(facet, true, context);

      return doPackageResources(context, manifestFile, target, resourceDirPaths, ArrayUtil.toStringArray(assetsDirPaths), outputFilePath,
                                AndroidJpsUtil.isReleaseBuild(context));
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
  }

  private static boolean doPackageResources(@NotNull final CompileContext context,
                                            @NotNull File manifestFile,
                                            @NotNull IAndroidTarget target,
                                            @NotNull String[] resourceDirPaths,
                                            @NotNull String[] assetsDirPaths,
                                            @NotNull String outputFilePath,
                                            boolean releasePackage) {
    try {
      final String outputPath = releasePackage
                                ? outputFilePath + RELEASE_SUFFIX
                                : outputFilePath;

      final IgnoredFilePatterns ignoredFilePatterns = context.getProject().getIgnoredFilePatterns();

      final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApt
        .packageResources(target, -1, manifestFile.getPath(), resourceDirPaths, assetsDirPaths, outputPath, null,
                          !releasePackage, 0, new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            return !ignoredFilePatterns.isIgnored(PathUtil.getFileName(pathname.getPath()));
          }
        });

      AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);
      return messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;
    }
    catch (final IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
  }

  private static void collectAssetDirs(@NotNull AndroidFacet facet, @NotNull List<String> result) throws IOException {
    final File assetsDir = facet.getAssetsDir();
    
    if (assetsDir != null) {
      result.add(assetsDir.getPath());
    }

    for (AndroidFacet depFacet : AndroidJpsUtil.getAllDependentAndroidLibraries(facet.getModule())) {
      final File depAssetsDir = depFacet.getAssetsDir();

      if (depAssetsDir != null) {
        result.add(depAssetsDir.getPath());
      }
    }
  }

  @NotNull
  private static File getPackagedResourcesFile(@NotNull Module module, @NotNull File outputDir) {
    return new File(outputDir.getPath(), module.getName() + ".apk.res");
  }

  private static class MyExcludedSourcesFilter implements Condition<File> {
    private final CompilerExcludes myExcludes;

    public MyExcludedSourcesFilter(@NotNull Project project) {
      myExcludes = project.getCompilerConfiguration().getExcludes();
    }

    @Override
    public boolean value(File file) {
      return !myExcludes.isExcluded(file);
    }
  }
}
