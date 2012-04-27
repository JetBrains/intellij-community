package org.jetbrains.jps.android;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.build.BuildConfigGenerator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.compiler.tools.AndroidIdl;
import org.jetbrains.android.compiler.tools.AndroidRenderscript;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Module;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.fs.RootDescriptor;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;

import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSourceGeneratingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.android.AndroidSourceGeneratingBuilder");

  @NonNls private static final String BUILDER_NAME = "android-source-generator";
  @NonNls private static final String AIDL_EXTENSION = "aidl";
  @NonNls private static final String RENDERSCRIPT_EXTENSION = "rs";
  @NonNls private static final String MANIFEST_TAG = "manifest";
  @NonNls private static final String PACKAGE_MANIFEST_ATTRIBUTE = "package";
  @NonNls private static final String PERMISSION_TAG = "permission";
  @NonNls private static final String PERMISSION_GROUP_TAG = "permission-group";
  @NonNls private static final String NAME_ATTRIBUTE = "name";

  public AndroidSourceGeneratingBuilder() {
    super(BuilderCategory.SOURCE_GENERATOR);
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public ModuleLevelBuilder.ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    if (context.isCompilingTests() || !AndroidJpsUtil.containsAndroidFacet(chunk)) {
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
    final Map<File, Module> idlFilesToCompile = new HashMap<File, Module>();
    final Map<File, Module> rsFilesToCompile = new HashMap<File, Module>();

    context.processFilesToRecompile(chunk, new FileProcessor() {
      @Override
      public boolean apply(Module module, File file, String sourceRoot) throws IOException {
        final AndroidFacet facet = AndroidJpsUtil.getFacet(module);

        if (facet == null) {
          return true;
        }
        final String ext = FileUtil.getExtension(file.getName());

        if (AIDL_EXTENSION.equals(ext)) {
          idlFilesToCompile.put(file, facet.getModule());
        }
        else if (RENDERSCRIPT_EXTENSION.equals(ext)) {
          rsFilesToCompile.put(file, facet.getModule());
        }

        return true;
      }
    });

    final Map<Module, MyModuleData> moduleDataMap = computeModuleDatas(chunk.getModules(), context);

    if (moduleDataMap == null || moduleDataMap.size() == 0) {
      return ExitCode.ABORT;
    }
    boolean success = true;

    if (context.isProjectRebuild()) {
      for (Module module : moduleDataMap.keySet()) {
        final File generatedSourcesStorage = AndroidJpsUtil.getGeneratedSourcesStorage(module);
        if (generatedSourcesStorage.exists() &&
            !deleteAndMarkRecursively(generatedSourcesStorage, context)) {
          success = false;
        }

        final File generatedResourcesStorage = AndroidJpsUtil.getGeneratedResourcesStorage(module);
        if (generatedResourcesStorage.exists() &&
            !deleteAndMarkRecursively(generatedResourcesStorage, context)) {
          success = false;
        }
      }
    }

    if (!success) {
      return ExitCode.ABORT;
    }

    if (!runAidlCompiler(context, idlFilesToCompile, moduleDataMap)) {
      success = false;
    }

    if (!runRenderscriptCompiler(context, rsFilesToCompile, moduleDataMap)) {
      success = false;
    }

    final File dataStorageRoot = context.getDataManager().getDataStorageRoot();

    final AndroidAptStateStorage aptStorage = new AndroidAptStateStorage(dataStorageRoot);
    try {
      if (!runAaptCompiler(context, moduleDataMap, aptStorage)) {
        success = false;
      }
    }
    finally {
      aptStorage.close();
    }

    final AndroidBuildConfigStateStorage buildConfigStorage = new AndroidBuildConfigStateStorage(dataStorageRoot);
    try {
      if (!runBuildConfigGeneration(context, moduleDataMap, buildConfigStorage)) {
        success = false;
      }
    }
    finally {
      buildConfigStorage.close();
    }

    return success ? ExitCode.OK : ExitCode.ABORT;
  }

  private static boolean runBuildConfigGeneration(@NotNull CompileContext context,
                                                  @NotNull Map<Module, MyModuleData> moduleDataMap,
                                                  @NotNull AndroidBuildConfigStateStorage storage) {
    boolean success = true;

    for (Map.Entry<Module, MyModuleData> entry : moduleDataMap.entrySet()) {
      final Module module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final AndroidFacet facet = AndroidJpsUtil.getFacet(module);

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module);
      final File outputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.BUILD_CONFIG_GENERATED_SOURCE_ROOT_NAME);

      try {
        if (facet == null || isLibraryWithBadCircularDependency(facet)) {
          if (!clearDirectoryIfNotEmpty(outputDirectory, context)) {
            success = false;
          }
          continue;
        }
        final String packageName = moduleData.getPackage();
        final boolean debug = !AndroidJpsUtil.isReleaseBuild(context);
        final Set<String> libPackages = new HashSet<String>(getDepLibPackages(module).values());
        libPackages.remove(packageName);

        final AndroidBuildConfigState newState = new AndroidBuildConfigState(packageName, libPackages, debug);

        if (context.isMake()) {
          final AndroidBuildConfigState oldState = storage.getState(module.getName());
          if (newState.equalsTo(oldState)) {
            continue;
          }
        }

        // clear directory, because it may contain obsolete files (ex. if package name was changed)
        if (!clearDirectory(outputDirectory, context)) {
          success = false;
          continue;
        }

        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.build.config", module.getName())));

        if (doBuildConfigGeneration(packageName, libPackages, debug, outputDirectory, context)) {
          storage.update(module.getName(), newState);
          markDirtyRecursively(outputDirectory, context);
        }
        else {
          storage.update(module.getName(), null);
          success = false;
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
        success = false;
      }
    }
    return success;
  }

  private static boolean doBuildConfigGeneration(@NotNull String packageName,
                                                 @NotNull Collection<String> libPackages,
                                                 boolean debug,
                                                 @NotNull File outputDirectory,
                                                 @NotNull CompileContext context) {
    if (!doBuildConfigGeneration(packageName, debug, outputDirectory.getPath(), context)) {
      return false;
    }

    for (String libPackage : libPackages) {
      if (!doBuildConfigGeneration(libPackage, debug, outputDirectory.getPath(), context)) {
        return false;
      }
    }
    return true;
  }

  private static boolean doBuildConfigGeneration(@NotNull String packageName,
                                                 boolean debug,
                                                 @NotNull String outputDirOsPath,
                                                 @NotNull CompileContext context) {
    final BuildConfigGenerator generator = new BuildConfigGenerator(outputDirOsPath, packageName, debug);
    try {
      generator.generate();
      return true;
    }
    catch (IOException e) {
      AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
      return false;
    }
  }

  private static boolean runAidlCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<File, Module> files,
                                         @NotNull Map<Module, MyModuleData> moduleDataMap) {
    if (files.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aidl")));
    }

    boolean success = true;

    for (Map.Entry<File, Module> entry : files.entrySet()) {
      final File file = entry.getKey();
      final Module module = entry.getValue();
      final String filePath = file.getPath();

      final MyModuleData moduleData = moduleDataMap.get(module);

      if (!LOG.assertTrue(moduleData != null)) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Internal error"));
        success = false;
        continue;
      }
      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module);
      final File aidlOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.AIDL_GENERATED_SOURCE_ROOT_NAME);

      if (!aidlOutputDirectory.exists() && !aidlOutputDirectory.mkdirs()) {
        context.processMessage(
          new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot create directory " + aidlOutputDirectory.getPath()));
        success = false;
        continue;
      }

      final IAndroidTarget target = moduleData.getAndroidTarget();

      try {
        final File[] sourceRoots = AndroidJpsUtil.getSourceRootsForModuleAndDependencies(module);
        final String[] sourceRootPaths = AndroidJpsUtil.toPaths(sourceRoots);
        final String packageName = computePackageForFile(context, file);

        if (packageName == null) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                     AndroidJpsBundle.message("android.jps.errors.cannot.compute.package", filePath)));
          success = false;
          continue;
        }

        final File outputFile = new File(aidlOutputDirectory, packageName.replace('.', File.separatorChar) +
                                                              File.separator + FileUtil.getNameWithoutExtension(file) + ".java");
        final String outputFilePath = outputFile.getPath();
        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidIdl.execute(target, filePath, outputFilePath, sourceRootPaths);

        addMessages(context, messages, filePath, BUILDER_NAME);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else {
          final SourceToOutputMapping sourceToOutputMap = context.getDataManager().getSourceToOutputMap(module.getName(), false);
          sourceToOutputMap.update(filePath, outputFilePath);
          context.markDirty(outputFile);
        }
      }
      catch (final IOException e) {
        AndroidJpsUtil.reportExceptionError(context, filePath, e, BUILDER_NAME);
        success = false;
      }
    }
    return success;
  }

  private static boolean runRenderscriptCompiler(@NotNull final CompileContext context,
                                                 @NotNull Map<File, Module> files,
                                                 @NotNull Map<Module, MyModuleData> moduleDataMap) {
    if (files.size() > 0) {
      context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.renderscript")));
    }

    boolean success = true;

    for (Map.Entry<File, Module> entry : files.entrySet()) {
      final File file = entry.getKey();
      final Module module = entry.getValue();

      final MyModuleData moduleData = moduleDataMap.get(module);
      if (!LOG.assertTrue(moduleData != null)) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Internal error"));
        success = false;
        continue;
      }

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module);
      final File rsOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.RENDERSCRIPT_GENERATED_SOURCE_ROOT_NAME);
      if (!rsOutputDirectory.exists() && !rsOutputDirectory.mkdirs()) {
        context.processMessage(
          new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot create directory " + rsOutputDirectory.getPath()));
        success = false;
        continue;
      }

      final File generatedResourcesDir = AndroidJpsUtil.getGeneratedResourcesStorage(module);
      final File rawDir = new File(generatedResourcesDir, "raw");

      if (!rawDir.exists() && !rawDir.mkdirs()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot create directory " + rawDir.getPath()));
        success = false;
        continue;
      }

      final IAndroidTarget target = moduleData.getAndroidTarget();
      final String sdkLocation = moduleData.getSdkLocation();
      final String filePath = file.getPath();

      File tmpOutputDirectory = null;

      try {
        tmpOutputDirectory = FileUtil.createTempDirectory("generated-rs-temp", null);
        final String depFolderPath = getDependencyFolder(context, file, tmpOutputDirectory);

        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidRenderscript.execute(sdkLocation, target, filePath, tmpOutputDirectory.getPath(), depFolderPath, rawDir.getPath());

        addMessages(context, messages, filePath, BUILDER_NAME);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else {
          final List<File> newFiles = new ArrayList<File>();
          AndroidCommonUtils.moveAllFiles(tmpOutputDirectory, rsOutputDirectory, newFiles);

          final File bcFile = new File(rawDir, FileUtil.getNameWithoutExtension(file) + ".bc");
          if (bcFile.exists()) {
            newFiles.add(bcFile);
          }
          final List<String> newFilePaths = Arrays.asList(AndroidJpsUtil.toPaths(newFiles.toArray(new File[newFiles.size()])));

          final SourceToOutputMapping sourceToOutputMap = context.getDataManager().getSourceToOutputMap(module.getName(), false);
          sourceToOutputMap.update(filePath, newFilePaths);

          for (File newFile : newFiles) {
            context.markDirty(newFile);
          }
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, filePath, e, BUILDER_NAME);
        success = false;
      }
      finally {
        if (tmpOutputDirectory != null) {
          FileUtil.delete(tmpOutputDirectory);
        }
      }
    }
    return success;
  }

  private static boolean runAaptCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<Module, MyModuleData> moduleDataMap,
                                         @NotNull AndroidAptStateStorage storage) {
    boolean success = true;

    for (Map.Entry<Module, MyModuleData> entry : moduleDataMap.entrySet()) {
      final Module module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final AndroidFacet facet = moduleData.getFacet();

      final File generatedSourcesDir = AndroidJpsUtil.getGeneratedSourcesStorage(module);
      final File aptOutputDirectory = new File(generatedSourcesDir, AndroidJpsUtil.AAPT_GENERATED_SOURCE_ROOT_NAME);
      final IAndroidTarget target = moduleData.getAndroidTarget();

      try {
        if (!needToRunAaptCompilation(facet)) {
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context)) {
            success = false;
          }
          continue;
        }

        final String[] resPaths = AndroidJpsUtil.collectResourceDirsForCompilation(facet, false, context);
        if (resPaths.length == 0) {
          // there is no resources in the module
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context)) {
            success = false;
          }
          continue;
        }
        final String packageName = moduleData.getPackage();
        final File manifestFile = moduleData.getManifestFileForCompiler();

        if (isLibraryWithBadCircularDependency(facet)) {
          if (!clearDirectoryIfNotEmpty(aptOutputDirectory, context)) {
            success = false;
          }
          continue;
        }
        final Map<Module, String> packageMap = getDepLibPackages(module);
        packageMap.put(module, packageName);

        final Module circularDepLibWithSamePackage = findCircularDependencyOnLibraryWithSamePackage(facet, packageMap);
        if (circularDepLibWithSamePackage != null && !facet.isLibrary()) {
          final String message = "Generated fields in " +
                                 packageName +
                                 ".R class in module '" +
                                 module.getName() +
                                 "' won't be final, because of circular dependency on module '" +
                                 circularDepLibWithSamePackage.getName() +
                                 "'";
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.WARNING, message));
        }
        final boolean generateNonFinalFields = facet.isLibrary() || circularDepLibWithSamePackage != null;

        final Map<String, ResourceFileData> resources = collectResources(resPaths);
        final List<ResourceEntry> manifestElements = collectManifestElements(manifestFile);

        final Set<String> depLibPackagesSet = new HashSet<String>(packageMap.values());
        depLibPackagesSet.remove(packageName);

        final AndroidAptValidityState newState = new AndroidAptValidityState(resources, manifestElements, depLibPackagesSet, packageName);

        if (context.isMake()) {
          final AndroidAptValidityState oldState = storage.getState(module.getName());
          if (newState.equalsTo(oldState)) {
            continue;
          }
        }

        // clear directory, because it may contain obsolete files (ex. if package name was changed)
        if (!clearDirectory(aptOutputDirectory, context)) {
          success = false;
          continue;
        }

        context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aapt", module.getName())));

        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidApt.compile(target, -1, manifestFile.getPath(), packageName, aptOutputDirectory.getPath(), resPaths,
                             ArrayUtil.toStringArray(depLibPackagesSet), generateNonFinalFields);

        AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
          storage.update(module.getName(), null);
        }
        else {
          storage.update(module.getName(), newState);
          markDirtyRecursively(aptOutputDirectory, context);
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
        success = false;
      }
    }
    return success;
  }

  private static boolean clearDirectory(File dir, CompileContext context) throws IOException {
    if (!deleteAndMarkRecursively(dir, context)) {
      return false;
    }

    if (!dir.mkdirs()) {
      context.processMessage(
        new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot create directory " + dir.getPath()));
      return false;
    }
    return true;
  }

  private static boolean clearDirectoryIfNotEmpty(@NotNull File dir, @NotNull CompileContext context) throws IOException {
    if (dir.isDirectory()) {
      final String[] list = dir.list();
      if (list != null && list.length > 0) {
        return clearDirectory(dir, context);
      }
    }
    return true;
  }

  private static boolean needToRunAaptCompilation(AndroidFacet facet) {
    return !facet.isRunProcessResourcesMavenTask() ||
           !AndroidJpsUtil.isMavenizedModule(facet.getModule());
  }

  private static boolean deleteAndMarkRecursively(@NotNull File dir, @NotNull CompileContext context) throws IOException {
    if (dir.exists()) {
      final List<File> filesToDelete = collectJavaFilesRecursively(dir);
      if (!FileUtil.delete(dir)) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot delete " + dir.getPath()));
        return false;
      }

      for (File file : filesToDelete) {
        context.markDeleted(file);
      }
    }
    return true;
  }

  private static boolean markDirtyRecursively(@NotNull File dir, @NotNull final CompileContext context) {
    final Ref<Boolean> success = Ref.create(true);

    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile() && "java".equals(FileUtil.getExtension(file.getName()))) {
          try {
            context.markDirty(file);
          }
          catch (IOException e) {
            AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
            success.set(false);
            return false;
          }
        }
        return true;
      }
    });
    return success.get();
  }

  @NotNull
  private static List<File> collectJavaFilesRecursively(@NotNull File dir) {
    final List<File> result = new ArrayList<File>();

    FileUtil.processFilesRecursively(dir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        if (file.isFile() && "java".equals(FileUtil.getExtension(file.getName()))) {
          result.add(file);
        }
        return true;
      }
    });
    return result;
  }

  @NotNull
  private static Map<Module, String> getDepLibPackages(@NotNull Module module) throws IOException {
    final Map<Module, String> result = new HashMap<Module, String>();

    for (AndroidFacet depFacet : AndroidJpsUtil.getAllAndroidDependencies(module, true)) {
      final File depManifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(depFacet);

      if (depManifestFile != null) {
        final String packageName = parsePackageNameFromManifestFile(depManifestFile);

        if (packageName != null) {
          result.put(depFacet.getModule(), packageName);
        }
      }
    }
    return result;
  }

  @Nullable
  private static String parsePackageNameFromManifestFile(@NotNull File manifestFile) throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(manifestFile));
    try {
      final Ref<String> packageName = new Ref<String>(null);
      FormsParsing.parse(inputStream, new FormsParsing.IXMLBuilderAdapter() {
        boolean processingManifestTagAttrs = false;

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          if (MANIFEST_TAG.equals(name)) {
            processingManifestTagAttrs = true;
          }
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
          throws Exception {
          if (value != null && PACKAGE_MANIFEST_ATTRIBUTE.equals(key)) {
            packageName.set(value.trim());
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          stop();
        }
      });

      return packageName.get();
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  private static Map<String, ResourceFileData> collectResources(@NotNull String[] resPaths) throws IOException {
    final Map<String, ResourceFileData> result = new HashMap<String, ResourceFileData>();

    for (String resDirPath : resPaths) {
      final File[] resSubdirs = new File(resDirPath).listFiles();

      if (resSubdirs != null) {
        for (File resSubdir : resSubdirs) {
          final String resType = AndroidCommonUtils.getResourceTypeByDirName(resSubdir.getName());

          if (resType != null) {
            final File[] resFiles = resSubdir.listFiles();

            if (resFiles != null) {
              for (File resFile : resFiles) {
                if (ResourceFolderType.VALUES.getName().equals(resType) && "xml".equals(FileUtil.getExtension(resFile.getName()))) {
                  final ArrayList<ResourceEntry> entries = new ArrayList<ResourceEntry>();
                  collectValueResources(resFile, entries);
                  result.put(FileUtil.toSystemIndependentName(resFile.getPath()), new ResourceFileData(entries, 0));
                }
                else {
                  final ResourceType resTypeObj = ResourceType.getEnum(resType);
                  final boolean idProvidingType =
                    resTypeObj != null && ArrayUtil.find(AndroidCommonUtils.ID_PROVIDING_RESOURCE_TYPES, resTypeObj) >= 0;
                  final ResourceFileData data =
                    new ResourceFileData(Collections.<ResourceEntry>emptyList(), idProvidingType ? resFile.lastModified() : 0);
                  result.put(FileUtil.toSystemIndependentName(resFile.getPath()), data);
                }
              }
            }
          }
        }
      }
    }
    return result;
  }

  private static void collectValueResources(@NotNull File valueResXmlFile, @NotNull final List<ResourceEntry> result)
    throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(valueResXmlFile));
    try {

      FormsParsing.parse(inputStream, new ValueResourcesFileParser() {
        @Override
        protected void stop() {
          throw new FormsParsing.ParserStoppedException();
        }

        @Override
        protected void process(@NotNull ResourceEntry resourceEntry) {
          result.add(resourceEntry);
        }
      });
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  private static List<ResourceEntry> collectManifestElements(@NotNull File manifestFile) throws IOException {
    final InputStream inputStream = new BufferedInputStream(new FileInputStream(manifestFile));
    try {
      final List<ResourceEntry> result = new ArrayList<ResourceEntry>();

      FormsParsing.parse(inputStream, new FormsParsing.IXMLBuilderAdapter() {
        String myLastName;

        @Override
        public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
          throws Exception {
          myLastName = null;
        }

        @Override
        public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type)
          throws Exception {
          if (value != null && NAME_ATTRIBUTE.equals(key)) {
            myLastName = value;
          }
        }

        @Override
        public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) throws Exception {
          if (myLastName != null && PERMISSION_TAG.equals(name) || PERMISSION_GROUP_TAG.equals(name)) {
            assert myLastName != null;
            result.add(new ResourceEntry(name, myLastName));
          }
        }
      });

      return result;
    }
    finally {
      inputStream.close();
    }
  }

  @Nullable
  private static String getDependencyFolder(@NotNull CompileContext context, @NotNull File sourceFile, @NotNull File genFolder) {
    final RootDescriptor descriptor = context.getRootsIndex().getModuleAndRoot(sourceFile);
    if (descriptor == null) {
      return null;
    }
    final File sourceRoot = descriptor.root;

    final File parent = FileUtil.getParentFile(sourceFile);
    if (parent == null) {
      return null;
    }

    if (parent.equals(sourceRoot)) {
      return genFolder.getPath();
    }
    final String relativePath = FileUtil.getRelativePath(sourceRoot, parent);
    assert relativePath != null;
    return genFolder.getPath() + '/' + relativePath;
  }

  @Nullable
  private static Map<Module, MyModuleData> computeModuleDatas(@NotNull Collection<Module> modules, @NotNull CompileContext context)
    throws IOException {
    final Map<Module, MyModuleData> moduleDataMap = new HashMap<Module, MyModuleData>();

    boolean success = true;

    for (Module module : modules) {
      final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
      if (facet == null) {
        continue;
      }

      final AndroidPlatform platform = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (platform == null) {
        success = false;
        continue;
      }
      final AndroidSdk androidSdk = platform.getSdk();
      final IAndroidTarget target = platform.getTarget();

      final File manifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(facet);
      if (manifestFile == null || !manifestFile.exists()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   AndroidJpsBundle.message("android.jps.errors.manifest.not.found", module.getName())));
        success = false;
        continue;
      }

      final String packageName = parsePackageNameFromManifestFile(manifestFile);
      if (packageName == null || packageName.length() == 0) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.errors.package.not.specified", module.getName())));
        success = false;
        continue;
      }

      moduleDataMap.put(module, new MyModuleData(androidSdk.getSdkPath(), target, facet, manifestFile, packageName));
    }

    return success ? moduleDataMap : null;
  }

  @Nullable
  private static String computePackageForFile(@NotNull CompileContext context, @NotNull File file) throws IOException {
    final RootDescriptor descriptor = context.getRootsIndex().getModuleAndRoot(file);
    if (descriptor == null) {
      return null;
    }

    final String relPath = FileUtil.getRelativePath(descriptor.root, FileUtil.getParentFile(file));
    if (relPath == null) {
      return null;
    }

    return FileUtil.toSystemIndependentName(relPath).replace('/', '.');
  }

  @Override
  public String getDescription() {
    return "Android Source Generating Builder";
  }

  // support for lib<->lib and app<->lib circular dependencies
  // see IDEA-79737 for details
  private static boolean isLibraryWithBadCircularDependency(@NotNull AndroidFacet facet)
    throws IOException {
    if (!facet.isLibrary()) {
      return false;
    }
    final List<AndroidFacet> dependencies = AndroidJpsUtil.getAllAndroidDependencies(facet.getModule(), false);

    for (AndroidFacet depFacet : dependencies) {
      final List<AndroidFacet> depDependencies = AndroidJpsUtil.getAllAndroidDependencies(depFacet.getModule(), true);

      if (depDependencies.contains(facet) &&
          dependencies.contains(depFacet) &&
          (depFacet.getModule().getName().compareTo(facet.getModule().getName()) < 0 || !depFacet.isLibrary())) {
        return true;
      }
    }
    return false;
  }

  private static void addMessages(@NotNull CompileContext context,
                                  @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                  @NotNull String sourcePath,
                                  @NotNull String builderName) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      final AndroidCompilerMessageKind kind = entry.getKey();
      final BuildMessage.Kind buildMessageKind = AndroidJpsUtil.toBuildMessageKind(kind);

      if (buildMessageKind == null) {
        continue;
      }

      for (String message : entry.getValue()) {
        context.processMessage(new CompilerMessage(builderName, buildMessageKind, message, sourcePath));
      }
    }
  }

  @Nullable
  public static Module findCircularDependencyOnLibraryWithSamePackage(@NotNull AndroidFacet facet,
                                                                      @NotNull Map<Module, String> packageMap) {
    final String aPackage = packageMap.get(facet.getModule());
    if (aPackage == null || aPackage.length() == 0) {
      return null;
    }

    for (AndroidFacet depFacet : AndroidJpsUtil.getAllAndroidDependencies(facet.getModule(), true)) {
      if (aPackage.equals(packageMap.get(depFacet.getModule()))) {
        final List<AndroidFacet> depDependencies = AndroidJpsUtil.getAllAndroidDependencies(depFacet.getModule(), false);

        if (depDependencies.contains(facet)) {
          // circular dependency on library with the same package
          return depFacet.getModule();
        }
      }
    }
    return null;
  }

  private static class MyModuleData {
    private final String mySdkLocation;
    private final IAndroidTarget myAndroidTarget;
    private final AndroidFacet myFacet;
    private final File myManifestFileForCompiler;
    private final String myPackage;

    private MyModuleData(@NotNull String sdkLocation,
                         @NotNull IAndroidTarget androidTarget,
                         @NotNull AndroidFacet facet,
                         @NotNull File manifestFileForCompiler,
                         @NotNull String aPackage) {
      mySdkLocation = sdkLocation;
      myAndroidTarget = androidTarget;
      myFacet = facet;
      myManifestFileForCompiler = manifestFileForCompiler;
      myPackage = aPackage;
    }

    @NotNull
    public IAndroidTarget getAndroidTarget() {
      return myAndroidTarget;
    }

    @NotNull
    public String getSdkLocation() {
      return mySdkLocation;
    }

    @NotNull
    public AndroidFacet getFacet() {
      return myFacet;
    }

    @NotNull
    public File getManifestFileForCompiler() {
      return myManifestFileForCompiler;
    }

    @NotNull
    public String getPackage() {
      return myPackage;
    }
  }
}
