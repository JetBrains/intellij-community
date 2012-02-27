package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.util.Pair;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcePackagingBuilder extends ModuleLevelBuilder {
  @NonNls private static final String BUILDER_NAME = "android-packager";

  protected AndroidResourcePackagingBuilder() {
    super(BuilderCategory.PACKAGER);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    if (context.isCompilingTests() || !AndroidJpsUtil.containsAndroidFacet(chunk) || AndroidJpsUtil.isLightBuild(context)) {
      return ModuleLevelBuilder.ExitCode.OK;
    }

    try {
      return doBuild(context, chunk);
    }
    catch (Exception e) {
      return AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static ExitCode doBuild(CompileContext context, ModuleChunk chunk) throws IOException {
    boolean success = true;

    final File dataStorageRoot = context.getDataManager().getDataStorageRoot();
    AndroidResourcesPackagingStateStorage devStorage = null;
    AndroidResourcesPackagingStateStorage releaseStorage = null;

    try {
      devStorage = new AndroidResourcesPackagingStateStorage(dataStorageRoot, false);
      releaseStorage = new AndroidResourcesPackagingStateStorage(dataStorageRoot, true);

      for (Module module : chunk.getModules()) {
        final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
        if (facet == null || facet.isLibrary()) {
          continue;
        }

        if (!packageResources(facet, context, devStorage, releaseStorage)) {
          success = false;
        }
      }
    }
    finally {
      if (devStorage != null) {
        devStorage.close();
      }
      if (releaseStorage != null) {
        releaseStorage.close();
      }
    }
    return success ? ExitCode.OK : ExitCode.ABORT;
  }

  private static boolean packageResources(@NotNull AndroidFacet facet,
                                          @NotNull CompileContext context,
                                          @NotNull AndroidResourcesPackagingStateStorage devStorage,
                                          @NotNull AndroidResourcesPackagingStateStorage releaseStorage) {
    final Module module = facet.getModule();

    try {
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

      // todo: add also png cache directories
      final String[] resourceDirPaths = AndroidJpsUtil.collectResourceDirs(facet);

      if (resourceDirPaths.length == 0) {
        // there is no resources in the module
        return true;
      }

      final List<String> assetDirPaths = assetsDirPath != null
                                         ? Arrays.asList(assetsDirPath)
                                         : Collections.<String>emptyList();

      final AndroidResourcesPackagingState newState = new AndroidResourcesPackagingState(Arrays.asList(resourceDirPaths), assetDirPaths);
      final boolean releaseBuild = AndroidJpsUtil.isReleaseBuild(context);
      final AndroidResourcesPackagingStateStorage storage = releaseBuild ? releaseStorage : devStorage;
      final AndroidResourcesPackagingState oldState = storage.getState(module.getName());

      if (oldState == null || !oldState.equalsTo(newState)) {
        context.processMessage(new ProgressMessage("Packaging resources for module " + module.getName()));

        if (!doPackageResources(context, manifestFile, target, resourceDirPaths, assetsDirPath, outputFilePath, releaseBuild)) {
          storage.update(module.getName(), null);
          return false;
        }
        storage.update(module.getName(), newState);
      }
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

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return "Android Packaging Builder";
  }
}
