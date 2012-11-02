package org.jetbrains.jps.android;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryPackagingBuilder extends ModuleLevelBuilder {
  @NonNls private static final String BUILDER_NAME = "Android Library Packaging";

  protected AndroidLibraryPackagingBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk, DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder) throws ProjectBuildException {
    if (chunk.containsTests() || !AndroidJpsUtil.containsAndroidFacet(chunk) || AndroidJpsUtil.isLightBuild(context)) {
      return ExitCode.NOTHING_DONE;
    }

    try {
      return doBuild(context, chunk);
    }
    catch (Exception e) {
      return AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static ModuleLevelBuilder.ExitCode doBuild(CompileContext context, ModuleChunk chunk) throws IOException {
    boolean success = true;
    boolean doneSomething = false;

    for (JpsModule module : chunk.getModules()) {
      final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
      if (extension == null || !extension.isLibrary()) {
        continue;
      }

      final ProjectPaths projectPaths = context.getProjectPaths();
      File outputDir = AndroidJpsUtil.getDirectoryForIntermediateArtifacts(context, module);
      outputDir = AndroidJpsUtil.createDirIfNotExist(outputDir, context, BUILDER_NAME);
      if (outputDir == null) {
        success = false;
        continue;
      }

      final File classesDir = projectPaths.getModuleOutputDir(module, false);
      if (classesDir == null || !classesDir.isDirectory()) {
        continue;
      }

      if (context.isMake()) {
        final Set<String> dirtyOutputDirs = context.getUserData(AndroidDexBuilder.DIRTY_OUTPUT_DIRS);
        assert dirtyOutputDirs != null;
        if (!dirtyOutputDirs.contains(classesDir.getPath())) {
          continue;
        }
      }
      final Set<String> subdirs = new HashSet<String>();
      AndroidJpsUtil.addSubdirectories(classesDir, subdirs);

      if (subdirs.size() > 0) {
        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.library.packaging", module.getName())));
        final File outputJarFile = new File(outputDir, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
        doneSomething = true;
        try {
          AndroidCommonUtils.packClassFilesIntoJar(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.toStringArray(subdirs), outputJarFile);
        }
        catch (IOException e) {
          AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
          success = false;
        }
      }
    }
    return success ? (doneSomething ? ExitCode.OK : ExitCode.NOTHING_DONE) : ExitCode.ABORT;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }
}
