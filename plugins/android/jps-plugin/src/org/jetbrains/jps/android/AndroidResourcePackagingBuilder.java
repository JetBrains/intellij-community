package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.ProjectLevelBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcePackagingBuilder extends ProjectLevelBuilder {
  @NonNls private static final String BUILDER_NAME = "android-packager";

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

      if (!doPackaging(context, modules, resourcesStates, assetsStates)) {
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
        resourcesStates.put(module, new AndroidFileSetState(resourceDirs, Condition.TRUE));

        final File assetsDir = facet.getAssetsDir();
        final List<String> assetDirs = assetsDir != null
                                       ? Arrays.asList(assetsDir.getPath())
                                       : Collections.<String>emptyList();
        assetsStates.put(module, new AndroidFileSetState(assetDirs, Condition.TRUE));
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
    if (savedState != null && savedState.equalsTo(state)) {
      return true;
    }
    context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.INFO,
                                               AndroidJpsBundle.message("android.jps.progress.res.caching", module.getName())));

    final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
    if (facet == null) {
      return true;
    }

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

  private static boolean doPackaging(@NotNull CompileContext context,
                                     @NotNull Collection<Module> modules,
                                     @NotNull Map<Module, AndroidFileSetState> resourcesStates,
                                     @NotNull Map<Module, AndroidFileSetState> assetsStates) throws IOException {
    boolean success = true;

    final File dataStorageRoot = context.getDataManager().getDataStorageRoot();
    AndroidFileSetStorage devResourcesStorage = null;
    AndroidFileSetStorage releaseResourcesStorage = null;
    AndroidFileSetStorage devAssetsStorage = null;
    AndroidFileSetStorage releaseAssetsStorage = null;

    try {
      devResourcesStorage = new AndroidFileSetStorage(dataStorageRoot, "resources_packaging_dev");
      releaseResourcesStorage = new AndroidFileSetStorage(dataStorageRoot, "resources_packaging_release");

      devAssetsStorage = new AndroidFileSetStorage(dataStorageRoot, "assets_packaging_dev");
      releaseAssetsStorage = new AndroidFileSetStorage(dataStorageRoot, "assets_packaging_release");

      for (Module module : modules) {
        final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
        if (facet == null || facet.isLibrary()) {
          continue;
        }

        if (!packageResources(facet, context, devResourcesStorage, devAssetsStorage, releaseResourcesStorage, releaseAssetsStorage,
                              resourcesStates, assetsStates)) {
          success = false;
        }
      }
    }
    finally {
      if (devResourcesStorage != null) {
        devResourcesStorage.close();
      }
      if (releaseResourcesStorage != null) {
        releaseResourcesStorage.close();
      }
      if (devAssetsStorage != null) {
        devAssetsStorage.close();
      }
      if (releaseAssetsStorage != null) {
        releaseAssetsStorage.close();
      }
    }
    return success;
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

  private static boolean packageResources(@NotNull AndroidFacet facet,
                                          @NotNull CompileContext context,
                                          @NotNull AndroidFileSetStorage devResourcesStorage,
                                          @NotNull AndroidFileSetStorage devAssetsStorage,
                                          @NotNull AndroidFileSetStorage releaseResourcesStorage,
                                          @NotNull AndroidFileSetStorage releaseAssetsStorage,
                                          @NotNull Map<Module, AndroidFileSetState> resourcesStates,
                                          @NotNull Map<Module, AndroidFileSetState> assetsStates) {
    final Module module = facet.getModule();

    final boolean releaseBuild = AndroidJpsUtil.isReleaseBuild(context);
    final AndroidFileSetStorage resourcesStorage = releaseBuild ? releaseResourcesStorage : devResourcesStorage;
    final AndroidFileSetStorage assetsStorage = releaseBuild ? releaseAssetsStorage : devAssetsStorage;

    try {
      if (checkUpToDate(module, resourcesStates, resourcesStorage) &&
          checkUpToDate(module, assetsStates, assetsStorage)) {
        return true;
      }
      context.processMessage(new ProgressMessage("Packaging resources for module " + module.getName()));

      final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(facet);
      if (manifestFile == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.manifest.not.found", module.getName())));
        return false;
      }
      final File assetsDir = facet.getAssetsDir();

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

      final String outputFilePath = getOutputFile(module, outputDir).getPath();
      final String assetsDirPath = assetsDir != null ? assetsDir.getPath() : null;
      final String[] resourceDirPaths = AndroidJpsUtil.collectResourceDirsForCompilation(facet, true, context);

      if (!doPackageResources(context, manifestFile, target, resourceDirPaths, assetsDirPath, outputFilePath, releaseBuild)) {
        resourcesStorage.update(module.getName(), null);
        assetsStorage.update(module.getName(), null);
        return false;
      }
      resourcesStorage.update(module.getName(), resourcesStates.get(module));
      assetsStorage.update(module.getName(), assetsStates.get(module));
      return true;
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
                                            @Nullable String assetsDirPath,
                                            @NotNull String outputFilePath,
                                            boolean releasePackage) {
    try {
      final String outputPath = releasePackage
                                ? outputFilePath + ".release"
                                : outputFilePath;
      final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApt
        .packageResources(target, -1, manifestFile.getPath(), resourceDirPaths, assetsDirPath, outputPath, null, !releasePackage, 0);

      AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);
      return messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;
    }
    catch (final IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
  }

  @NotNull
  static File getOutputFile(@NotNull Module module, @NotNull File outputDir) {
    return new File(outputDir.getPath(), module.getName() + ".apk.res");
  }
}
