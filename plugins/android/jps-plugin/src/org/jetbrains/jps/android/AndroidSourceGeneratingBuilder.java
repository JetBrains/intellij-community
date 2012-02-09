package org.jetbrains.jps.android;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidIdl;
import org.jetbrains.android.compiler.tools.AndroidRenderscript;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.idea.Facet;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.incremental.storage.SourceToOutputMapping;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSourceGeneratingBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.android.AndroidSourceGeneratingBuilder");

  @NonNls private static final String BUILDER_NAME = "android-source-generator";
  @NonNls private static final String AIDL_EXTENSION = "aidl";
  @NonNls private static final String RENDERSCRIPT_EXTENSION = "rs";

  public AndroidSourceGeneratingBuilder() {
    super(BuilderCategory.SOURCE_GENERATOR);
  }

  @Override
  public String getName() {
    return BUILDER_NAME;
  }

  @Override
  public ModuleLevelBuilder.ExitCode build(CompileContext context, ModuleChunk chunk) throws ProjectBuildException {
    if (context.isCompilingTests()) {
      return ModuleLevelBuilder.ExitCode.OK;
    }
    
    try {
      return doBuild(context, chunk);
    }
    catch (ProjectBuildException e) {
      throw e;
    }
    catch (Exception e) {
      String message = e.getMessage();

      if (message == null) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        //noinspection IOResourceOpenedButNotSafelyClosed
        e.printStackTrace(new PrintStream(out));
        message = "Internal error: \n" + out.toString();
      }
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
      throw new ProjectBuildException(message, e);
    }
  }

  private static ModuleLevelBuilder.ExitCode doBuild(CompileContext context, ModuleChunk chunk) throws Exception {
    final Map<File, AndroidFacet> idlFilesToCompile = new HashMap<File, AndroidFacet>();
    final Map<File, AndroidFacet> rsFilesToCompile = new HashMap<File, AndroidFacet>();
    final Set<Module> modules = new HashSet<Module>();

    context.processFilesToRecompile(chunk, new FileProcessor() {
      @Override
      public boolean apply(Module module, File file, String sourceRoot) throws IOException {
        final AndroidFacet facet = getFacet(module);

        if (facet == null) {
          return true;
        }
        final String ext = FileUtil.getExtension(file.getName());

        if (AIDL_EXTENSION.equals(ext)) {
          idlFilesToCompile.put(file, facet);
          modules.add(facet.getModule());
        }
        else if (RENDERSCRIPT_EXTENSION.equals(ext)) {
          rsFilesToCompile.put(file, facet);
          modules.add(facet.getModule());
        }

        return true;
      }
    });

    final Map<Module, MyModuleData> moduleDataMap = computeModuleDatas(modules, context);

    if (moduleDataMap == null) {
      return ExitCode.OK;
    }
    
    if (!runAidlCompiler(context, idlFilesToCompile, moduleDataMap)) {
      return ExitCode.OK;
    }

    if (!runRenderscriptCompiler(context, rsFilesToCompile, moduleDataMap)) {
      return ExitCode.OK;
    }

    return ExitCode.OK;
  }

  private static boolean runAidlCompiler(@NotNull final CompileContext context,
                                         @NotNull Map<File, AndroidFacet> files,
                                         @NotNull Map<Module, MyModuleData> moduleDataMap) {
    context.processMessage(new ProgressMessage("Processing AIDL files..."));
    
    boolean success = true;

    for (Map.Entry<File, AndroidFacet> entry : files.entrySet()) {
      final File file = entry.getKey();
      final Module module = entry.getValue().getModule();
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
        final String[] sourceRootPaths = toPaths(sourceRoots); 
        final String packageName = computePackageForFile(context, file);
        
        if (packageName == null) {
          context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, "Cannot compute package for file",
                                                     filePath));
          success = false;
          continue;
        }

        final File outputFile = new File(aidlOutputDirectory, packageName.replace('.', File.separatorChar) +
                                                              File.separator + FileUtil.getNameWithoutExtension(file) + ".java");
        final String outputFilePath = outputFile.getPath();
        final Map<AndroidCompilerMessageKind, List<String>> messages =
          AndroidIdl.execute(target, filePath, outputFilePath, sourceRootPaths);

        addMessages(context, messages, filePath);

        if (messages.get(AndroidCompilerMessageKind.ERROR).size() > 0) {
          success = false;
          continue;
        }

        final String moduleName = getCannonicalModuleName(module);
        final SourceToOutputMapping sourceToOutputMap = context.getDataManager().getSourceToOutputMap(moduleName, false);
        sourceToOutputMap.update(filePath, outputFilePath);
      }
      catch (final IOException e) {
        reportExceptionError(context, filePath, e);
        success = false;
      }
    }
    return success;
  }

  private static boolean runRenderscriptCompiler(@NotNull final CompileContext context,
                                                 @NotNull Map<File, AndroidFacet> files,
                                                 @NotNull Map<Module, MyModuleData> moduleDataMap) {
    context.processMessage(new ProgressMessage("Processing Renderscript files..."));

    boolean success = true;

    for (Map.Entry<File, AndroidFacet> entry : files.entrySet()) {
      final File file = entry.getKey();
      final Module module = entry.getValue().getModule();

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

        addMessages(context, messages, filePath);

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
          final List<String> newFilePaths = Arrays.asList(toPaths(newFiles.toArray(new File[newFiles.size()])));

          final String moduleName = getCannonicalModuleName(module);
          final SourceToOutputMapping sourceToOutputMap = context.getDataManager().getSourceToOutputMap(moduleName, false);
          sourceToOutputMap.update(filePath, newFilePaths);
        }
      }
      catch (IOException e) {
        reportExceptionError(context, filePath, e);
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

  private static void reportExceptionError(@NotNull CompileContext context, @NotNull String filePath, @NotNull Exception exception) {
    final String message = exception.getMessage();

    if (message != null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message, filePath));
      LOG.debug(exception);
    }
    else {
      context.processMessage(new CompilerMessage(BUILDER_NAME, exception));
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
  private static Map<Module, MyModuleData> computeModuleDatas(@NotNull Collection<Module> modules, @NotNull CompileContext context)
    throws Exception {
    final Map<Module, MyModuleData> moduleDataMap = new HashMap<Module, MyModuleData>();

    boolean success = true;

    for (Module module : modules) {

      final Sdk sdk = module.getSdk();
      if (!(sdk instanceof AndroidSdk)) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   "Android SDK is not specified for module " + module.getName()));
        success = false;
        continue;
      }
      final AndroidSdk androidSdk = (AndroidSdk)sdk;

      final IAndroidTarget target = parseAndroidTarget(androidSdk, context);
      if (target == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   "Android SDK is invalid or not specified for module " + module.getName()));
        success = false;
        continue;
      }

      final File outputDir = context.getProjectPaths().getModuleOutputDir(module, false);
      if (outputDir == null) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                   "Cannot find output directory for module " + module.getName()));
        success = false;
        continue;
      }

      moduleDataMap.put(module, new MyModuleData(outputDir, androidSdk.getSdkPath(), target));
    }

    return success ? moduleDataMap : null;
  }

  private static void addMessages(@NotNull CompileContext context,
                                  @NotNull Map<AndroidCompilerMessageKind, List<String>> messages,
                                  @Nullable String sourcePath) {
    for (Map.Entry<AndroidCompilerMessageKind, List<String>> entry : messages.entrySet()) {
      final AndroidCompilerMessageKind kind = entry.getKey();
      final BuildMessage.Kind buildMessageKind = toBuildMessageKind(kind);
      
      if (buildMessageKind == null) {
        continue;
      }

      for (String message : entry.getValue()) {
        context.processMessage(new CompilerMessage(BUILDER_NAME, buildMessageKind, message, sourcePath));
      }
    }
  }

  @Nullable
  private static BuildMessage.Kind toBuildMessageKind(@NotNull AndroidCompilerMessageKind kind) {
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

  @Nullable
  private static IAndroidTarget parseAndroidTarget(@NotNull AndroidSdk sdk, @NotNull CompileContext context) {
    final String targetHashString = sdk.getBuildTargetHashString();
    if (targetHashString == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 "Cannot parse SDK " + sdk.getName() + ": build target is not specified"));
      return null;
    }

    final MessageBuildingSdkLog log = new MessageBuildingSdkLog();
    final SdkManager manager = AndroidCommonUtils.createSdkManager(sdk.getSdkPath(), log);

    if (manager == null) {
      final String message = log.getErrorMessage();
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 "Android SDK is parsed incorrectly." +
                                                 (message.length() > 0 ? " Parsing log:\n" + message : "")));
      return null;
    }

    final IAndroidTarget target = manager.getTargetFromHashString(targetHashString);
    if (target == null) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 "Cannot parse SDK '" + sdk.getName() + "': unknown target " + targetHashString));
      return null;
    }
    return target;
  }

  private static void fillSourceRoots(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Set<File> result)
    throws IOException {
    visited.add(module);
    final AndroidFacet facet = getFacet(module);
    File resDir = null;

    if (facet != null) {
      resDir = facet.getResourceDir();
      if (resDir != null) {
        resDir = resDir.getCanonicalFile();
      }
    }

    for (String sourceRootPath : module.getSourceRoots()) {
      final File sourceRoot = new File(sourceRootPath).getCanonicalFile();
      
      if (!sourceRoot.equals(resDir)) {
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
    return "Android Builder";
  }
  
  @Nullable
  private static AndroidFacet getFacet(@NotNull Module module) {
    AndroidFacet androidFacet = null;

    for (Facet facet : module.getFacets().values()) {
      if (facet instanceof AndroidFacet) {
        androidFacet = (AndroidFacet)facet;
      }
    }
    return androidFacet;
  }
  
  @NotNull
  private static String[] toPaths(@NotNull File[] files) {
    final String[] result = new String[files.length];
    
    for (int i = 0; i < result.length; i++) {
      result[i] = files[i].getPath();
    }
    return result;
  }
  
  private static class MyModuleData {
    private final File myOutputDirectory;
    private final String mySdkLocation;
    private final IAndroidTarget myAndroidTarget;

    private MyModuleData(@NotNull File outputDirectory,
                         @NotNull String sdkLocation,
                         @NotNull IAndroidTarget androidTarget) {
      myOutputDirectory = outputDirectory;
      mySdkLocation = sdkLocation;
      myAndroidTarget = androidTarget;
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
  }
}
