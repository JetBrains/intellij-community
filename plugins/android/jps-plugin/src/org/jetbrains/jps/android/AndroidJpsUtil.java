package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.artifact.AndroidArtifactSigningMode;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.android.model.*;
import org.jetbrains.jps.android.model.impl.JpsAndroidFinalPackageElement;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTarget;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactUtil;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaClasspathKind;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.impl.JpsJavaDependenciesEnumerationHandler;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryRoot;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidJpsUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.android.AndroidJpsUtil");

  @NonNls public static final String ANDROID_FACET_TYPE_ID = "android";
  @NonNls public static final String ANDROID_FACET_NAME = "Android";

  @NonNls public static final String ANDROID_STORAGE_DIR = "android";
  @NonNls private static final String RESOURCE_CACHE_STORAGE = "res_cache";
  @NonNls private static final String INTERMEDIATE_ARTIFACTS_STORAGE = "intermediate_artifacts";

  public static final Condition<File> CLASSES_AND_JARS_FILTER = new Condition<File>() {
    @Override
    public boolean value(File file) {
      final String ext = FileUtil.getExtension(file.getName());
      return "jar".equals(ext) || "class".equals(ext);
    }
  };
  @NonNls public static final String GENERATED_RESOURCES_DIR_NAME = "generated_resources";
  @NonNls public static final String AAPT_GENERATED_SOURCE_ROOT_NAME = "aapt";
  @NonNls public static final String AIDL_GENERATED_SOURCE_ROOT_NAME = "aidl";
  @NonNls public static final String RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME = "rs";
  @NonNls public static final String BUILD_CONFIG_GENERATED_SOURCE_ROOT_NAME = "build_config";
  @NonNls private static final String GENERATED_SOURCES_FOLDER_NAME = "generated_sources";

  private AndroidJpsUtil() {
  }

  private static boolean shouldProcessDependenciesRecursively(JpsModule module) {
    return JpsJavaDependenciesEnumerationHandler.shouldProcessDependenciesRecursively(
      JpsJavaDependenciesEnumerationHandler.createHandlers(
        Collections.singletonList(module)));
  }

  @Nullable
  public static File getMainContentRoot(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    final JpsModule module = extension.getModule();

    final List<String> contentRoots = module.getContentRootsList().getUrls();

    if (contentRoots.size() == 0) {
      return null;
    }
    final File manifestFile = extension.getManifestFile();

    if (manifestFile != null) {
      for (String rootUrl : contentRoots) {
        final File root = JpsPathUtil.urlToFile(rootUrl);

        if (FileUtil.isAncestor(root, manifestFile, true)) {
          return root;
        }
      }
    }
    return JpsPathUtil.urlToFile(contentRoots.get(0));
  }

  public static void addMessages(@NotNull CompileContext context,
                                 @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                 @NotNull String builderName,
                                 @NotNull String entryName) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      for (String message : entry.getValue()) {
        String filePath = null;
        int line = -1;
        final Matcher matcher = AndroidCommonUtils.COMPILER_MESSAGE_PATTERN.matcher(message);

        if (matcher.matches()) {
          filePath = matcher.group(1);
          line = Integer.parseInt(matcher.group(2));
        }
        final BuildMessage.Kind category = toBuildMessageKind(entry.getKey());
        if (category != null) {
          context.processMessage(
            new CompilerMessage(builderName, category, '[' + entryName + "] " + message, filePath, -1L, -1L, -1L, line, -1L));
        }
      }
    }
  }

  @Nullable
  public static JpsAndroidModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsAndroidModuleExtensionImpl.KIND);
  }

  @NotNull
  public static String[] toPaths(@NotNull File[] files) {
    final String[] result = new String[files.length];

    for (int i = 0; i < result.length; i++) {
      result[i] = files[i].getPath();
    }
    return result;
  }

  @NotNull
  public static List<String> toPaths(@NotNull Collection<File> files) {
    if (files.size() == 0) {
      return Collections.emptyList();
    }

    final List<String> result = new ArrayList<String>(files.size());
    for (File file : files) {
      result.add(file.getPath());
    }
    return result;
  }

  @NotNull
  public static File getDirectoryForIntermediateArtifacts(@NotNull CompileContext context,
                                                          @NotNull JpsModule module) {
    return getDirectoryForIntermediateArtifacts(context.getProjectDescriptor().dataManager.getDataPaths(), module);
  }

  @NotNull
  public static File getDirectoryForIntermediateArtifacts(@NotNull BuildDataPaths dataPaths,
                                                           @NotNull JpsModule module) {
    final File androidStorage = new File(dataPaths.getDataStorageRoot(), ANDROID_STORAGE_DIR);
    return new File(new File(androidStorage, INTERMEDIATE_ARTIFACTS_STORAGE), module.getName());
  }

  @Nullable
  public static File createDirIfNotExist(@NotNull File dir, @NotNull CompileContext context, @NotNull String compilerName) {
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        context.processMessage(new CompilerMessage(compilerName, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.cannot.create.directory", dir.getPath())));
        return null;
      }
    }
    return dir;
  }

  public static void addSubdirectories(@NotNull File baseDir, @NotNull Collection<String> result) {
    // only include files inside packages
    final File[] children = baseDir.listFiles();

    if (children != null) {
      for (File child : children) {
        if (child.isDirectory()) {
          result.add(child.getPath());
        }
      }
    }
  }

  @NotNull
  public static Set<String> getExternalLibraries(@NotNull CompileContext context,
                                                 @NotNull JpsModule module,
                                                 @NotNull AndroidPlatform platform) {
    final BuildDataPaths paths = context.getProjectDescriptor().dataManager.getDataPaths();
    return getExternalLibraries(paths, module, platform);
  }

  @NotNull
  public static Set<String> getExternalLibraries(@NotNull BuildDataPaths paths,
                                                 @NotNull JpsModule module,
                                                 @Nullable AndroidPlatform platform) {
    final Set<String> result = new HashSet<String>();
    final AndroidDependencyProcessor processor = new AndroidDependencyProcessor() {
      @Override
      public void processExternalLibrary(@NotNull File file) {
        result.add(file.getPath());
      }

      @Override
      public boolean isToProcess(@NotNull AndroidDependencyType type) {
        return type == AndroidDependencyType.EXTERNAL_LIBRARY;
      }
    };
    processClasspath(paths, module, processor);

    if (platform != null) {
      addAnnotationsJarIfNecessary(platform, result);
    }
    return result;
  }

  private static void addAnnotationsJarIfNecessary(@NotNull AndroidPlatform platform, @NotNull Set<String> libs) {
    if (platform.needToAddAnnotationsJarToClasspath()) {
      final String sdkHomePath = platform.getSdk().getHomePath();
      final String annotationsJarPath = FileUtil.toSystemIndependentName(sdkHomePath) + AndroidCommonUtils.ANNOTATIONS_JAR_RELATIVE_PATH;

      if (new File(annotationsJarPath).exists()) {
        libs.add(annotationsJarPath);
      }
    }
  }

  public static void processClasspath(@NotNull CompileContext context,
                                      @NotNull JpsModule module,
                                      @NotNull AndroidDependencyProcessor processor) {
    final BuildDataPaths paths = context.getProjectDescriptor().dataManager.getDataPaths();
    processClasspath(paths, module, processor);
  }

  public static void processClasspath(@NotNull BuildDataPaths paths,
                                      @NotNull JpsModule module,
                                      @NotNull AndroidDependencyProcessor processor) {
    // In a module imported from Maven dependencies are transitive, so we don't need to traverse all dependency tree
    // and compute all jars referred by library modules. Moreover it would be incorrect,
    // because Maven has dependency resolving algorithm based on versioning
    final boolean recursive = shouldProcessDependenciesRecursively(module);
    processClasspath(paths, module, processor, new HashSet<String>(), false, recursive);
  }

  private static void processClasspath(@NotNull BuildDataPaths paths,
                                       @NotNull final JpsModule module,
                                       @NotNull final AndroidDependencyProcessor processor,
                                       @NotNull final Set<String> visitedModules,
                                       final boolean exportedLibrariesOnly,
                                       final boolean recursive) {
    if (!visitedModules.add(module.getName())) {
      return;
    }
    if (processor.isToProcess(AndroidDependencyType.EXTERNAL_LIBRARY)) {
      for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, exportedLibrariesOnly)) {
        if (item instanceof JpsLibraryDependency) {
          final JpsLibrary library = ((JpsLibraryDependency)item).getLibrary();
          if (library != null) {
            for (JpsLibraryRoot root : library.getRoots(JpsOrderRootType.COMPILED)) {
              final File file = JpsPathUtil.urlToFile(root.getUrl());

              if (file.exists()) {
                processClassFilesAndJarsRecursively(file, new Processor<File>() {
                  @Override
                  public boolean process(File file) {
                    processor.processExternalLibrary(file);
                    return true;
                  }
                });
              }
            }
          }
        }
      }
    }

    for (JpsDependencyElement item : JpsJavaExtensionService.getInstance().getDependencies(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false)) {
      if (item instanceof JpsModuleDependency) {
        final JpsModule depModule = ((JpsModuleDependency)item).getModule();
        if (depModule == null) continue;
        final JpsAndroidModuleExtension depExtension = getExtension(depModule);
        final boolean depLibrary = depExtension != null && depExtension.isLibrary();
        final File depClassDir = new ModuleBuildTarget(depModule, JavaModuleBuildTargetType.PRODUCTION).getOutputDir();

        if (depLibrary) {
          if (processor.isToProcess(AndroidDependencyType.ANDROID_LIBRARY_PACKAGE)) {
            final File intArtifactsDir = getDirectoryForIntermediateArtifacts(paths, depModule);
            final File packagedClassesJar = new File(intArtifactsDir, AndroidCommonUtils.CLASSES_JAR_FILE_NAME);
            processor.processAndroidLibraryPackage(packagedClassesJar);
          }
          if (processor.isToProcess(AndroidDependencyType.ANDROID_LIBRARY_OUTPUT_DIRECTORY)) {
            if (depClassDir != null) {
              processor.processAndroidLibraryOutputDirectory(depClassDir);
            }
          }
        }
        else if (processor.isToProcess(AndroidDependencyType.JAVA_MODULE_OUTPUT_DIR) &&
                 depExtension == null &&
                 depClassDir != null) {
          // do not support android-app->android-app compile dependencies
          processor.processJavaModuleOutputDirectory(depClassDir);
        }
        if (recursive) {
          processClasspath(paths, depModule, processor, visitedModules, !depLibrary || exportedLibrariesOnly, recursive);
        }
      }
    }
  }

  public static void processClassFilesAndJarsRecursively(@NotNull File root, @NotNull final Processor<File> processor) {
    FileUtil.processFilesRecursively(root, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile()) {
          final String ext = FileUtil.getExtension(file.getName());

          // NOTE: we should ignore apklib dependencies (IDEA-82976)
          if ("jar".equals(ext) || "class".equals(ext)) {
            if (!processor.process(file)) {
              return false;
            }
          }
        }
        return true;
      }
    });
  }

  @Nullable
  public static IAndroidTarget parseAndroidTarget(@NotNull JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk,
                                                  @Nullable CompileContext context,
                                                  String builderName) {
    JpsAndroidSdkProperties sdkProperties = sdk.getSdkProperties().getData();
    final String targetHashString = sdkProperties.getBuildTargetHashString();
    if (targetHashString == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   "Cannot parse SDK " + sdk.getParent().getName() + ": build target is not specified"));
      }
      return null;
    }

    final MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    final SdkManager manager = AndroidCommonUtils.createSdkManager(sdk.getHomePath(), log);

    if (manager == null) {
      final String message = log.getErrorMessage();
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   "Android SDK is parsed incorrectly." +
                                                   (message.length() > 0 ? " Parsing log:\n" + message : "")));
      }
      return null;
    }

    final IAndroidTarget target = manager.getTargetFromHashString(targetHashString);
    if (target == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   "Cannot parse SDK '" + sdk.getParent().getName() +
                                                   "': unknown target " + targetHashString));
      }
      return null;
    }
    return target;
  }

  @Nullable
  public static BuildMessage.Kind toBuildMessageKind(@NotNull AndroidCompilerMessageKind kind) {
    switch (kind) {
      case ERROR:
        return BuildMessage.Kind.ERROR;
      case INFORMATION:
        return BuildMessage.Kind.INFO;
      case WARNING:
        return BuildMessage.Kind.WARNING;
      default:
        LOG.error("unknown AndroidCompilerMessageKind object " + kind);
        return null;
    }
  }

  public static void reportExceptionError(@NotNull CompileContext context,
                                          @Nullable String filePath,
                                          @NotNull Exception exception,
                                          @NotNull String builderName) {
    final String message = exception.getMessage();

    if (message != null) {
      context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR, message, filePath));
      LOG.debug(exception);
    }
    else {
      context.processMessage(new CompilerMessage(builderName, exception));
    }
  }

  public static boolean containsAndroidFacet(@NotNull ModuleChunk chunk) {
    for (JpsModule module : chunk.getModules()) {
      if (getExtension(module) != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsAndroidFacet(@NotNull JpsProject project) {
    for (JpsModule module : project.getModules()) {
      if (getExtension(module) != null) {
        return true;
      }
    }
    return false;
  }

  public static ModuleLevelBuilder.ExitCode handleException(@NotNull CompileContext context,
                                                            @NotNull Exception e,
                                                            @NotNull String builderName)
    throws ProjectBuildException {
    String message = e.getMessage();

    if (message == null) {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      //noinspection IOResourceOpenedButNotSafelyClosed
      e.printStackTrace(new PrintStream(out));
      message = "Internal error: \n" + out.toString();
    }
    context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR, message));
    throw new ProjectBuildException(message, e);
  }

  @Nullable
  public static File getManifestFileForCompilationPath(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    return extension.useCustomManifestForCompilation()
           ? extension.getManifestFileForCompilation()
           : extension.getManifestFile();
  }

  @Nullable
  public static AndroidPlatform getAndroidPlatform(@NotNull JpsModule module,
                                                   @Nullable CompileContext context,
                                                   String builderName) {
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> sdk = module.getSdk(JpsAndroidSdkType.INSTANCE);
    if (sdk == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.sdk.not.specified", module.getName())));
      }
      return null;
    }

    final IAndroidTarget target = parseAndroidTarget(sdk, context, builderName);
    if (target == null) {
      if (context != null) {
        context.processMessage(new CompilerMessage(builderName, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.sdk.invalid", module.getName())));
      }
      return null;
    }
    return new AndroidPlatform(sdk, target);
  }

  public static String[] collectResourceDirsForCompilation(@NotNull JpsAndroidModuleExtension extension,
                                                           boolean withCacheDirs,
                                                           @NotNull CompileContext context) throws IOException {
    final List<String> result = new ArrayList<String>();
    addCompilableResourceDirsForModule(extension, withCacheDirs, context, result);

    for (JpsAndroidModuleExtension depExtension : getAllAndroidDependencies(extension.getModule(), true)) {
      addCompilableResourceDirsForModule(depExtension, withCacheDirs, context, result);
    }
    return ArrayUtil.toStringArray(result);
  }

  private static void addCompilableResourceDirsForModule(JpsAndroidModuleExtension extension,
                                                         boolean withCacheDirs,
                                                         CompileContext context,
                                                         List<String> result) throws IOException {
    if (withCacheDirs) {
      final File resourcesCacheDir = getResourcesCacheDir(context, extension.getModule());
      if (resourcesCacheDir.exists()) {
        result.add(resourcesCacheDir.getPath());
      }
    }

    final File resDir = getResourceDirForCompilationPath(extension);
    if (resDir != null) {
      result.add(resDir.getPath());
    }

    final File generatedResourcesStorage = getGeneratedResourcesStorage(extension.getModule(), context.getProjectDescriptor().dataManager);
    if (generatedResourcesStorage.exists()) {
      result.add(generatedResourcesStorage.getPath());
    }
  }

  @Nullable
  public static File getResourceDirForCompilationPath(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    return extension.useCustomResFolderForCompilation()
           ? extension.getResourceDirForCompilation()
           : extension.getResourceDir();
  }

  @NotNull
  static List<JpsAndroidModuleExtension> getAllAndroidDependencies(@NotNull JpsModule module, boolean librariesOnly) {
    final List<JpsAndroidModuleExtension> result = new ArrayList<JpsAndroidModuleExtension>();
    collectDependentAndroidLibraries(module, result, new HashSet<String>(), librariesOnly);
    return result;
  }

  private static void collectDependentAndroidLibraries(@NotNull JpsModule module,
                                                       @NotNull List<JpsAndroidModuleExtension> result,
                                                       @NotNull Set<String> visitedSet,
                                                       boolean librariesOnly) {
    final List<JpsDependencyElement> dependencies =
      new ArrayList<JpsDependencyElement>(JpsJavaExtensionService.getInstance().getDependencies(
        module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false));

    for (int i = dependencies.size() - 1; i >= 0; i--) {
      final JpsDependencyElement item = dependencies.get(i);

      if (item instanceof JpsModuleDependency) {
        final JpsModule depModule = ((JpsModuleDependency)item).getModule();
        if (depModule != null) {
          final JpsAndroidModuleExtension depExtension = getExtension(depModule);
          if (depExtension != null && (!librariesOnly || depExtension.isLibrary()) && visitedSet.add(depModule.getName())) {
            collectDependentAndroidLibraries(depModule, result, visitedSet, librariesOnly);
            result.add(0, depExtension);
          }
        }
      }
    }
  }

  public static boolean isLightBuild(@NotNull CompileContext context) {
    final String typeId = getRunConfigurationTypeId(context);
    return typeId != null && AndroidCommonUtils.isTestConfiguration(typeId);
  }

  @Nullable
  public static String getRunConfigurationTypeId(@NotNull CompileContext context) {
    return context.getBuilderParameter("RUN_CONFIGURATION_TYPE_ID");
  }

  public static boolean isReleaseBuild(@NotNull CompileContext context) {
    if (Boolean.parseBoolean(context.getBuilderParameter(AndroidCommonUtils.RELEASE_BUILD_OPTION))) {
      return true;
    }

    for (JpsArtifact artifact : getAndroidArtifactsToBuild(context)) {
      final JpsElement props = artifact.getProperties();

      if (props instanceof JpsAndroidApplicationArtifactProperties &&
          ((JpsAndroidApplicationArtifactProperties)props).getSigningMode() != AndroidArtifactSigningMode.DEBUG) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static List<JpsArtifact> getAndroidArtifactsToBuild(@NotNull CompileContext context) {
    final List<JpsArtifact> artifacts = JpsArtifactService.getInstance().getArtifacts(context.getProjectDescriptor().getProject());
    final List<JpsArtifact> result = new ArrayList<JpsArtifact>();

    for (JpsArtifact artifact : artifacts) {
      if (artifact.getArtifactType() instanceof AndroidApplicationArtifactType &&
          context.getScope().isAffected(new ArtifactBuildTarget(artifact))) {
        result.add(artifact);
      }
    }
    return result;
  }

  @NotNull
  public static File getResourcesCacheDir(@NotNull CompileContext context, @NotNull JpsModule module) {
    final File androidStorage = new File(context.getProjectDescriptor().dataManager.getDataPaths().getDataStorageRoot(), ANDROID_STORAGE_DIR);
    return new File(new File(androidStorage, RESOURCE_CACHE_STORAGE), module.getName());
  }

  private static void fillSourceRoots(@NotNull JpsModule module, @NotNull Set<JpsModule> visited, @NotNull Set<File> result)
    throws IOException {
    visited.add(module);
    final JpsAndroidModuleExtension extension = getExtension(module);
    File resDir = null;
    File resDirForCompilation = null;

    if (extension != null) {
      resDir = extension.getResourceDir();
      resDirForCompilation = extension.getResourceDirForCompilation();
    }

    for (JpsModuleSourceRoot root : module.getSourceRoots()) {
      final File rootDir = JpsPathUtil.urlToFile(root.getUrl());

      if ((JavaSourceRootType.SOURCE.equals(root.getRootType())
           || JavaSourceRootType.TEST_SOURCE.equals(root.getRootType()) && extension != null && extension.isPackTestCode())
          && !FileUtil.filesEqual(rootDir, resDir) && !rootDir.equals(resDirForCompilation)) {
        result.add(rootDir);
      }
    }


    for (JpsDependencyElement classpathItem : JpsJavaExtensionService.getInstance().getDependencies(module, JpsJavaClasspathKind.PRODUCTION_RUNTIME, false)) {
      if (classpathItem instanceof JpsModuleDependency) {
        final JpsModule depModule = ((JpsModuleDependency)classpathItem).getModule();

        if (depModule != null && !visited.contains(depModule)) {
          fillSourceRoots(depModule, visited, result);
        }
      }
    }
  }

  @NotNull
  public static File[] getSourceRootsForModuleAndDependencies(@NotNull JpsModule module) throws IOException {
    Set<File> result = new HashSet<File>();
    fillSourceRoots(module, new HashSet<JpsModule>(), result);
    return result.toArray(new File[result.size()]);
  }

  @Nullable
  public static String getApkPath(@NotNull JpsAndroidModuleExtension extension, @NotNull File outputDirForPackagedArtifacts) {
    final String apkRelativePath = extension.getApkRelativePath();
    final JpsModule module = extension.getModule();

    if (apkRelativePath == null || apkRelativePath.length() == 0) {
      return new File(outputDirForPackagedArtifacts, getApkName(module)).getPath();
    }

    File moduleBaseDirectory = JpsModelSerializationDataService.getBaseDirectory(module);
    return moduleBaseDirectory != null ? FileUtil.toSystemDependentName(moduleBaseDirectory.getAbsolutePath() + apkRelativePath) : null;
  }

  @NotNull
  public static String getApkName(@NotNull JpsModule module) {
    return module.getName() + ".apk";
  }

  @NotNull
  public static File getGeneratedSourcesStorage(@NotNull JpsModule module, BuildDataManager dataManager) {
    return getGeneratedSourcesStorage(module, dataManager.getDataPaths());
  }

  @NotNull
  public static File getGeneratedSourcesStorage(@NotNull JpsModule module, final BuildDataPaths dataPaths) {
    final File androidStorageRoot = new File(dataPaths.getDataStorageRoot(), ANDROID_STORAGE_DIR);
    final File generatedSourcesRoot = new File(androidStorageRoot, GENERATED_SOURCES_FOLDER_NAME);
    return new File(generatedSourcesRoot, module.getName());
  }

  @NotNull
  public static File getGeneratedResourcesStorage(@NotNull JpsModule module, BuildDataManager dataManager) {
    final File dataStorageRoot = dataManager.getDataPaths().getDataStorageRoot();
    final File androidStorageRoot = new File(dataStorageRoot, ANDROID_STORAGE_DIR);
    final File generatedSourcesRoot = new File(androidStorageRoot, GENERATED_RESOURCES_DIR_NAME);
    return new File(generatedSourcesRoot, module.getName());
  }

  @NotNull
  public static File getStorageFile(@NotNull File dataStorageRoot, @NotNull String storageName) {
    return new File(new File(new File(dataStorageRoot, ANDROID_STORAGE_DIR), storageName), storageName);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  @Nullable
  private static Properties readPropertyFile(@NotNull File file) {
    final Properties properties = new Properties();
    try {
      properties.load(new FileInputStream(file));
      return properties;
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return null;
  }

  @Nullable
  public static Pair<String, File> getProjectPropertyValue(@NotNull JpsAndroidModuleExtension extension, @NotNull String propertyKey) {
    final File root;
    try {
      root = getMainContentRoot(extension);
    }
    catch (IOException e) {
      return null;
    }
    if (root == null) {
      return null;
    }
    final File projectProperties = new File(root, SdkConstants.FN_PROJECT_PROPERTIES);

    if (projectProperties.exists()) {
      final Properties properties = readPropertyFile(projectProperties);

      if (properties != null) {
        final String value = properties.getProperty(propertyKey);

        if (value != null) {
          return Pair.create(value, projectProperties);
        }
      }
    }
    return null;
  }


  @NotNull
  public static Set<String> getGenDirs(@NotNull JpsAndroidModuleExtension extension) throws IOException {
    final Set<String> result = new HashSet<String>();
    File dir = extension.getAaptGenDir();

    if (dir != null) {
      result.add(dir.getPath());
    }
    dir = extension.getAidlGenDir();

    if (dir != null) {
      result.add(dir.getPath());
    }
    return result;
  }

  @Nullable
  public static JpsAndroidModuleExtension getPackagedFacet(@NotNull JpsArtifact artifact) {
    final List<JpsAndroidModuleExtension> facets = getAllPackagedFacets(artifact);
    return facets.size() == 1 ? facets.get(0) : null;
  }

  @NotNull
  public static List<JpsAndroidModuleExtension> getAllPackagedFacets(JpsArtifact artifact) {
    final List<JpsAndroidModuleExtension> extensions = new ArrayList<JpsAndroidModuleExtension>();

    JpsArtifactUtil.processPackagingElements(artifact.getRootElement(), new Processor<JpsPackagingElement>() {
      @Override
      public boolean process(JpsPackagingElement element) {
        if (element instanceof JpsAndroidFinalPackageElement) {
          final JpsModuleReference reference = ((JpsAndroidFinalPackageElement)element).getModuleReference();
          final JpsModule module = reference != null ? reference.resolve() : null;
          final JpsAndroidModuleExtension extension = module != null ? getExtension(module) : null;

          if (extension != null) {
            extensions.add(extension);
          }
        }
        return true;
      }
    });
    return extensions;
  }

  public static ProGuardOptions getProGuardConfigIfShouldRun(@NotNull CompileContext context, @NotNull JpsAndroidModuleExtension extension)
    throws IOException {
    if (extension.isRunProguard()) {
      return new ProGuardOptions(extension.getProguardConfigFile(), extension.isIncludeSystemProguardCfgFile());
    }

    final String cfgPathFromContext = context.getBuilderParameter(AndroidCommonUtils.PROGUARD_CFG_PATH_OPTION);
    if (cfgPathFromContext != null) {
      final String includeSystemProGuardFile = context.getBuilderParameter(AndroidCommonUtils.INCLUDE_SYSTEM_PROGUARD_FILE_OPTION);
      return new ProGuardOptions(new File(cfgPathFromContext), Boolean.parseBoolean(includeSystemProGuardFile));
    }

    for (JpsArtifact artifact : getAndroidArtifactsToBuild(context)) {
      final JpsAndroidModuleExtension facetFromArtifact = getPackagedFacet(artifact);
      final JpsModule moduleFromArtifact = facetFromArtifact != null ? facetFromArtifact.getModule() : null;

      if (moduleFromArtifact != null && moduleFromArtifact.equals(extension.getModule())) {
        final JpsElement props = artifact.getProperties();

        if (props instanceof JpsAndroidApplicationArtifactProperties) {
          final JpsAndroidApplicationArtifactProperties androidProps = (JpsAndroidApplicationArtifactProperties)props;

          if (androidProps.isRunProGuard()) {
            final String cfgFileUrl = androidProps.getProGuardCfgFileUrl();
            final String cfgPath = cfgFileUrl != null ? JpsPathUtil.urlToPath(cfgFileUrl) : null;
            return new ProGuardOptions(new File(cfgPath), androidProps.isIncludeSystemProGuardCfgFile());
          }
        }
      }
    }
    return null;
  }
}
