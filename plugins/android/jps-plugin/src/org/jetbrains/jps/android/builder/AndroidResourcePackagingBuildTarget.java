package org.jetbrains.jps.android.builder;

import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcePackagingBuildTarget extends AndroidBuildTarget {
  public AndroidResourcePackagingBuildTarget(@NotNull JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @Override
  public void writeConfiguration(ProjectDescriptor pd, PrintWriter out) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;

    if (extension.isUseCustomManifestPackage()) {
      out.println(extension.getCustomManifestPackage());
    }
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;

    final String[] resourceDirs = AndroidJpsUtil.collectResourceDirsForCompilation(extension, true, dataPaths, false);

    final List<String> assertDirs = new ArrayList<String>();
    collectAssetDirs(extension, assertDirs, false);

    final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(extension);
    final List<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();

    for (String resourceDir : resourceDirs) {
      result.add(new BuildRootDescriptorImpl(this, new File(resourceDir)));
    }

    for (String assetDir : assertDirs) {
      result.add(new BuildRootDescriptorImpl(this, new File(assetDir)));
    }

    if (manifestFile != null) {
      result.add(new BuildRootDescriptorImpl(this, manifestFile));
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputFile(context));
  }

  @NotNull
  public File getOutputFile(@NotNull CompileContext context) {
    return getOutputFile(context.getProjectDescriptor().dataManager.getDataPaths(), myModule);
  }

  @NotNull
  public static File getOutputFile(@NotNull BuildDataPaths dataPaths, @NotNull JpsModule module) {
    final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(dataPaths, module);
    return new File(dir.getPath(), module.getName() + ".apk.res");
  }

  public static void collectAssetDirs(@NotNull JpsAndroidModuleExtension extension, @NotNull List<String> result, boolean checkExistence) {
    final File assetsDir = extension.getAssetsDir();

    if (assetsDir != null && (!checkExistence || assetsDir.exists())) {
      result.add(assetsDir.getPath());
    }

    if (extension.isIncludeAssetsFromLibraries()) {
      for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), true)) {
        final File depAssetsDir = depExtension.getAssetsDir();

        if (depAssetsDir != null && (!checkExistence || depAssetsDir.exists())) {
          result.add(depAssetsDir.getPath());
        }
      }
    }
  }

  @Override
  protected void fillDependencies(List<BuildTarget<?>> result) {
    super.fillDependencies(result);
    result.add(new AndroidResourceCachingBuildTarget(myModule));

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(myModule, true)) {
      result.add(new AndroidResourceCachingBuildTarget(depExtension.getModule()));
    }
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidResourcePackagingBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.RESOURCE_PACKAGING_BUILD_TARGET_ID, "Resource Packaging");
    }

    @Override
    public AndroidResourcePackagingBuildTarget createBuildTarget(@NotNull JpsAndroidModuleExtension extension) {
      return !extension.isLibrary() ? new AndroidResourcePackagingBuildTarget(extension.getModule()) : null;
    }
  }
}
