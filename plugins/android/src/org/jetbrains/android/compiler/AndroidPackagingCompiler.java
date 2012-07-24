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

import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApkBuilder;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidNativeLibData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class AndroidPackagingCompiler implements PackagingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidPackagingCompiler");

  public void processOutdatedItem(CompileContext context, String url, @Nullable ValidityState state) {
  }

  @NotNull
  private static VirtualFile[] getExternalJars(@NotNull Module module,
                                               @NotNull AndroidFacetConfiguration configuration) {
    AndroidPlatform platform = configuration.getAndroidPlatform();
    if (platform != null) {
      List<VirtualFile> externalLibsAndModules = AndroidRootUtil.getExternalLibraries(module);
      return externalLibsAndModules.toArray(new VirtualFile[externalLibsAndModules.size()]);
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  private static void fillSourceRoots(@NotNull Module module,
                                      @NotNull Set<Module> visited,
                                      @NotNull Set<VirtualFile> result,
                                      boolean includingTests) {
    visited.add(module);
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    VirtualFile resDir = facet != null ? AndroidRootUtil.getResourceDir(facet) : null;
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    for (VirtualFile sourceRoot : manager.getSourceRoots(includingTests)) {
      if (!Comparing.equal(resDir, sourceRoot)) {
        result.add(sourceRoot);
      }
    }
    for (OrderEntry entry : manager.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        DependencyScope scope = moduleOrderEntry.getScope();
        if (scope == DependencyScope.COMPILE) {
          Module depModule = moduleOrderEntry.getModule();
          if (depModule != null && !visited.contains(depModule)) {
            fillSourceRoots(depModule, visited, result, false);
          }
        }
      }
    }
  }

  @NotNull
  public static VirtualFile[] getSourceRootsForModuleAndDependencies(@NotNull Module module, boolean includingTests) {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    fillSourceRoots(module, new HashSet<Module>(), result, includingTests);
    return VfsUtil.toVirtualFileArray(result);
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);
        VirtualFile[] sourceRoots = getSourceRootsForModuleAndDependencies(module, facet.getConfiguration().PACK_TEST_CODE);
        if (manifestFile != null) {
          AndroidFacetConfiguration configuration = facet.getConfiguration();
          VirtualFile outputDir = AndroidDexCompiler.getOutputDirectoryForDex(module);
          if (outputDir != null) {
            VirtualFile[] externalJars = getExternalJars(module, configuration);

            File resPackage = AndroidResourcesPackagingCompiler.getOutputFile(module, outputDir);
            String resPackagePath = FileUtil.toSystemDependentName(resPackage.getPath());

            File classesDexFile = new File(outputDir.getPath(), AndroidCommonUtils.CLASSES_FILE_NAME);
            String classesDexPath = FileUtil.toSystemDependentName(classesDexFile.getPath());

            AndroidPlatform platform = configuration.getAndroidPlatform();
            if (platform == null) {
              context.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
              continue;
            }
            String sdkPath = platform.getSdkData().getLocation();
            String outputPath = AndroidRootUtil.getApkPath(facet);
            if (outputPath == null) {
              context.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.apk.path.not.specified", module.getName()), null, -1, -1);
              continue;
            }
            final String keystorePath = FileUtil.toSystemDependentName(VfsUtil.urlToPath(configuration.CUSTOM_DEBUG_KEYSTORE_PATH));
            items.add(
              createItem(module, facet, manifestFile, sourceRoots, externalJars, resPackagePath, classesDexPath, sdkPath, outputPath,
                         configuration.GENERATE_UNSIGNED_APK, AndroidCompileUtil.isReleaseBuild(context), keystorePath));
          }
        }
      }
    }
    return items.toArray(new ProcessingItem[items.size()]);
  }

  private static AptPackagingItem createItem(Module module,
                                             AndroidFacet facet,
                                             VirtualFile manifestFile,
                                             VirtualFile[] sourceRoots,
                                             VirtualFile[] externalJars,
                                             String resPackagePath,
                                             String classesDexPath,
                                             String sdkPath,
                                             String outputPath,
                                             boolean generateSignedApk,
                                             boolean releaseBuild,
                                             String customKeystorePath) {
    final AptPackagingItem item =
      new AptPackagingItem(sdkPath, manifestFile, resPackagePath, outputPath, generateSignedApk, releaseBuild, module, customKeystorePath,
                           facet.getConfiguration().getAdditionalNativeLibraries());
    item.setNativeLibsFolders(collectNativeLibsFolders(facet));
    item.setClassesDexPath(classesDexPath);
    item.setSourceRoots(sourceRoots);
    item.setExternalLibraries(externalJars);
    return item;
  }

  @NotNull
  private static VirtualFile[] collectNativeLibsFolders(AndroidFacet facet) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    VirtualFile libsDir = AndroidRootUtil.getLibsDir(facet);
    if (libsDir != null) {
      result.add(libsDir);
    }
    for (AndroidFacet depFacet : AndroidUtils.getAndroidLibraryDependencies(facet.getModule())) {
      VirtualFile depLibsDir = AndroidRootUtil.getLibsDir(depFacet);
      if (depLibsDir != null) {
        result.add(depLibsDir);
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }

  private static String[] getPaths(@NotNull VirtualFile[] vFiles) {
    String[] result = new String[vFiles.length];
    for (int i = 0; i < vFiles.length; i++) {
      VirtualFile vFile = vFiles[i];
      result[i] = FileUtil.toSystemDependentName(vFile.getPath());
    }
    return result;
  }

  public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items) {
    if (!AndroidCompileUtil.isFullBuild(context)) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText("Building Android package...");
    final List<ProcessingItem> result = new ArrayList<ProcessingItem>();
    for (ProcessingItem processingItem : items) {
      AptPackagingItem item = (AptPackagingItem)processingItem;

      if (!AndroidCompileUtil.isModuleAffected(context, item.myModule)) {
        continue;
      }

      doProcess(context, item, false);

      if (context.getMessages(CompilerMessageCategory.ERROR).length == 0) {
        doProcess(context, item, true);
      }

      if (context.getMessages(CompilerMessageCategory.ERROR).length == 0) {
        result.add(item);
      }
    }
    return result.toArray(new ProcessingItem[result.size()]);
  }

  private static void doProcess(final CompileContext context, AptPackagingItem item, boolean unsigned) {
    if (!shouldGenerateApk(item.myModule, context, unsigned)) {
      return;
    }

    try {
      final String[] externalLibPaths = getPaths(item.getExternalLibraries());

      final String resPackagePath = unsigned
                                    ? item.getResPackagePath() + AndroidResourcesPackagingCompiler.RELEASE_SUFFIX
                                    : item.getResPackagePath();

      final String finalPath = unsigned
                               ? AndroidCommonUtils.addSuffixToFileName(item.getFinalPath(), AndroidCompileUtil.UNSIGNED_SUFFIX)
                               : item.getFinalPath();

      final String[] sourceRoots = AndroidCompileUtil.toOsPaths(item.getSourceRoots());
      final String[] nativeLibsFolders = AndroidCompileUtil.toOsPaths(item.getNativeLibsFolders());
      final Project project = context.getProject();

      final Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
        AndroidApkBuilder.execute(resPackagePath, item.getClassesDexPath(), sourceRoots, externalLibPaths, nativeLibsFolders,
                                  item.getAdditionalNativeLibs(), finalPath, unsigned, item.mySdkPath, item.getCustomKeystorePath(),
                                  new ExcludedSourcesFilter(project)));

      if (messages.get(CompilerMessageCategory.ERROR).size() == 0) {
        if (item.myReleaseBuild == unsigned) {
          final File dst = new File(
            AndroidCommonUtils.addSuffixToFileName(item.getFinalPath(), AndroidCommonUtils.ANDROID_FINAL_PACKAGE_FOR_ARTIFACT_SUFFIX));
          FileUtil.copy(new File(finalPath), dst);
          CompilerUtil.refreshIOFile(dst);
          final VirtualFile jar = JarFileSystem.getInstance().refreshAndFindFileByPath(dst.getPath() + "!/");
          if (jar != null) {
            jar.refresh(false, true);
          }
        }
      }
      AndroidCompileUtil.addMessages(context, messages, item.myModule);
    }
    catch (final IOException e) {
      LOG.info(e);
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (context.getProject().isDisposed()) return;
          context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
        }
      });
    }
  }

  public static boolean shouldGenerateApk(Module module, CompileContext context, boolean unsigned) {
    final boolean releaseBuild = AndroidCompileUtil.isReleaseBuild(context);

    if (!unsigned) {
      return !releaseBuild;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return true;
    }

    if (releaseBuild) {
      return true;
    }

    if (facet.getConfiguration().GENERATE_UNSIGNED_APK) {
      return true;
    }

    return false;
  }

  @NotNull
  public String getDescription() {
    return "Android Packaging Compiler";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInput is) throws IOException {
    return new MyValidityState(is);
  }

  private static class AptPackagingItem implements ProcessingItem {
    private final String mySdkPath;
    private final VirtualFile myManifestFile;
    private final String myResPackagePath;
    private final String myFinalPath;
    private String myClassesDexPath;
    private VirtualFile[] myNativeLibsFolders;
    private final List<AndroidNativeLibData> myAdditionalNativeLibs;
    private VirtualFile[] mySourceRoots;
    private VirtualFile[] myExternalLibraries;
    private final boolean myGenerateUnsigendApk;
    private final Module myModule;
    private boolean myReleaseBuild;

    private final String myCustomKeystorePath;

    private AptPackagingItem(String sdkPath,
                             @NotNull VirtualFile manifestFile,
                             @NotNull String resPackagePath,
                             @NotNull String finalPath,
                             boolean generateUnsigendApk,
                             boolean releaseBuild,
                             @NotNull Module module,
                             @Nullable String customKeystorePath,
                             @NotNull List<AndroidNativeLibData> additionalNativeLibs) {
      mySdkPath = sdkPath;
      myManifestFile = manifestFile;
      myResPackagePath = resPackagePath;
      myFinalPath = finalPath;
      myGenerateUnsigendApk = generateUnsigendApk;
      myReleaseBuild = releaseBuild;
      myModule = module;
      myCustomKeystorePath = customKeystorePath;
      myAdditionalNativeLibs = additionalNativeLibs;
    }

    @NotNull
    public String getResPackagePath() {
      return myResPackagePath;
    }

    @NotNull
    public String getFinalPath() {
      return myFinalPath;
    }

    @NotNull
    public String getClassesDexPath() {
      return myClassesDexPath;
    }

    @Nullable
    public String getCustomKeystorePath() {
      return myCustomKeystorePath;
    }

    @NotNull
    public VirtualFile[] getNativeLibsFolders() {
      return myNativeLibsFolders;
    }

    @NotNull
    public List<AndroidNativeLibData> getAdditionalNativeLibs() {
      return myAdditionalNativeLibs;
    }

    @NotNull
    public VirtualFile[] getSourceRoots() {
      return mySourceRoots;
    }

    @NotNull
    public VirtualFile[] getExternalLibraries() {
      return myExternalLibraries;
    }

    public void setSourceRoots(@NotNull VirtualFile[] sourceRoots) {
      this.mySourceRoots = sourceRoots;
    }

    public void setExternalLibraries(@NotNull VirtualFile[] externalLibraries) {
      this.myExternalLibraries = externalLibraries;
    }

    public void setClassesDexPath(@NotNull String classesDexPath) {
      myClassesDexPath = classesDexPath;
    }

    public void setNativeLibsFolders(@NotNull VirtualFile[] nativeLibsFolders) {
      myNativeLibsFolders = nativeLibsFolders;
    }

    @NotNull
    public VirtualFile getFile() {
      return myManifestFile;
    }

    @Nullable
    public ValidityState getValidityState() {
      return new MyValidityState(myModule.getProject(), myResPackagePath, myClassesDexPath, myFinalPath, myGenerateUnsigendApk,
                                 myReleaseBuild, mySourceRoots, myExternalLibraries, myNativeLibsFolders, myCustomKeystorePath,
                                 myAdditionalNativeLibs);
    }
  }

  private static class MyValidityState implements ValidityState {
    private final Map<String, Long> myResourceTimestamps = new HashMap<String, Long>();
    private final Map<AndroidNativeLibData, Long> myAdditionalNativeLibs = new HashMap<AndroidNativeLibData, Long>();
    private final String myApkPath;
    private final boolean myGenerateUnsignedApk;
    private final boolean myReleaseBuild;
    private final String myCustomKeystorePath;

    MyValidityState(DataInput is) throws IOException {
      int size = is.readInt();
      for (int i = 0; i < size; i++) {
        String key = is.readUTF();
        long value = is.readLong();
        myResourceTimestamps.put(key, value);
      }
      myGenerateUnsignedApk = is.readBoolean();
      myReleaseBuild = is.readBoolean();
      myApkPath = is.readUTF();
      myCustomKeystorePath = CompilerIOUtil.readString(is);

      size = is.readInt();
      for (int i = 0; i < size; i++) {
        final String architecture = is.readUTF();
        final String path = is.readUTF();
        final String targetFileName = is.readUTF();
        final long timestamp = is.readLong();
        myAdditionalNativeLibs.put(new AndroidNativeLibData(architecture, path, targetFileName), timestamp);
      }
    }

    MyValidityState(Project project,
                    String resPackagePath,
                    String classesDexPath,
                    String apkPath,
                    boolean generateUnsignedApk,
                    boolean releaseBuild,
                    VirtualFile[] sourceRoots,
                    VirtualFile[] externalLibs,
                    VirtualFile[] nativeLibFolders,
                    String customKeystorePath,
                    List<AndroidNativeLibData> additionalNativeLibs) {
      myResourceTimestamps.put(FileUtil.toSystemIndependentName(resPackagePath), new File(resPackagePath).lastModified());
      myResourceTimestamps.put(FileUtil.toSystemIndependentName(classesDexPath), new File(classesDexPath).lastModified());
      myApkPath = apkPath;
      myGenerateUnsignedApk = generateUnsignedApk;
      myReleaseBuild = releaseBuild;
      myCustomKeystorePath = customKeystorePath != null ? customKeystorePath : "";

      final HashSet<File> resourcesFromSourceRoot = new HashSet<File>();
      for (VirtualFile sourceRoot : sourceRoots) {
        AndroidApkBuilder.collectStandardSourceFolderResources(new File(sourceRoot.getPath()), resourcesFromSourceRoot,
                                                               new ExcludedSourcesFilter(project));
      }
      for (File resource : resourcesFromSourceRoot) {
        myResourceTimestamps.put(FileUtil.toSystemIndependentName(resource.getPath()), resource.lastModified());
      }

      for (VirtualFile externalLib : externalLibs) {
        myResourceTimestamps.put(externalLib.getPath(), externalLib.getTimeStamp());
      }
      ArrayList<File> nativeLibs = new ArrayList<File>();
      for (VirtualFile nativeLibFolder : nativeLibFolders) {
        for (VirtualFile child : nativeLibFolder.getChildren()) {
          AndroidApkBuilder.collectNativeLibraries(new File(child.getPath()), nativeLibs, !releaseBuild);
        }
      }
      for (File nativeLib : nativeLibs) {
        myResourceTimestamps.put(FileUtil.toSystemIndependentName(nativeLib.getPath()), nativeLib.lastModified());
      }
      for (AndroidNativeLibData lib : additionalNativeLibs) {
        final String path = lib.getPath();
        myAdditionalNativeLibs.put(lib, new File(path).lastModified());
      }
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      MyValidityState mvs = (MyValidityState)otherState;
      return mvs.myGenerateUnsignedApk == myGenerateUnsignedApk &&
             mvs.myReleaseBuild == myReleaseBuild &&
             mvs.myResourceTimestamps.equals(myResourceTimestamps) &&
             mvs.myApkPath.equals(myApkPath) &&
             mvs.myCustomKeystorePath.equals(myCustomKeystorePath) &&
             mvs.myAdditionalNativeLibs.equals(myAdditionalNativeLibs);
    }

    @Override
    public void save(DataOutput out) throws IOException {
      out.writeInt(myResourceTimestamps.size());
      for (Map.Entry<String, Long> entry : myResourceTimestamps.entrySet()) {
        out.writeUTF(entry.getKey());
        out.writeLong(entry.getValue());
      }
      out.writeBoolean(myGenerateUnsignedApk);
      out.writeBoolean(myReleaseBuild);
      out.writeUTF(myApkPath);
      CompilerIOUtil.writeString(myCustomKeystorePath, out);

      out.writeInt(myAdditionalNativeLibs.size());
      for (Map.Entry<AndroidNativeLibData, Long> entry : myAdditionalNativeLibs.entrySet()) {
        final AndroidNativeLibData lib = entry.getKey();
        out.writeUTF(lib.getArchitecture());
        out.writeUTF(lib.getPath());
        out.writeUTF(lib.getTargetFileName());
        out.writeLong(entry.getValue());
      }
    }
  }
}
