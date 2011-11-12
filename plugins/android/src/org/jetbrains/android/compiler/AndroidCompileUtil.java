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
import com.intellij.compiler.impl.CompileContextImpl;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.progress.CompilerTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class AndroidCompileUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidCompileUtil");

  private static final Pattern ourMessagePattern = Pattern.compile("(.+):(\\d+):.+");

  private static final Key<Boolean> RELEASE_BUILD_KEY = new Key<Boolean>("RELEASE_BUILD_KEY");
  @NonNls private static final String RESOURCES_CACHE_DIR_NAME = "res-cache";
  @NonNls private static final String GEN_MODULE_PREFIX = "~generated_";

  private AndroidCompileUtil() {
  }

  static void addMessages(final CompileContext context, final Map<CompilerMessageCategory, List<String>> messages) {
    addMessages(context, messages, null);
  }

  public static void addMessages(@NotNull Map<CompilerMessageCategory, List<String>> messages,
                                 @NotNull Map<CompilerMessageCategory, List<String>> toAdd) {
    for (Map.Entry<CompilerMessageCategory, List<String>> entry : toAdd.entrySet()) {
      List<String> list = messages.get(entry.getKey());
      if (list == null) {
        list = new ArrayList<String>();
        messages.put(entry.getKey(), list);
      }
      list.addAll(entry.getValue());
    }
  }

  static void addMessages(final CompileContext context, final Map<CompilerMessageCategory, List<String>> messages,
                          @Nullable final Map<VirtualFile, VirtualFile> presentableFilesMap) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        if (context.getProject().isDisposed()) return;
        for (CompilerMessageCategory category : messages.keySet()) {
          List<String> messageList = messages.get(category);
          for (String message : messageList) {
            String url = null;
            int line = -1;
            Matcher matcher = ourMessagePattern.matcher(message);
            if (matcher.matches()) {
              String fileName = matcher.group(1);
              if (new File(fileName).exists()) {
                url = getPresentableFile("file://" + fileName, presentableFilesMap);
                line = Integer.parseInt(matcher.group(2));
              }
            }
            context.addMessage(category, message, url, line, -1);
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
      if (file == entry.getValue()) {
        return entry.getKey().getUrl();
      }
    }
    return url;
  }

  private static void collectChildrenRecursively(@NotNull VirtualFile root,
                                                 @NotNull VirtualFile anchor,
                                                 @NotNull Collection<VirtualFile> result) {
    VirtualFile parent = anchor.getParent();
    if (parent == null) {
      return;
    }
    for (VirtualFile child : parent.getChildren()) {
      if (child != anchor) {
        result.add(child);
      }
    }
    if (parent != root) {
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
        if (folder.getFile() == excludedRoot) {
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
    // todo: name can be used by another module
    return GEN_MODULE_PREFIX + module.getName();
  }

  public static boolean isGenModule(@NotNull Module module) {
    return module.getName().startsWith(GEN_MODULE_PREFIX);
  }
  
  @Nullable
  public static Module getBaseModuleByGenModule(@NotNull Module module) {
    if (isGenModule(module)) {
      final String baseModuleName = module.getName().substring(GEN_MODULE_PREFIX.length());
      return ModuleManager.getInstance(module.getProject()).findModuleByName(baseModuleName);
    }
    return null;
  }

  @Nullable
  public static Module getGenModule(@NotNull Module baseModule) {
    final String genModuleName = getGenModuleName(baseModule);
    return ModuleManager.getInstance(baseModule.getProject()).findModuleByName(genModuleName);
  }

  public static void createSourceRootIfNotExist(@NotNull final String path, @NotNull final Module module) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    final File rootFile = new File(path);
    final boolean created;
    if (!rootFile.exists()) {
      if (!rootFile.mkdirs()) return;
      created = true;
    }
    else {
      created = false;
    }

    final Project project = module.getProject();
    Module genModule = module;

    final AndroidFacet facet = AndroidFacet.getInstance(genModule);

    if (facet != null && facet.getConfiguration().LIBRARY_PROJECT) {
      final VirtualFile oldStyleRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      if (oldStyleRoot != null) {
        removeSourceRoot(module, oldStyleRoot);
      }

      genModule = setUpGenModule(module);
      if (genModule == null) {
        return;
      }
    }

    if (project.isDisposed() || genModule.isDisposed()) {
      return;
    }

    final VirtualFile root;
    if (created) {
      root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootFile);
    }
    else {
      root = LocalFileSystem.getInstance().findFileByIoFile(rootFile);
    }
    if (root != null) {
      final ModuleRootManager manager = ModuleRootManager.getInstance(genModule);
      unexcludeRootIfNeccessary(root, manager);
      for (VirtualFile existingRoot : manager.getSourceRoots()) {
        if (existingRoot == root) return;
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          addSourceRoot(manager, root);
        }
      });
    }
  }
  
  private static Module setUpGenModule(@NotNull Module libModule) {
    final ModuleRootManager libRootManager = ModuleRootManager.getInstance(libModule);
    final Sdk sdk = libRootManager.getSdk();

    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      LOG.error("Android SDK is not specified for module " + libModule.getName());
      return null;
    }

    final AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    final Sdk jdk = data != null ? data.getJavaSdk() : null;

    if (jdk == null) {
      LOG.error("Internal Java SDK is not specified for module " + libModule.getName());
      return null;
    }

    final Module genModule = findOrCreateGenModule(libModule);
    
    final ModifiableRootModel genModel = ModuleRootManager.getInstance(genModule).getModifiableModel();
    genModel.setSdk(jdk);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        genModel.commit();
      }
    });
    

    if (ArrayUtil.find(libRootManager.getDependencies(), genModule) < 0) {
      final ModifiableRootModel libModel = libRootManager.getModifiableModel();
      libModel.addModuleOrderEntry(genModule);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          libModel.commit();
        }
      });
    }
    
    return genModule;
  }

  @NotNull
  private static Module findOrCreateGenModule(Module module) {
    final String genModuleName = getGenModuleName(module);
    final ModuleManager moduleManager = ModuleManager.getInstance(module.getProject());
    
    final Module genModule = moduleManager.findModuleByName(genModuleName);
    if (genModule != null) {
      return genModule;
    }
    
    final File moduleDir = new File(module.getModuleFilePath()).getParentFile();
    final String moduleDirPath = FileUtil.toSystemIndependentName(moduleDir.getPath());

    return ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return moduleManager.newModule(moduleDirPath + '/' + genModuleName + ModuleFileType.DOT_DEFAULT_EXTENSION,
                                       StdModuleTypes.JAVA);
      }
    });
  }
  
  private static void removeSourceRoot(@NotNull Module module, @NotNull final VirtualFile root) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final ContentEntry contentEntry = findContentEntryForRoot(model, root);

    if (contentEntry != null) {
      for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
        if (sourceFolder.getFile() == root) {
          contentEntry.removeSourceFolder(sourceFolder);
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
  private static ContentEntry findContentEntryForRoot(@NotNull ModifiableRootModel model, @NotNull VirtualFile root) {
    ContentEntry contentEntry = null;
    for (ContentEntry candidate : model.getContentEntries()) {
      VirtualFile contentRoot = candidate.getFile();
      if (contentRoot != null && VfsUtil.isAncestor(contentRoot, root, false)) {
        contentEntry = candidate;
      }
    }
    return contentEntry;
  }

  public static void generate(final Module module, final GeneratingCompiler compiler, boolean withDependentModules) {
    if (withDependentModules) {
      Set<Module> modules = new HashSet<Module>();
      collectModules(module, modules, ModuleManager.getInstance(module.getProject()).getModules());
      for (Module module1 : modules) {
        generate(module1, compiler);
      }
    }
    else {
      generate(module, compiler);
    }
  }

  public static void generate(final Module module, GeneratingCompiler compiler) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    final CompileContext[] contextWrapper = new CompileContext[1];
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        Project project = module.getProject();
        if (project.isDisposed()) return;
        CompilerTask task = new CompilerTask(project, true, "Android auto-generation", true);
        CompileScope scope = new ModuleCompileScope(module, false);
        contextWrapper[0] = new CompileContextImpl(project, task, scope, null, false, false);
      }
    });
    generate(compiler, contextWrapper[0]);
  }

  public static boolean isModuleAffected(CompileContext context, Module module) {
    return ArrayUtil.find(context.getCompileScope().getAffectedModules(), module) >= 0;
  }

  public static void generate(GeneratingCompiler compiler, final CompileContext context) {
    if (context == null) {
      return;
    }
    
    final Set<Module> affectedModules = new HashSet<Module>();
    Collections.addAll(affectedModules, context.getCompileScope().getAffectedModules());
    
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        for (Module module : affectedModules) {
          final AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet != null) {
            AndroidCompileUtil.createGenModulesAndSourceRoots(facet);
          }
        }
      }
    }, ModalityState.defaultModalityState());
    
    List<GeneratingCompiler.GenerationItem> itemsToGenerate = new ArrayList<GeneratingCompiler.GenerationItem>();
    for (GeneratingCompiler.GenerationItem item : compiler.getGenerationItems(context)) {
      if (affectedModules.contains(item.getModule())) {
        itemsToGenerate.add(item);
      }
    }

    GeneratingCompiler.GenerationItem[] items = itemsToGenerate.toArray(new GeneratingCompiler.GenerationItem[itemsToGenerate.size()]);

    final boolean[] run = {true};
    final VirtualFile[] files = getFilesToCheckReadonlyStatus(items);
    if (files.length > 0) {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              final Project project = context.getProject();
              run[0] = !project.isDisposed() && ReadonlyStatusHandler.ensureFilesWritable(project, files);
            }
          });
        }
      }, ModalityState.defaultModalityState());
    }

    if (run[0]) {
      compiler.generate(context, items, null);
    }
  }

  private static VirtualFile[] getFilesToCheckReadonlyStatus(GeneratingCompiler.GenerationItem[] items) {
    List<VirtualFile> filesToCheck = new ArrayList<VirtualFile>();
    for (GeneratingCompiler.GenerationItem item : items) {
      if (item instanceof AndroidAptCompiler.AptGenerationItem) {
        final Set<File> generatedFiles = ((AndroidAptCompiler.AptGenerationItem)item).getGeneratedFiles().keySet();
        for (File generatedFile : generatedFiles) {
          if (generatedFile.exists()) {
            VirtualFile generatedVFile = LocalFileSystem.getInstance().findFileByIoFile(generatedFile);
            if (generatedVFile != null) {
              filesToCheck.add(generatedVFile);
            }
          }
        }
      }
    }
    return VfsUtil.toVirtualFileArray(filesToCheck);
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
        if (virtualFile != null && projectFileIndex.getSourceRootForFile(virtualFile) == sourceRoot) {
          final String path = virtualFile.getPath();
          File f = new File(path);

          try {
            f = f.getCanonicalFile();
            classFile = classFile != null ? classFile.getCanonicalFile() : null;
            if (f != null && !f.equals(classFile) && f.exists()) {
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
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    }
  }

  @NotNull
  public static String[] collectResourceDirs(AndroidFacet facet, boolean collectResCacheDirs) {
    final Project project = facet.getModule().getProject();
    final IntermediateOutputCompiler pngFilesCachingCompiler =
      collectResCacheDirs ? Extensions.findExtension(Compiler.EP_NAME, project, AndroidPngFilesCachingCompiler.class) : null;
    
    if (collectResCacheDirs) {
      assert pngFilesCachingCompiler != null;
    }
    
    final List<String> result = new ArrayList<String>();

    doCollectResourceDirs(facet, collectResCacheDirs, result);
    
    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
      doCollectResourceDirs(depFacet, collectResCacheDirs, result);
    }
    return ArrayUtil.toStringArray(result);
  }

  private static void doCollectResourceDirs(AndroidFacet facet, boolean collectResCacheDirs, List<String> result) {
    final Module module = facet.getModule();

    if (collectResCacheDirs) {
      final AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      final int platformToolsRevision = platform != null ? platform.getSdk().getPlatformToolsRevision() : -1;

      if (platformToolsRevision < 0 || platformToolsRevision > 7) {
        // png cache is supported since platform-tools-r8
        final String resCacheDirOsPath = findResourcesCacheDirectory(module, false, null);
        if (resCacheDirOsPath != null) {
          result.add(resCacheDirOsPath);
        }
        else {
          LOG.info("PNG cache not found for module " + module.getName());
        }
      }
    }

    final VirtualFile resourcesDir = AndroidAptCompiler.getResourceDirForApkCompiler(module, facet);
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

    final VirtualFile projectOutputDirectory = extension.getCompilerOutput();
    if (projectOutputDirectory == null) {
      if (context != null) {
        context.addMessage(CompilerMessageCategory.ERROR, "Cannot find output directory for project " + project.getName(), null, -1, -1);
      }
      return null;
    }

    final String pngCacheDirPath = projectOutputDirectory.getPath() + '/' + RESOURCES_CACHE_DIR_NAME + '/' + module.getName();
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
    RunConfiguration runConfiguration = CompileStepBeforeRun.getRunConfiguration(context);
    return !(runConfiguration instanceof JUnitConfiguration);
  }

  public static boolean isReleaseBuild(@NotNull CompileContext context) {
    final Boolean value = context.getCompileScope().getUserData(RELEASE_BUILD_KEY);
    return value != null && value.booleanValue();
  }

  public static void setReleaseBuild(@NotNull CompileScope compileScope) {
    compileScope.putUserData(RELEASE_BUILD_KEY, Boolean.TRUE);
  }

  public static void createGenModulesAndSourceRoots(@NotNull final AndroidFacet facet) {
    final Module module = facet.getModule();

    String sourceRootPath = AndroidRootUtil.getRenderscriptGenSourceRootPath(module);
    if (sourceRootPath != null) {
      createSourceRootIfNotExist(sourceRootPath, module);
    }

    sourceRootPath = facet.getAptGenSourceRootPath();
    if (sourceRootPath != null) {
      createSourceRootIfNotExist(sourceRootPath, module);
    }

    sourceRootPath = facet.getAidlGenSourceRootPath();
    if (sourceRootPath != null) {
      createSourceRootIfNotExist(sourceRootPath, module);
    }
  }
}
