package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPackagingBuildTarget extends AndroidBuildTarget {
  public AndroidPackagingBuildTarget(@NotNull JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    final File moduleOutputDir = context.getProjectPaths().getModuleOutputDir(myModule, false);
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);

    if (moduleOutputDir == null || extension == null) {
      return Collections.emptyList();
    }
    final String outputPath = AndroidJpsUtil.getApkPath(extension, moduleOutputDir);

    if (outputPath == null) {
      return Collections.emptyList();
    }
    final String afpFile = AndroidCommonUtils.addSuffixToFileName(
      outputPath, AndroidCommonUtils.ANDROID_FINAL_PACKAGE_FOR_ARTIFACT_SUFFIX);
    return Collections.singletonList(new File(FileUtil.toSystemDependentName(afpFile)));
  }

  @Override
  protected void fillDependencies(List<BuildTarget<?>> result) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);

    if (extension != null && !extension.isLibrary()) {
      // todo: remove this when AndroidPackagingBuilder will be fully target-based
      result.add(new AndroidDexBuildTarget(myModule));
    }
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidPackagingBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, "Packaging");
    }

    @Override
    public AndroidPackagingBuildTarget createBuildTarget(@NotNull JpsModule module) {
      return new AndroidPackagingBuildTarget(module);
    }
  }
}
