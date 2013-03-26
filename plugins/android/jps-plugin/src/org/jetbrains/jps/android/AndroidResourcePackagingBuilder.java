package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtilRt;
import com.intellij.util.Processor;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.builder.AndroidResourcePackagingBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcePackagingBuilder extends TargetBuilder<BuildRootDescriptor, AndroidResourcePackagingBuildTarget> {
  @NonNls private static final String BUILDER_NAME = "Android Resource Packaging";

  protected AndroidResourcePackagingBuilder() {
    super(Collections.singletonList(AndroidResourcePackagingBuildTarget.MyTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull AndroidResourcePackagingBuildTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidResourcePackagingBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    final boolean releaseBuild = AndroidJpsUtil.isReleaseBuild(context);
    final AndroidPackagingStateStorage packagingStateStorage =
      context.getProjectDescriptor().dataManager.getStorage(target, AndroidPackagingStateStorage.Provider.INSTANCE);

    if (!holder.hasDirtyFiles() && !holder.hasRemovedFiles()) {
      final AndroidPackagingStateStorage.MyState savedState = packagingStateStorage.read();

      if (savedState != null && savedState.isRelease() == releaseBuild) {
        return;
      }
    }
    assert !AndroidJpsUtil.isLightBuild(context);

    if (!packageResources(target, context, outputConsumer, releaseBuild)) {
      throw new ProjectBuildException();
    }
    packagingStateStorage.saveState(new AndroidPackagingStateStorage.MyState(releaseBuild));
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  private static boolean packageResources(@NotNull AndroidResourcePackagingBuildTarget target,
                                          @NotNull CompileContext context,
                                          @NotNull BuildOutputConsumer outputConsumer,
                                          boolean releaseBuild) {
    final JpsModule module = target.getModule();
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    assert extension != null && !extension.isLibrary();

    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.packaging.resources", module.getName())));

    final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(extension);

    if (manifestFile == null || !manifestFile.exists()) {
      context.processMessage(new CompilerMessage(
        BUILDER_NAME, BuildMessage.Kind.ERROR,
        AndroidJpsBundle.message("android.jps.errors.manifest.not.found", module.getName())));
      return false;
    }
    final ArrayList<String> assetsDirPaths = new ArrayList<String>();
    AndroidResourcePackagingBuildTarget.collectAssetDirs(extension, assetsDirPaths, true);

    final String outputFilePath = target.getOutputFile(context).getPath();
    File outputDir = new File(outputFilePath).getParentFile();
    assert outputDir != null;
    outputDir = AndroidJpsUtil.createDirIfNotExist(outputDir, context, BUILDER_NAME);

    if (outputDir == null) {
      return false;
    }
    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);

    if (platform == null) {
      return false;
    }
    final IAndroidTarget androidTarget = platform.getTarget();
    final String[] resourceDirPaths = AndroidJpsUtil.collectResourceDirsForCompilation(extension, true, context, true);

    final String customManifestPackage = extension.isUseCustomManifestPackage()
                                         ? extension.getCustomManifestPackage()
                                         : null;
    return doPackageResources(context, manifestFile, androidTarget, resourceDirPaths, ArrayUtil.toStringArray(assetsDirPaths),
                              outputFilePath, releaseBuild, module.getName(), outputConsumer, customManifestPackage);
  }

  private static boolean doPackageResources(@NotNull final CompileContext context,
                                            @NotNull File manifestFile,
                                            @NotNull IAndroidTarget target,
                                            @NotNull String[] resourceDirPaths,
                                            @NotNull String[] assetsDirPaths,
                                            @NotNull String outputPath,
                                            boolean releasePackage,
                                            @NotNull String moduleName,
                                            @NotNull BuildOutputConsumer outputConsumer,
                                            @Nullable String customManifestPackage) {
    try {
      final IgnoredFileIndex ignoredFileIndex = context.getProjectDescriptor().getIgnoredFileIndex();

      final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApt
        .packageResources(target, -1, manifestFile.getPath(), resourceDirPaths, assetsDirPaths, outputPath, null,
                          !releasePackage, 0, customManifestPackage, new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            return !ignoredFileIndex.isIgnored(PathUtilRt.getFileName(pathname.getPath()));
          }
        });

      AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, moduleName);
      final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).size() == 0;

      if (success) {
        final List<String> srcFiles = new ArrayList<String>();
        srcFiles.add(manifestFile.getPath());
        fillRecursively(resourceDirPaths, srcFiles);
        fillRecursively(assetsDirPaths, srcFiles);

        outputConsumer.registerOutputFile(new File(outputPath), srcFiles);
      }
      return success;
    }
    catch (final IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
  }

  private static void fillRecursively(String[] roots, final List<String> result) {
    for (String root : roots) {
      FileUtil.processFilesRecursively(new File(root), new Processor<File>() {
        @Override
        public boolean process(File file) {
          if (file.isFile()) {
            result.add(file.getPath());
          }
          return true;
        }
      });
    }
  }
}
