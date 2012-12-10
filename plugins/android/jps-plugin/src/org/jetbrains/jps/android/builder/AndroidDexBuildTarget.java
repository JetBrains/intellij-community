package org.jetbrains.jps.android.builder;

import com.intellij.openapi.util.io.FileUtil;
import gnu.trove.THashSet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.AndroidDependencyProcessor;
import org.jetbrains.jps.android.AndroidDependencyType;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.AndroidPlatform;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDexBuildTarget extends AndroidBuildTarget {
  public AndroidDexBuildTarget(@NotNull JpsModule module) {
    super(MyTargetType.INSTANCE, module);
  }

  @Override
  public void writeConfiguration(PrintWriter out, BuildDataPaths dataPaths, BuildRootIndex buildRootIndex) {
    super.writeConfiguration(out, dataPaths, buildRootIndex);

    // todo: write compiler settings
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(myModule);
    assert extension != null;

    if (extension.isLibrary()) {
      return Collections.emptyList();
    }
    final Set<String> libPackages = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> appClassesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> javaClassesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);
    final Set<String> libClassesDirs = new THashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

    final File moduleClassesDir = new ModuleBuildTarget(
      myModule, JavaModuleBuildTargetType.PRODUCTION).getOutputDir();

    if (moduleClassesDir != null) {
      appClassesDirs.add(moduleClassesDir.getPath());
    }

    AndroidJpsUtil.processClasspath(dataPaths, myModule, new AndroidDependencyProcessor() {
      @Override
      public void processAndroidLibraryPackage(@NotNull File file) {
        libPackages.add(file.getPath());
      }

      @Override
      public void processAndroidLibraryOutputDirectory(@NotNull File dir) {
        libClassesDirs.add(dir.getPath());
      }

      @Override
      public void processJavaModuleOutputDirectory(@NotNull File dir) {
        javaClassesDirs.add(dir.getPath());
      }

      @Override
      public boolean isToProcess(@NotNull AndroidDependencyType type) {
        return type == AndroidDependencyType.ANDROID_LIBRARY_PACKAGE ||
               type == AndroidDependencyType.ANDROID_LIBRARY_OUTPUT_DIRECTORY ||
               type == AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR;
      }
    });

    if (extension.isPackTestCode()) {
      final File testModuleClassesDir = new ModuleBuildTarget(
        myModule, JavaModuleBuildTargetType.TEST).getOutputDir();

      if (testModuleClassesDir != null && testModuleClassesDir.isDirectory()) {
        appClassesDirs.add(testModuleClassesDir.getPath());
      }
    }
    final List<BuildRootDescriptor> result = new ArrayList<BuildRootDescriptor>();

    for (String classesDir : appClassesDirs) {
      result.add(new MyClassesDirBuildRootDescriptor(this, new File(classesDir), ClassesDirType.ANDROID_APP));
    }

    for (String classesDir : libClassesDirs) {
      result.add(new MyClassesDirBuildRootDescriptor(this, new File(classesDir), ClassesDirType.ANDROID_LIB));
    }

    for (String classesDir : javaClassesDirs) {
      result.add(new MyClassesDirBuildRootDescriptor(this, new File(classesDir), ClassesDirType.JAVA));
    }

    for (String jar : libPackages) {
      result.add(new MyJarBuildRootDescriptor(this, new File(jar), true));
    }
    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(myModule, null, null);

    for (String jar : AndroidJpsUtil.getExternalLibraries(dataPaths, myModule, platform)) {
      result.add(new MyJarBuildRootDescriptor(this, new File(jar), false));
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<File> getOutputRoots(CompileContext context) {
    return Collections.singletonList(getOutputFile(context));
  }

  @NotNull
  public File getOutputFile(CompileContext context) {
    final File dir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, myModule);
    return new File(dir, AndroidCommonUtils.CLASSES_FILE_NAME);
  }

  public static class MyTargetType extends AndroidBuildTargetType<AndroidDexBuildTarget> {
    public static final MyTargetType INSTANCE = new MyTargetType();

    private MyTargetType() {
      super(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, "DEX");
    }

    @Override
    public AndroidDexBuildTarget createBuildTarget(@NotNull JpsModule module) {
      return new AndroidDexBuildTarget(module);
    }
  }

  public enum ClassesDirType {
    ANDROID_APP, ANDROID_LIB, JAVA
  }

  public static class MyClassesDirBuildRootDescriptor extends AndroidClassesDirBuildRootDescriptor {
    private final ClassesDirType myClassesDirType;

    public MyClassesDirBuildRootDescriptor(@NotNull BuildTarget target,
                                           @NotNull File root,
                                           @NotNull ClassesDirType classesDirType) {
      super(target, root);
      myClassesDirType = classesDirType;
    }

    @NotNull
    public ClassesDirType getClassesDirType() {
      return myClassesDirType;
    }
  }

  public static class MyJarBuildRootDescriptor extends AndroidFileBasedBuildRootDescriptor {
    private final boolean myLibPackage;

    public MyJarBuildRootDescriptor(@NotNull BuildTarget target, @NotNull File file, boolean libPackage) {
      super(target, file);
      myLibPackage = libPackage;
    }

    public boolean isLibPackage() {
      return myLibPackage;
    }
  }
}
