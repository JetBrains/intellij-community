package org.jetbrains.jps.android;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLibraryPackagingBuilder extends ModuleLevelBuilder {
  @NonNls private static final String BUILDER_NAME = "android-library-packager";

  protected AndroidLibraryPackagingBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @Override
  public ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    if (context.isCompilingTests() || !AndroidJpsUtil.containsAndroidFacet(chunk) || AndroidJpsUtil.isLightBuild(context)) {
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
    final AndroidFileSetStorage storage = new AndroidFileSetStorage(context.getDataManager().getDataStorageRoot(), "libs_packaging");

    try {
      boolean doneSomething = false;
      for (Module module : chunk.getModules()) {
        final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
        if (facet == null || !facet.isLibrary()) {
          continue;
        }

        final ProjectPaths projectPaths = context.getProjectPaths();
        final File outputDirectoryForPackagedFiles = AndroidJpsUtil.getOutputDirectoryForPackagedFiles(projectPaths, module);

        if (outputDirectoryForPackagedFiles == null) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
            .message("android.jps.errors.output.dir.not.specified", module.getName())));
          success = false;
          continue;
        }

        final File classesDir = projectPaths.getModuleOutputDir(module, false);
        if (classesDir == null || !classesDir.isDirectory()) {
          continue;
        }

        final Set<String> subdirs = new HashSet<String>();
        AndroidJpsUtil.addSubdirectories(classesDir, subdirs);

        final AndroidFileSetState newState = new AndroidFileSetState(subdirs, AndroidJpsUtil.CLASSES_AND_JARS_FILTER, true);
        final AndroidFileSetState oldState = storage.getState(module.getName());

        if (oldState != null && oldState.equalsTo(newState)) {
          continue;
        }

        if (subdirs.size() > 0) {
          context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.library.packaging", module.getName())));
          final File outputJarFile = new File(outputDirectoryForPackagedFiles, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
          doneSomething = true;
          try {
            AndroidCommonUtils.packClassFilesIntoJar(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.toStringArray(subdirs), outputJarFile);
            storage.update(module.getName(), newState);
          }
          catch (IOException e) {
            AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
            storage.update(module.getName(), null);
            success = false;
          }
        }
      }
      return success ? (doneSomething? ExitCode.OK : ExitCode.NOTHING_DONE) : ExitCode.ABORT;
    }
    finally {
      storage.close();
    }
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public String getDescription() {
    return "Android Library Packaging Builder";
  }
}
