package org.jetbrains.jps.android.builder;

import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
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
public class AndroidLibraryPackagingTarget extends AndroidBuildTarget {
  public AndroidLibraryPackagingTarget(@NotNull JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    final File moduleOutputDir = ProjectPaths.getModuleOutputDir(myModule, false);

    if (moduleOutputDir != null) {
      return Collections.<BuildRootDescriptor>singletonList(
        new AndroidClassesDirBuildRootDescriptor(this, moduleOutputDir));
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputFile(context));
  }

  @NotNull
  public File getOutputFile(CompileContext context) {
    final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, myModule);
    return new File(dir, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidLibraryPackagingTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.LIBRARY_PACKAGING_BUILD_TARGET_ID, "Library Packaging");
    }

    @Nullable
    @Override
    public AndroidLibraryPackagingTarget createBuildTarget(@NotNull JpsAndroidModuleExtension extension) {
      return extension.isLibrary() ? new AndroidLibraryPackagingTarget(extension.getModule()) : null;
    }
  }
}
