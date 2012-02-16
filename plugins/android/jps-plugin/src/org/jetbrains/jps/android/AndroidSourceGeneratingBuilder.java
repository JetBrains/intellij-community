package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.compiler.tools.AndroidIdl;
import org.jetbrains.android.compiler.tools.AndroidRenderscript;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.FormsParsing;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;

import java.io.*;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */

// todo: change output folders

public class AndroidSourceGeneratingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.android.AndroidSourceGeneratingBuilder");

  @NonNls private static final String BUILDER_NAME = "android-source-generator";
  @NonNls private static final String AIDL_EXTENSION = "aidl";
  @NonNls private static final String RENDERSCRIPT_EXTENSION = "rs";
  @NonNls private static final String MANIFEST_TAG = "manifest";
  @NonNls private static final String PACKAGE_MANIFEST_ATTRIBUTE = "package";

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
      return ModuleLevelBuilder.ExitCode.OK;
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

    if (!runAidlCompiler(context, idlFilesToCompile, moduleDataMap)) {
      success = false;
    }

    if (!runRenderscriptCompiler(context, rsFilesToCompile, moduleDataMap)) {
      success = false;
    }

    if (!runAaptCompiler(context, moduleDataMap)) {
      success = false;
    }

    return success ? ExitCode.OK : ExitCode.ABORT;
  }

  private static boolean runAidlCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<File, Module> files,
                                         @NotNull Map<Module, MyModuleData> moduleDataMap) {
    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aidl")));

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
      final File outputDirectory = moduleData.getOutputDirectory();
      final File aidlOutputDirectory = new File(outputDirectory, "generated-aidl");
      final IAndroidTarget target = moduleData.getAndroidTarget();

      try {
        final File[] sourceRoots = getSourceRootsForModuleAndDependencies(module);
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
          final String moduleName = getCannonicalModuleName(module);
          final SourceToOutputMapping sourceToOutputMap = context.getDataManager().getSourceToOutputMap(moduleName, false);
          sourceToOutputMap.update(filePath, outputFilePath);

          JavaBuilder.addTempSourcePathRoot(context, aidlOutputDirectory);
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
    context.processMessage(new ProgressMessage("Processing Renderscript files..."));

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

      final File outputDirectory = moduleData.getOutputDirectory();
      final File rsOutputDirectory = new File(outputDirectory, "generated-rs");
      final File generatedResourcesDir = new File(outputDirectory, "generated-resources");

      final IAndroidTarget target = moduleData.getAndroidTarget();
      final String sdkLocation = moduleData.getSdkLocation();
      final String filePath = file.getPath();

      File tmpOutputDirectory = null;

      try {
        tmpOutputDirectory = FileUtil.createTempDirectory("generated-rs-temp", null);
        final String depFolderPath = getDependencyFolder(context, file, tmpOutputDirectory);
        final File rawDir = new File(generatedResourcesDir, "raw");

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

          final String moduleName = getCannonicalModuleName(module);
          final SourceToOutputMapping sourceToOutputMap = context.getDataManager().getSourceToOutputMap(moduleName, false);
          sourceToOutputMap.update(filePath, newFilePaths);

          JavaBuilder.addTempSourcePathRoot(context, rsOutputDirectory);
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

  // todo: save validity state
  private static boolean runAaptCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<Module, MyModuleData> moduleDataMap) {
    context.processMessage(new ProgressMessage(AndroidJpsBundle.message("android.jps.progress.aapt")));

    boolean success = true;

    for (Map.Entry<Module, MyModuleData> entry : moduleDataMap.entrySet()) {
      final Module module = entry.getKey();
      final MyModuleData moduleData = entry.getValue();
      final AndroidFacet facet = moduleData.getFacet();

      // todo: check if we need special strategy for maven apksources

      final IAndroidTarget target = moduleData.getAndroidTarget();

      try {
        final String[] resPaths = AndroidJpsUtil.collectResourceDirs(facet);
        if (resPaths.length == 0) {
          // there is no resources in the module
          continue;
        }

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

        final Set<String> depLibPackagesSet = getDepLibPackages(module);
        depLibPackagesSet.remove(packageName);

        final File outputDirectory = moduleData.getOutputDirectory();
        final File aptOutputDirectory = new File(outputDirectory, "generated-aapt");

        if (aptOutputDirectory.exists()) {
          // clear directory, because it may contain obsolete files (ex. if package name was changed)
          FileUtil.delete(aptOutputDirectory);
        }

        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidApt.compile(target, -1, manifestFile.getPath(), packageName, aptOutputDirectory.getPath(), resPaths,
                             ArrayUtil.toStringArray(depLibPackagesSet), facet.getLibrary());

        AndroidJpsUtil.addMessages(context, messages, BUILDER_NAME);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
        }
        else {
          JavaBuilder.addTempSourcePathRoot(context, aptOutputDirectory);
        }
      }
      catch (IOException e) {
        AndroidJpsUtil.reportExceptionError(context, null, e, BUILDER_NAME);
        success = false;
      }
    }
    return success;
  }

  @NotNull
  private static Set<String> getDepLibPackages(@NotNull Module module) throws IOException {
    final Set<String> result = new HashSet<String>();

    for (AndroidFacet depFacet : AndroidJpsUtil.getAllDependentAndroidLibraries(module)) {
      final File depManifestFile = AndroidJpsUtil.getManifestFileForCompilationPath(depFacet);

      if (depManifestFile != null) {
        final String packageName = parsePackageNameFromManifestFile(depManifestFile);

        if (packageName != null) {
          result.add(packageName);
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
  private static String getCannonicalModuleName(@NotNull Module module) {
    return module.getName().toLowerCase(Locale.US);
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
  private static Map<Module, MyModuleData> computeModuleDatas(@NotNull Collection<Module> modules, @NotNull CompileContext context) {
    final Map<Module, MyModuleData> moduleDataMap = new HashMap<Module, MyModuleData>();

    boolean success = true;

    for (Module module : modules) {
      final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
      if (facet == null) {
        continue;
      }

      final Pair<AndroidSdk,IAndroidTarget> pair = AndroidJpsUtil.getAndroidPlatform(module, context, BUILDER_NAME);
      if (pair == null) {
        success = false;
        continue;
      }
      final AndroidSdk androidSdk = pair.getFirst();
      final IAndroidTarget target = pair.getSecond();

      final File outputDir = context.getProjectPaths().getModuleOutputDir(module, false);
      if (outputDir == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, AndroidJpsBundle
          .message("android.jps.errors.output.dir.not.specified", module.getName())));
        success = false;
        continue;
      }

      moduleDataMap.put(module, new MyModuleData(outputDir, androidSdk.getSdkPath(), target, facet));
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

  private static void fillSourceRoots(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Set<File> result)
    throws IOException {
    visited.add(module);
    final AndroidFacet facet = AndroidJpsUtil.getFacet(module);
    File resDir = null;
    File resDirForCompilation = null;

    if (facet != null) {
      resDir = facet.getResourceDir();
      resDirForCompilation = facet.getResourceDirForCompilation();
    }

    for (String sourceRootPath : module.getSourceRoots()) {
      final File sourceRoot = new File(sourceRootPath).getCanonicalFile();
      
      if (!sourceRoot.equals(resDir) && !sourceRoot.equals(resDirForCompilation)) {
        result.add(sourceRoot);
      }
    }
    
    for (ClasspathItem classpathItem : module.getClasspath(ClasspathKind.PRODUCTION_COMPILE)) {
      if (classpathItem instanceof Module) {
        final Module depModule = (Module)classpathItem;

        if (!visited.contains(depModule)) {
          fillSourceRoots(depModule, visited, result);
        }
      }
    }
  }

  @NotNull
  public static File[] getSourceRootsForModuleAndDependencies(@NotNull Module module) throws IOException {
    Set<File> result = new HashSet<File>();
    fillSourceRoots(module, new HashSet<Module>(), result);
    return result.toArray(new File[result.size()]);
  }

  @Override
  public String getDescription() {
    return "Android Source Generating Builder";
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

  private static class MyModuleData {
    private final File myOutputDirectory;
    private final String mySdkLocation;
    private final IAndroidTarget myAndroidTarget;
    private final AndroidFacet myFacet;

    private MyModuleData(@NotNull File outputDirectory,
                         @NotNull String sdkLocation,
                         @NotNull IAndroidTarget androidTarget,
                         @NotNull AndroidFacet facet) {
      myOutputDirectory = outputDirectory;
      mySdkLocation = sdkLocation;
      myAndroidTarget = androidTarget;
      myFacet = facet;
    }

    @NotNull
    public File getOutputDirectory() {
      return myOutputDirectory;
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
  }
}
