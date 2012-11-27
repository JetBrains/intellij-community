/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.compiler;

import com.intellij.CommonBundle;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.artifact.*;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.fileTypes.AndroidIdlFileType;
import org.jetbrains.android.fileTypes.AndroidRenderscriptFileType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidCompilerMessageKind;
import org.jetbrains.android.util.AndroidExecutionUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;

/**
 * @author yole
 */
public class AndroidCompileUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidCompileUtil");

  private static final Key<Boolean> RELEASE_BUILD_KEY = new Key<Boolean>(AndroidCommonUtils.RELEASE_BUILD_OPTION);
  @NonNls private static final String RESOURCES_CACHE_DIR_NAME = "res-cache";
  @NonNls private static final String GEN_MODULE_PREFIX = "~generated_";

  @NonNls public static final String PROGUARD_CFG_FILE_NAME = "proguard-project.txt";
  @NonNls public static final String OLD_PROGUARD_CFG_FILE_NAME = "proguard.cfg";
  public static final String UNSIGNED_SUFFIX = ".unsigned";

  private AndroidCompileUtil() {
  }

  @NotNull
  public static <T> Map<CompilerMessageCategory, T> toCompilerMessageCategoryKeys(@NotNull Map<AndroidCompilerMessageKind, T> map) {
    final Map<CompilerMessageCategory, T> result = new HashMap<CompilerMessageCategory, T>();

    for (Map.Entry<AndroidCompilerMessageKind, T> entry : map.entrySet()) {
      final AndroidCompilerMessageKind key = entry.getKey();
      final T value = entry.getValue();

      switch (key) {
        case ERROR:
          result.put(CompilerMessageCategory.ERROR, value);
          break;
        case INFORMATION:
          result.put(CompilerMessageCategory.INFORMATION, value);
          break;
        case WARNING:
          result.put(CompilerMessageCategory.WARNING, value);
          break;
      }
    }
    return result;
  }

  @Nullable
  public static Pair<VirtualFile, Boolean> getDefaultProguardConfigFile(@NotNull AndroidFacet facet) {
    VirtualFile root = AndroidRootUtil.getMainContentRoot(facet);
    if (root == null) {
      return null;
    }
    final VirtualFile proguardCfg = root.findChild(PROGUARD_CFG_FILE_NAME);
    if (proguardCfg != null) {
      return new Pair<VirtualFile, Boolean>(proguardCfg, true);
    }

    final VirtualFile oldProguardCfg = root.findChild(OLD_PROGUARD_CFG_FILE_NAME);
    if (oldProguardCfg != null) {
      return new Pair<VirtualFile, Boolean>(oldProguardCfg, false);
    }
    return null;
  }

  public static void addMessages(final CompileContext context, final Map<CompilerMessageCategory, List<String>> messages, @Nullable Module module) {
    addMessages(context, messages, null, module);
  }

  static void addMessages(final CompileContext context,
                          final Map<CompilerMessageCategory, List<String>> messages,
                          @Nullable final Map<VirtualFile, VirtualFile> presentableFilesMap,
                          @Nullable final Module module) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (context.getProject().isDisposed()) return;
        for (CompilerMessageCategory category : messages.keySet()) {
          List<String> messageList = messages.get(category);
          for (String message : messageList) {
            String url = null;
            int line = -1;
            Matcher matcher = AndroidCommonUtils.COMPILER_MESSAGE_PATTERN.matcher(message);
            if (matcher.matches()) {
              String fileName = matcher.group(1);
              if (new File(fileName).exists()) {
                url = getPresentableFile("file://" + fileName, presentableFilesMap);
                line = Integer.parseInt(matcher.group(2));
              }
            }
            context.addMessage(category, (module != null ? '[' + module.getName() + "] " : "") + message, url, line, -1);
          }
        }
      }
    });
  }

  @NotNull
  private static String getPresentableFile(@NotNull String url, @Nullable Map<VirtualFile, VirtualFile> presentableFilesMap) {
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file == null) {
      return url;
    }

    if (presentableFilesMap == null) {
      return url;
    }

    for (Map.Entry<VirtualFile, VirtualFile> entry : presentableFilesMap.entrySet()) {
      if (Comparing.equal(file, entry.getValue())) {
        return entry.getKey().getUrl();
      }
    }
    return url;
  }

  private static void collectChildrenRecursively(@NotNull VirtualFile root,
                                                 @NotNull VirtualFile anchor,
                                                 @NotNull Collection<VirtualFile> result) {
    if (Comparing.equal(root, anchor)) {
      return;
    }

    VirtualFile parent = anchor.getParent();
    if (parent == null) {
      return;
    }
    for (VirtualFile child : parent.getChildren()) {
      if (!Comparing.equal(child, anchor)) {
        result.add(child);
      }
    }
    if (!Comparing.equal(parent, root)) {
      collectChildrenRecursively(root, parent, result);
    }
  }

  private static void unexcludeRootIfNeccessary(@NotNull VirtualFile root, @NotNull ModuleRootManager manager) {
    Set<VirtualFile> excludedRoots = new HashSet<VirtualFile>(Arrays.asList(manager.getExcludeRoots()));
    VirtualFile excludedRoot = root;
    while (excludedRoot != null && !excludedRoots.contains(excludedRoot)) {
      excludedRoot = excludedRoot.getParent();
    }
    if (excludedRoot == null) {
      return;
    }
    Set<VirtualFile> rootsToExclude = new HashSet<VirtualFile>();
    collectChildrenRecursively(excludedRoot, root, rootsToExclude);
    final ModifiableRootModel model = manager.getModifiableModel();
    ContentEntry contentEntry = findContentEntryForRoot(model, excludedRoot);
    if (contentEntry != null) {
      ExcludeFolder excludedFolder = null;
      for (ExcludeFolder folder : contentEntry.getExcludeFolders()) {
        if (Comparing.equal(folder.getFile(), excludedRoot)) {
          excludedFolder = folder;
          break;
        }
      }
      if (excludedFolder != null) {
        contentEntry.removeExcludeFolder(excludedFolder);
      }
      for (VirtualFile rootToExclude : rootsToExclude) {
        if (!excludedRoots.contains(rootToExclude)) {
          contentEntry.addExcludeFolder(rootToExclude);
        }
      }
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
  }

  @NotNull
  private static String getGenModuleName(@NotNull Module module) {
    return GEN_MODULE_PREFIX + module.getName();
  }

  @Nullable
  public static VirtualFile createSourceRootIfNotExist(@NotNull final String path, @NotNull final Module module) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final File rootFile = new File(path);
    final boolean created;
    if (!rootFile.exists()) {
      if (!rootFile.mkdirs()) return null;
      created = true;
    }
    else {
      created = false;
    }

    final Project project = module.getProject();

    if (project.isDisposed() || module.isDisposed()) {
      return null;
    }

    final VirtualFile root;
    if (created) {
      root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootFile);
    }
    else {
      root = LocalFileSystem.getInstance().findFileByIoFile(rootFile);
    }
    if (root != null) {
      final ModuleRootManager manager = ModuleRootManager.getInstance(module);
      unexcludeRootIfNeccessary(root, manager);

      boolean markedAsSource = false;

      for (VirtualFile existingRoot : manager.getSourceRoots()) {
        if (Comparing.equal(existingRoot, root)) {
          markedAsSource = true;
        }
      }

      if (!markedAsSource) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            addSourceRoot(manager, root);
          }
        });
      }
    }
    return root;
  }

  private static void excludeFromCompilation(@NotNull Project project, @NotNull VirtualFile sourceRoot, @NotNull String aPackage) {
    final String buildConfigPath = sourceRoot.getPath() + '/' + aPackage.replace('.', '/') + "/BuildConfig.java";
    String url = VfsUtilCore.pathToUrl(buildConfigPath);
    final ExcludedEntriesConfiguration configuration =
      CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();

    for (ExcludeEntryDescription description : configuration.getExcludeEntryDescriptions()) {
      if (Comparing.equal(description.getUrl(), url)) {
        return;
      }
    }
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(url, true, false, project));
  }

  private static void excludeFromCompilation(@NotNull Project project, @NotNull VirtualFile dir) {
    final ExcludedEntriesConfiguration configuration =
      CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration();

    for (ExcludeEntryDescription description : configuration.getExcludeEntryDescriptions()) {
      if (Comparing.equal(description.getVirtualFile(), dir)) {
        return;
      }
    }
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(dir, true, false, project));
  }

  private static void removeGenModule(@NotNull final Module libModule) {
    final String genModuleName = getGenModuleName(libModule);
    final ModuleManager moduleManager = ModuleManager.getInstance(libModule.getProject());

    final Module genModule = moduleManager.findModuleByName(genModuleName);
    if (genModule == null) {
      return;
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(libModule).getModifiableModel();

    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry &&
          genModuleName.equals(((ModuleOrderEntry)entry).getModuleName())) {
        model.removeOrderEntry(entry);
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
      }
    });
    final VirtualFile moduleFile = genModule.getModuleFile();
    moduleManager.disposeModule(genModule);

    if (moduleFile != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                moduleFile.delete(libModule.getProject());
              }
              catch (IOException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
    }
  }

  public static void addSourceRoot(final ModuleRootManager manager, @NotNull final VirtualFile root) {
    final ModifiableRootModel model = manager.getModifiableModel();
    ContentEntry contentEntry = findContentEntryForRoot(model, root);
    if (contentEntry == null) {
      contentEntry = model.addContentEntry(root);
    }
    contentEntry.addSourceFolder(root, false);
    model.commit();
  }

  @Nullable
  public static ContentEntry findContentEntryForRoot(@NotNull ModifiableRootModel model, @NotNull VirtualFile root) {
    ContentEntry contentEntry = null;
    for (ContentEntry candidate : model.getContentEntries()) {
      VirtualFile contentRoot = candidate.getFile();
      if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, root, false)) {
        contentEntry = candidate;
      }
    }
    return contentEntry;
  }

  public static void generate(final Module module, final AndroidAutogeneratorMode mode, boolean withDependentModules) {
    if (withDependentModules) {
      Set<Module> modules = new HashSet<Module>();
      collectModules(module, modules, ModuleManager.getInstance(module.getProject()).getModules());
      for (Module module1 : modules) {
        generate(module1, mode);
      }
    }
    else {
      generate(module, mode);
    }
  }

  public static void generate(final Module module, final AndroidAutogeneratorMode mode) {
    final AndroidFacet facet = AndroidFacet.getInstance(module);

    if (facet != null) {
      facet.scheduleSourceRegenerating(mode);
    }
  }

  public static boolean doGenerate(final Module module, final AndroidAutogeneratorMode mode) {
    final Project project = module.getProject();
    assert !ApplicationManager.getApplication().isDispatchThread();
    final CompileContext[] contextWrapper = new CompileContext[1];

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (project.isDisposed()) return;
        CompilerTask task = new CompilerTask(project, "Android auto-generation", true, false);
        CompileScope scope = new ModuleCompileScope(module, false);
        contextWrapper[0] = new CompileContextImpl(project, task, scope, null, false, false);
      }
    });
    CompileContext context = contextWrapper[0];
    if (context == null) {
      return false;
    }
    generate(module, mode, context);
    return context.getMessages(CompilerMessageCategory.ERROR).length == 0;
  }

  public static boolean isModuleAffected(CompileContext context, Module module) {
    return ArrayUtil.find(context.getCompileScope().getAffectedModules(), module) >= 0;
  }

  public static void generate(final Module module, AndroidAutogeneratorMode mode, final CompileContext context) {
    if (context == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return;
    }

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        if (facet.getModule().isDisposed() || facet.getModule().getProject().isDisposed()) {
          return;
        }
        AndroidCompileUtil.createGenModulesAndSourceRoots(facet);
      }
    }, ModalityState.defaultModalityState());

    AndroidAutogenerator.run(mode, facet, context);
  }

  private static void collectModules(Module module, Set<Module> result, Module[] allModules) {
    if (!result.add(module)) {
      return;
    }
    for (Module otherModule : allModules) {
      if (ModuleRootManager.getInstance(otherModule).isDependsOn(module)) {
        collectModules(otherModule, result, allModules);
      }
    }
  }

  // must be invoked in a read action!

  public static void removeDuplicatingClasses(final Module module, @NotNull final String packageName, @NotNull String className,
                                              @Nullable File classFile, String sourceRootPath) {
    if (sourceRootPath == null) {
      return;
    }
    VirtualFile sourceRoot = LocalFileSystem.getInstance().findFileByPath(sourceRootPath);
    if (sourceRoot == null) {
      return;
    }
    final Project project = module.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final String interfaceQualifiedName = packageName + '.' + className;
    PsiClass[] classes = facade.findClasses(interfaceQualifiedName, GlobalSearchScope.moduleScope(module));
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    for (PsiClass c : classes) {
      PsiFile psiFile = c.getContainingFile();
      if (className.equals(FileUtil.getNameWithoutExtension(psiFile.getName()))) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null && Comparing.equal(projectFileIndex.getSourceRootForFile(virtualFile), sourceRoot)) {
          final String path = virtualFile.getPath();
          final File f = new File(path);

          if (!FileUtil.filesEqual(f, classFile) && f.exists()) {
            if (f.delete()) {
              virtualFile.refresh(true, false);
            }
            else {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showErrorDialog(project, "Can't delete file " + path, CommonBundle.getErrorTitle());
                }
              }, project.getDisposed());
            }
          }
        }
      }
    }
  }

  @NotNull
  public static String[] collectResourceDirs(AndroidFacet facet, boolean collectResCacheDirs, @Nullable CompileContext context) {
    final Project project = facet.getModule().getProject();
    final IntermediateOutputCompiler pngFilesCachingCompiler =
      collectResCacheDirs ? Extensions.findExtension(Compiler.EP_NAME, project, AndroidPngFilesCachingCompiler.class) : null;

    if (collectResCacheDirs) {
      assert pngFilesCachingCompiler != null;
    }

    final List<String> result = new ArrayList<String>();

    doCollectResourceDirs(facet, collectResCacheDirs, result, context);

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      doCollectResourceDirs(depFacet, collectResCacheDirs, result, context);
    }
    return ArrayUtil.toStringArray(result);
  }

  private static void doCollectResourceDirs(AndroidFacet facet, boolean collectResCacheDirs, List<String> result, CompileContext context) {
    final Module module = facet.getModule();

    if (collectResCacheDirs) {
      final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      final int platformToolsRevision = platform != null ? platform.getSdkData().getPlatformToolsRevision() : -1;

      if (platformToolsRevision < 0 || platformToolsRevision > 7) {
        // png cache is supported since platform-tools-r8
        final String resCacheDirOsPath = findResourcesCacheDirectory(module, false, context);
        if (resCacheDirOsPath != null) {
          result.add(resCacheDirOsPath);
        }
        else {
          LOG.info("PNG cache not found for module " + module.getName());
        }
      }
    }

    final VirtualFile resourcesDir = AndroidAptCompiler.getResourceDirForApkCompiler(facet);
    if (resourcesDir != null) {
      result.add(resourcesDir.getPath());
    }
  }

  @Nullable
  public static String findResourcesCacheDirectory(@NotNull Module module, boolean createIfNotFound, @Nullable CompileContext context) {
    final Project project = module.getProject();

    final CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
    if (extension == null) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot get compiler settings for project " + project.getName(), null, -1, -1);
      }
      return null;
    }

    final String projectOutputDirUrl = extension.getCompilerOutputUrl();
    if (projectOutputDirUrl == null) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot find output directory for project " + project.getName(), null, -1, -1);
      }
      return null;
    }

    final String pngCacheDirPath = VfsUtil.urlToPath(projectOutputDirUrl) + '/' + RESOURCES_CACHE_DIR_NAME + '/' + module.getName();
    final String pngCacheDirOsPath = FileUtil.toSystemDependentName(pngCacheDirPath);

    final File pngCacheDir = new File(pngCacheDirOsPath);
    if (pngCacheDir.exists()) {
      if (pngCacheDir.isDirectory()) {
        return pngCacheDirOsPath;
      }
      else {
        if (context != null) {
          context.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory " + pngCacheDirOsPath + " because file already exists",
                             null, -1, -1);
        }
        return null;
      }
    }

    if (!createIfNotFound) {
      return null;
    }

    if (!pngCacheDir.mkdirs()) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot create directory " + pngCacheDirOsPath, null, -1, -1);
      }
      return null;
    }

    return pngCacheDirOsPath;
  }

  public static boolean isFullBuild(@NotNull CompileContext context) {
    return isFullBuild(context.getCompileScope());
  }

  public static boolean isFullBuild(@NotNull CompileScope scope) {
    final RunConfiguration c = CompileStepBeforeRun.getRunConfiguration(scope);
    return c == null || !AndroidCommonUtils.isTestConfiguration(c.getType().getId());
  }

  public static boolean isReleaseBuild(@NotNull CompileContext context) {
    final Boolean value = context.getCompileScope().getUserData(RELEASE_BUILD_KEY);
    if (value != null && value.booleanValue()) {
      return true;
    }
    final Project project = context.getProject();
    final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, context.getCompileScope(), false);

    if (artifacts != null) {
      for (Artifact artifact : artifacts) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());
        if (properties instanceof AndroidApplicationArtifactProperties &&
            ((AndroidApplicationArtifactProperties)properties).getSigningMode() != AndroidArtifactSigningMode.DEBUG) {
          return true;
        }
      }
    }
    return false;
  }

  public static void setReleaseBuild(@NotNull CompileScope compileScope) {
    compileScope.putUserData(RELEASE_BUILD_KEY, Boolean.TRUE);
  }

  public static void createGenModulesAndSourceRoots(@NotNull final AndroidFacet facet) {
    final Module module = facet.getModule();
    final GlobalSearchScope moduleScope = facet.getModule().getModuleScope();

    if (facet.getConfiguration().LIBRARY_PROJECT) {
      removeGenModule(module);
    }
    initializeGenSourceRoot(module, AndroidRootUtil.getBuildconfigGenSourceRootPath(facet), true, true);

    initializeGenSourceRoot(module, AndroidRootUtil.getRenderscriptGenSourceRootPath(facet),
                            FileTypeIndex.getFiles(AndroidRenderscriptFileType.INSTANCE, moduleScope).size() > 0, true);

    if (AndroidAptCompiler.isToCompileModule(module, facet.getConfiguration())) {
      initializeGenSourceRoot(module, AndroidRootUtil.getAptGenSourceRootPath(facet), true, true);
    }
    else {
      // we need to include generated-sources/r to compilation, because it contains R.java generated by Maven,
      // which should be used in Maven-based resource processing mode
      final VirtualFile aptSourceRoot = initializeGenSourceRoot(module, AndroidRootUtil.getAptGenSourceRootPath(facet), true, false);
      
      if (aptSourceRoot != null) {
        excludeAllBuildConfigsFromCompilation(facet, aptSourceRoot);
      }
      includeAaptGenSourceRootToCompilation(facet);
    }
    initializeGenSourceRoot(module, AndroidRootUtil.getAidlGenSourceRootPath(facet),
                            FileTypeIndex.getFiles(AndroidIdlFileType.ourFileType, moduleScope).size() > 0, true);
  }

  private static void excludeAllBuildConfigsFromCompilation(AndroidFacet facet, VirtualFile sourceRoot) {
    final Module module = facet.getModule();
    final Project project = module.getProject();
    final Set<String> packages = new HashSet<String>();

    final Manifest manifest = facet.getManifest();
    final String aPackage = manifest != null ? manifest.getPackage().getStringValue() : null;

    if (aPackage != null) {
      packages.add(aPackage);
    }
    packages.addAll(AndroidUtils.getDepLibsPackages(module));

    for (String p : packages) {
      excludeFromCompilation(project, sourceRoot, p);
    }
  }

  private static void includeAaptGenSourceRootToCompilation(AndroidFacet facet) {
    final Project project = facet.getModule().getProject();
    final ExcludedEntriesConfiguration configuration =
      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).getExcludedEntriesConfiguration();
    final ExcludeEntryDescription[] descriptions = configuration.getExcludeEntryDescriptions();

    configuration.removeAllExcludeEntryDescriptions();

    for (ExcludeEntryDescription description : descriptions) {
      final VirtualFile vFile = description.getVirtualFile();
      if (!Comparing.equal(vFile, AndroidRootUtil.getAaptGenDir(facet))) {
        configuration.addExcludeEntryDescription(description);
      }
    }
  }

  @Nullable
  private static VirtualFile initializeGenSourceRoot(@NotNull Module module,
                                                     @Nullable String sourceRootPath,
                                                     boolean createIfNotExist,
                                                     boolean exclude) {
    if (sourceRootPath == null) {
      return null;
    }
    VirtualFile sourceRoot = null;

    if (createIfNotExist) {
      final VirtualFile root = createSourceRootIfNotExist(sourceRootPath, module);
      if (root != null) {
        sourceRoot = root;
      }
    }
    if (sourceRoot == null) {
      sourceRoot = LocalFileSystem.getInstance().findFileByPath(sourceRootPath);
    }
    if (sourceRoot != null && exclude) {
      excludeFromCompilation(module.getProject(), sourceRoot);
    }
    return sourceRoot;
  }

  @NotNull
  public static String[] toOsPaths(@NotNull VirtualFile[] classFilesDirs) {
    final String[] classFilesDirOsPaths = new String[classFilesDirs.length];

    for (int i = 0; i < classFilesDirs.length; i++) {
      classFilesDirOsPaths[i] = FileUtil.toSystemDependentName(classFilesDirs[i].getPath());
    }
    return classFilesDirOsPaths;
  }

  // can't be invoked from dispatch thread
  @NotNull
  public static Map<CompilerMessageCategory, List<String>> execute(String... argv) throws IOException {
    assert !ApplicationManager.getApplication().isDispatchThread();
    final Map<AndroidCompilerMessageKind, List<String>> messages = AndroidExecutionUtil.doExecute(argv);
    return toCompilerMessageCategoryKeys(messages);
  }

  public static String getApkName(Module module) {
    return module.getName() + ".apk";
  }

  @Nullable
  public static String getOutputPackage(@NotNull Module module) {
    VirtualFile compilerOutput = CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
    if (compilerOutput == null) return null;
    return new File(compilerOutput.getPath(), getApkName(module)).getPath();
  }

  public static boolean isExcludedFromCompilation(@NotNull File file, @Nullable Project project) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    return vFile != null && isExcludedFromCompilation(vFile, project);
  }

  public static boolean isExcludedFromCompilation(VirtualFile child, @Nullable Project project) {
    final CompilerManager compilerManager = project != null ? CompilerManager.getInstance(project) : null;

    if (compilerManager == null) {
      return false;
    }

    if (!compilerManager.isExcludedFromCompilation(child)) {
      return false;
    }

    final Module module = ModuleUtil.findModuleForFile(child, project);
    if (module == null) {
      return true;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
      return true;
    }

    final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      return true;
    }

    // we exclude sources of library modules automatically for tools r7 or previous
    return platform.getSdkData().getPlatformToolsRevision() > 7;
  }

  @Nullable
  public static ProguardRunningOptions getProguardConfigFilePathIfShouldRun(@NotNull AndroidFacet facet, CompileContext context) {
    // wizard
    String path = context.getCompileScope().getUserData(AndroidProguardCompiler.PROGUARD_CFG_PATH_KEY);
    if (path != null) {
      final Boolean includeSystemProguardFile = context.getCompileScope().
        getUserData(AndroidProguardCompiler.INCLUDE_SYSTEM_PROGUARD_FILE);
      return new ProguardRunningOptions(path, Boolean.TRUE.equals(includeSystemProguardFile));
    }
    
    // artifact
    final Project project = context.getProject();
    final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, context.getCompileScope(), false);

    for (Artifact artifact : artifacts) {
      if (artifact.getArtifactType() instanceof AndroidApplicationArtifactType &&
          facet.equals(AndroidArtifactUtil.getPackagedFacet(project, artifact))) {
        final ArtifactProperties<?> properties = artifact.getProperties(AndroidArtifactPropertiesProvider.getInstance());
        
        if (properties instanceof AndroidApplicationArtifactProperties) {
          final AndroidApplicationArtifactProperties p = (AndroidApplicationArtifactProperties)properties;
          
          if (p.isRunProGuard()) {
            path = FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(p.getProGuardCfgFileUrl()));
            return new ProguardRunningOptions(path, p.isIncludeSystemProGuardCfgFile());
          }
        }
      }
    }

    // facet
    final AndroidFacetConfiguration configuration = facet.getConfiguration();
    if (configuration.RUN_PROGUARD) {
      final VirtualFile proguardCfgFile = AndroidRootUtil.getProguardCfgFile(facet);
      final String proguardCfgPath = proguardCfgFile != null ? FileUtil.toSystemDependentName(proguardCfgFile.getPath()) : null;
      return new ProguardRunningOptions(proguardCfgPath, configuration.isIncludeSystemProguardCfgPath());
    }
    return null;
  }

  @Nullable
  public static Module findCircularDependencyOnLibraryWithSamePackage(@NotNull AndroidFacet facet) {
    final Manifest manifest = facet.getManifest();
    final String aPackage = manifest != null ? manifest.getPackage().getValue() : null;
    if (aPackage == null) {
      return null;
    }

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      final Manifest depManifest = depFacet.getManifest();
      final String depPackage = depManifest != null ? depManifest.getPackage().getValue() : null;
      if (aPackage.equals(depPackage)) {
        final List<AndroidFacet> depDependencies = AndroidUtils.getAllAndroidDependencies(depFacet.getModule(), false);

        if (depDependencies.contains(facet)) {
          // circular dependency on library with the same package
          return depFacet.getModule();
        }
      }
    }
    return null;
  }

  @NotNull
  public static String[] getLibPackages(@NotNull Module module, @NotNull String packageName) {
    final Set<String> packageSet = new HashSet<String>();
    packageSet.add(packageName);

    final List<String> result = new ArrayList<String>();

    for (String libPackage : AndroidUtils.getDepLibsPackages(module)) {
      if (packageSet.add(libPackage)) {
        result.add(libPackage);
      }
    }

    return ArrayUtil.toStringArray(result);
  }

  // support for lib<->lib and app<->lib circular dependencies
  // see IDEA-79737 for details
  public static boolean isLibraryWithBadCircularDependency(@NotNull AndroidFacet facet) {
    if (!facet.getConfiguration().LIBRARY_PROJECT) {
      return false;
    }

    final List<AndroidFacet> dependencies = AndroidUtils.getAllAndroidDependencies(facet.getModule(), false);

    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return false;
    }

    final String aPackage = manifest.getPackage().getValue();
    if (aPackage == null || aPackage.length() == 0) {
      return false;
    }

    for (AndroidFacet depFacet : dependencies) {
      final List<AndroidFacet> depDependencies = AndroidUtils.getAllAndroidDependencies(depFacet.getModule(), true);

      if (depDependencies.contains(facet) &&
          dependencies.contains(depFacet) &&
          (depFacet.getModule().getName().compareTo(facet.getModule().getName()) < 0 ||
           !depFacet.getConfiguration().LIBRARY_PROJECT)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getUnsignedApkPath(@NotNull AndroidFacet facet) {
    final String apkPath = AndroidRootUtil.getApkPath(facet);
    return apkPath != null ? AndroidCommonUtils.addSuffixToFileName(apkPath, UNSIGNED_SUFFIX) : null;
  }

  @Nullable
  public static <T> T handleExceptionError(@NotNull CompileContext context,
                                           @NotNull String messagePrefix,
                                           @NotNull Exception e) {
    reportException(context, messagePrefix, e);
    return null;
  }

  public static void reportException(@NotNull CompileContext context, @NotNull String messagePrefix, @NotNull Exception e) {
    context.addMessage(CompilerMessageCategory.ERROR, messagePrefix + e.getClass().getSimpleName() + ": " + e.getMessage(), null, -1, -1);
  }

  public static void markDirtyAndRefresh(VirtualFile f, boolean recursively) {
    markDirty(f, recursively);
    f.refresh(false, recursively);
  }

  public static void markDirty(VirtualFile f, boolean recursively) {
    if (f instanceof NewVirtualFile) {
      final NewVirtualFile newF = (NewVirtualFile)f;

      if (recursively) {
        newF.markDirtyRecursively();
      }
      else {
        newF.markDirty();
      }
    }
  }
}