package org.jetbrains.jps.android;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApkBuilder;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ProjectPaths;
import org.jetbrains.jps.android.builder.AndroidDexBuildTarget;
import org.jetbrains.jps.android.builder.AndroidPackagingBuildTarget;
import org.jetbrains.jps.android.builder.AndroidResourcePackagingBuildTarget;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.BuildOutputConsumer;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.DirtyFilesHolder;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsCompilerExcludes;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidPackagingBuilder extends TargetBuilder<BuildRootDescriptor, AndroidPackagingBuildTarget> {
  @NonNls private static final String BUILDER_NAME = "Android Packager";


  public AndroidPackagingBuilder() {
    super(Collections.singletonList(AndroidPackagingBuildTarget.MyTargetType.INSTANCE));
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return BUILDER_NAME;
  }

  @Override
  public void build(@NotNull AndroidPackagingBuildTarget target,
                    @NotNull DirtyFilesHolder<BuildRootDescriptor, AndroidPackagingBuildTarget> holder,
                    @NotNull BuildOutputConsumer outputConsumer,
                    @NotNull CompileContext context) throws ProjectBuildException, IOException {
    if (AndroidJpsUtil.isLightBuild(context)) {
      return;
    }
    final boolean hasDirtyFiles = holder.hasDirtyFiles() || holder.hasRemovedFiles();

    try {
      if (!doPackaging(target, context, target.getModule(), hasDirtyFiles, outputConsumer)) {
        throw new ProjectBuildException();
      }
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      AndroidJpsUtil.handleException(context, e, BUILDER_NAME);
    }
  }

  private static boolean doPackaging(@NotNull BuildTarget<?> target,
                                     @NotNull CompileContext context,
                                     @NotNull JpsModule module,
                                     boolean hasDirtyFiles,
                                     @NotNull BuildOutputConsumer outputConsumer) throws IOException {
    final boolean release = AndroidJpsUtil.isReleaseBuild(context);
    final BuildDataManager dataManager = context.getProjectDescriptor().dataManager;

    boolean success = true;

    final AndroidApkBuilderConfigStateStorage.Provider builderStateStoragetProvider =
      new AndroidApkBuilderConfigStateStorage.Provider("apk_builder_config");
    final AndroidApkBuilderConfigStateStorage apkBuilderConfigStateStorage =
      dataManager.getStorage(target, builderStateStoragetProvider);

    final AndroidPackagingStateStorage packagingStateStorage =
      dataManager.getStorage(target, AndroidPackagingStateStorage.Provider.INSTANCE);

    try {
      if (!doPackagingForModule(context, module, apkBuilderConfigStateStorage, packagingStateStorage,
                                release, hasDirtyFiles, outputConsumer)) {
        success = false;
      }
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      success = false;
    }
    return success;
  }

  private static boolean doPackagingForModule(@NotNull CompileContext context,
                                              @NotNull JpsModule module,
                                              @NotNull AndroidApkBuilderConfigStateStorage apkBuilderConfigStateStorage,
                                              @NotNull AndroidPackagingStateStorage packagingStateStorage,
                                              boolean release,
                                              boolean hasDirtyFiles,
                                              @NotNull BuildOutputConsumer outputConsumer) throws IOException {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    if (extension == null || extension.isLibrary()) {
      return true;
    }

    final String[] sourceRoots = AndroidJpsUtil.toPaths(AndroidJpsUtil.getSourceRootsForModuleAndDependencies(module));
    Arrays.sort(sourceRoots);

    final File moduleOutputDir = ProjectPaths.getModuleOutputDir(module, false);
    if (moduleOutputDir == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.output.dir.not.specified", module.getName())));
      return false;
    }

    final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
    if (platform == null) {
      return false;
    }

    final Set<String> externalJarsSet = new HashSet<String>();

    for (String jarPath : AndroidJpsUtil.getExternalLibraries(context, module, platform)) {
      if (new File(jarPath).exists()) {
        externalJarsSet.add(jarPath);
      }
    }
    final BuildDataPaths dataPaths = context.getProjectDescriptor().dataManager.getDataPaths();
    final File resPackage = AndroidResourcePackagingBuildTarget.getOutputFile(dataPaths, module);
    final File classesDexFile = AndroidDexBuildTarget.getOutputFile(dataPaths, module);

    final String sdkPath = platform.getSdk().getHomePath();
    final String outputPath = AndroidJpsUtil.getApkPath(extension, moduleOutputDir);

    if (outputPath == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
        .message("android.jps.errors.cannot.compute.output.apk", module.getName())));
      return false;
    }
    final String customKeyStorePath = FileUtil.toSystemDependentName(extension.getCustomDebugKeyStorePath());
    final String[] nativeLibDirs = AndroidPackagingBuildTarget.collectNativeLibsFolders(extension, true);
    final String resPackagePath = resPackage.getPath();

    final String classesDexFilePath = classesDexFile.getPath();
    final String[] externalJars = ArrayUtil.toStringArray(externalJarsSet);
    Arrays.sort(externalJars);

    final List<AndroidNativeLibData> additionalNativeLibs = extension.getAdditionalNativeLibs();

    final AndroidApkBuilderConfigState currentApkBuilderConfigState =
      new AndroidApkBuilderConfigState(outputPath, customKeyStorePath, additionalNativeLibs);

    if (!hasDirtyFiles) {
      final AndroidApkBuilderConfigState savedApkBuilderConfigState = apkBuilderConfigStateStorage.getState(module.getName());
      final AndroidPackagingStateStorage.MyState packagingState = packagingStateStorage.read();

      if (currentApkBuilderConfigState.equalsTo(savedApkBuilderConfigState) &&
          packagingState != null && packagingState.isRelease() == release) {
        return true;
      }
    }
    context.processMessage(new ProgressMessage(
      AndroidJpsBundle.message("android.jps.progress.packaging", AndroidJpsUtil.getApkName(module))));

    final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidApkBuilder
      .execute(resPackagePath, classesDexFilePath, sourceRoots, externalJars,
               nativeLibDirs, additionalNativeLibs, outputPath, release, sdkPath, customKeyStorePath,
               new MyExcludedSourcesFilter(context.getProjectDescriptor().getProject()));

    if (messages.get(AndroidCompilerMessageKind.ERROR).size() == 0) {
      final List<String> srcFiles = new ArrayList<String>();
      srcFiles.add(resPackagePath);
      srcFiles.add(classesDexFilePath);

      for (String sourceRoot : sourceRoots) {
        FileUtil.processFilesRecursively(new File(sourceRoot), new Processor<File>() {
          @Override
          public boolean process(File file) {
            if (file.isFile()) {
              srcFiles.add(file.getPath());
            }
            return true;
          }
        });
      }
      Collections.addAll(srcFiles, externalJars);

      for (String nativeLibDir : nativeLibDirs) {
        FileUtil.processFilesRecursively(new File(nativeLibDir), new Processor<File>() {
          @Override
          public boolean process(File file) {
            if (file.isFile()) {
              srcFiles.add(file.getPath());
            }
            return true;
          }
        });
      }
      outputConsumer.registerOutputFile(new File(outputPath), srcFiles);
    }
    AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME, module.getName());
    final boolean success = messages.get(AndroidCompilerMessageKind.ERROR).isEmpty();

    apkBuilderConfigStateStorage.update(module.getName(), success ? currentApkBuilderConfigState : null);
    packagingStateStorage.saveState(new AndroidPackagingStateStorage.MyState(release));
    return success;
  }

  private static class MyExcludedSourcesFilter implements Condition<File> {
    private final JpsCompilerExcludes myExcludes;

    public MyExcludedSourcesFilter(@NotNull JpsProject project) {
      myExcludes = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project).getCompilerExcludes();
    }

    @Override
    public boolean value(File file) {
      return !myExcludes.isExcluded(file);
    }
  }
}
