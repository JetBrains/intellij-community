package org.jetbrains.jps.android.builder;

import com.intellij.util.ArrayUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.AndroidPlatform;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.impl.BuildRootDescriptorImpl;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.ArrayList;
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
  public static String[] collectNativeLibsFolders(@NotNull JpsAndroidModuleExtension extension, boolean checkExistence) {
    final List<String> result = new ArrayList<String>();
    final File libsDir = extension.getNativeLibsDir();

    if (libsDir != null && (!checkExistence || libsDir.exists())) {
      result.add(libsDir.getPath());
    }

    for (JpsAndroidModuleExtension depExtension : AndroidJpsUtil.getAllAndroidDependencies(extension.getModule(), true)) {
      final File depLibsDir = depExtension.getNativeLibsDir();

      if (depLibsDir != null && (!checkExistence || depLibsDir.exists())) {
        result.add(depLibsDir.getPath());
      }
    }
    return ArrayUtil.toStringArray(result);
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    final File resPackage = AndroidResourcePackagingBuildTarget.getOutputFile(dataPaths, myModule);
    final File classesDexFile = AndroidDexBuildTarget.getOutputFile(dataPaths, myModule);

    final List<BuildRootDescriptor> roots = new ArrayList<BuildRootDescriptor>();

    roots.add(new BuildRootDescriptorImpl(this, resPackage));
    roots.add(new BuildRootDescriptorImpl(this, classesDexFile));

    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(myModule, null, null);

    if (platform != null) {
      for (String jarOrLibDir : AndroidJpsUtil.getExternalLibraries(dataPaths, myModule, platform, false)) {
        roots.add(new BuildRootDescriptorImpl(this, new File(jarOrLibDir), false));
      }
    }

    for (File sourceRoot : AndroidJpsUtil.getSourceRootsForModuleAndDependencies(myModule)) {
      roots.add(new BuildRootDescriptorImpl(this, sourceRoot));
    }
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;

    for (String nativeLibDir : collectNativeLibsFolders(extension, false)) {
      roots.add(new BuildRootDescriptorImpl(this, new File(nativeLibDir)));
    }
    return roots;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    final File moduleOutputDir = ProjectPaths.getModuleOutputDir(myModule, false);
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);

    if (moduleOutputDir == null || extension == null) {
      return Collections.emptyList();
    }
    final String outputPath = AndroidJpsUtil.getApkPath(extension, moduleOutputDir);

    if (outputPath == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new File(outputPath));
  }

  @Override
  protected void fillDependencies(List<BuildTarget<?>> result) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);

    if (extension != null && !extension.isLibrary()) {
      // todo: remove this when AndroidPackagingBuilder will be fully target-based
      result.add(new AndroidDexBuildTarget(myModule));
      result.add(new AndroidResourcePackagingBuildTarget(myModule));
    }
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidPackagingBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, "Packaging");
    }

    @Override
    public AndroidPackagingBuildTarget createBuildTarget(@NotNull JpsAndroidModuleExtension extension) {
      return new AndroidPackagingBuildTarget(extension.getModule());
    }
  }
}
