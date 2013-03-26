package org.jetbrains.jps.android;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.builder.AndroidLibraryPackagingTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryPackagingBuilder extends TargetBuilder<BuildRootDescriptor, AndroidLibraryPackagingTarget> {
  @NonNls private static final String BUILDER_NAME = "Android Library Packaging";

  protected AndroidLibraryPackagingBuilder() {
    super(Collections.singletonList(AndroidLibraryPackagingTarget.MyTargetType.INSTANCE));
  }

  @Override
  public void build(@NotNull AndroidLibraryPackagingTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidLibraryPackagingTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (!holder.hasDirtyFiles() && !holder.hasRemovedFiles()) {
      return;
    }
    assert !AndroidJpsUtil.isLightBuild(context);

    if (!doBuild(context, target.getModule(), outputConsumer)) {
      throw new ProjectBuildException();
    }
  }

  private static boolean doBuild(CompileContext context, JpsModule module, BuildOutputConsumer outputConsumer) throws IOException {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    if (extension == null || !extension.isLibrary()) {
      return true;
    }

    File outputDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
    outputDir = AndroidJpsUtil.createDirIfNotExist(outputDir, context, BUILDER_NAME);
    if (outputDir == null) {
      return false;
    }

    final File classesDir = ProjectPaths.getModuleOutputDir(module, false);
    if (classesDir == null || !classesDir.isDirectory()) {
      return true;
    }
    final Set<String> subdirs = new HashSet<String>();
    AndroidJpsUtil.addSubdirectories(classesDir, subdirs);

    if (subdirs.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.library.packaging", module.getName())));
      final File outputJarFile = new File(outputDir, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
      final List<String> srcFiles;

      try {
        srcFiles = AndroidCommonUtils.packClassFilesIntoJar(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.toStringArray(subdirs), outputJarFile);
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
        return false;
      }
      final AndroidBuildTestingManager testingManager = AndroidBuildTestingManager.getTestingManager();

      if (testingManager != null && outputJarFile.isFile()) {
        testingManager.getCommandExecutor().checkJarContent("library_package_jar", outputJarFile.getPath());
      }

      if (srcFiles.size() > 0) {
        outputConsumer.registerOutputFile(outputJarFile, srcFiles);
      }
    }
    return true;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
