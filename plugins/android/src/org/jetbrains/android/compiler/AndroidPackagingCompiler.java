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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApkBuilder;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
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

  public static final String UNSIGNED_SUFFIX = ".unsigned";

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

  private static void fillSourceRoots(@NotNull Module module, @NotNull Set<Module> visited, @NotNull Set<VirtualFile> result) {
    visited.add(module);
    VirtualFile resDir = AndroidRootUtil.getResourceDir(module);
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    for (VirtualFile sourceRoot : manager.getSourceRoots()) {
      if (resDir != sourceRoot) {
        result.add(sourceRoot);
      }
    }
    for (OrderEntry entry : manager.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)entry;
        DependencyScope scope = moduleOrderEntry.getScope();
        if (scope == DependencyScope.COMPILE || scope == DependencyScope.TEST) {
          Module depModule = moduleOrderEntry.getModule();
          if (depModule != null && !visited.contains(depModule)) {
            fillSourceRoots(depModule, visited, result);
          }
        }
      }
    }
  }

  @NotNull
  public static VirtualFile[] getSourceRootsForModuleAndDependencies(@NotNull Module module) {
    Set<VirtualFile> result = new HashSet<VirtualFile>();
    fillSourceRoots(module, new HashSet<Module>(), result);
    return VfsUtil.toVirtualFileArray(result);
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);
        VirtualFile[] sourceRoots = getSourceRootsForModuleAndDependencies(module);
        if (manifestFile != null) {
          AndroidFacetConfiguration configuration = facet.getConfiguration();
          VirtualFile outputDir = AndroidDexCompiler.getOutputDirectoryForDex(module);
          if (outputDir != null) {
            VirtualFile[] externalJars = getExternalJars(module, configuration);

            File resPackage = AndroidResourcesPackagingCompiler.getOutputFile(module, outputDir);
            String resPackagePath = FileUtil.toSystemDependentName(resPackage.getPath());

            File classesDexFile = new File(outputDir.getPath(), AndroidUtils.CLASSES_FILE_NAME);
            String classesDexPath = FileUtil.toSystemDependentName(classesDexFile.getPath());

            AndroidPlatform platform = configuration.getAndroidPlatform();
            if (platform == null) {
              context.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
              continue;
            }
            String sdkPath = platform.getSdk().getLocation();
            String outputPath = facet.getApkPath();
            if (outputPath == null) {
              context.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.apk.path.not.specified", module.getName()), null, -1, -1);
              continue;
            }
            items.add(createItem(module, facet, manifestFile, sourceRoots, externalJars, resPackagePath, classesDexPath, sdkPath,
                                 outputPath, configuration.GENERATE_UNSIGNED_APK, AndroidCompileUtil.isReleaseBuild(context)));
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
                                             boolean releaseBuild) {
    AptPackagingItem item =
      new AptPackagingItem(sdkPath, manifestFile, resPackagePath, outputPath, generateSignedApk, releaseBuild, module);
    item.setNativeLibsFolders(collectNativeLibsFolders(facet));
    item.setClassesDexPath(classesDexPath);
    item.setSourceRoots(sourceRoots);
    item.setExternalLibraries(externalJars);
    return item;
  }

  @NotNull
  private static VirtualFile[] collectNativeLibsFolders(AndroidFacet facet) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    Module module = facet.getModule();
    VirtualFile libsDir = AndroidRootUtil.getLibsDir(module);
    if (libsDir != null) {
      result.add(libsDir);
    }
    for (AndroidFacet depFacet : AndroidUtils.getAndroidDependencies(module, true)) {
      VirtualFile depLibsDir = AndroidRootUtil.getLibsDir(depFacet.getModule());
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
                               ? item.getFinalPath() + UNSIGNED_SUFFIX
                               : item.getFinalPath();

      final Map<CompilerMessageCategory, List<String>> messages = AndroidApkBuilder
        .execute(item.mySdkPath,
                 resPackagePath,
                 item.getClassesDexPath(),
                 item.getSourceRoots(),
                 externalLibPaths,
                 item.getNativeLibsFolders(),
                 finalPath,
                 unsigned);
      AndroidCompileUtil.addMessages(context, messages);
    }
    catch (final IOException e) {
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
    private VirtualFile[] mySourceRoots;
    private VirtualFile[] myExternalLibraries;
    private final boolean myGenerateUnsigendApk;
    private final Module myModule;
    private boolean myReleaseBuild;

    private AptPackagingItem(String sdkPath,
                             @NotNull VirtualFile manifestFile,
                             @NotNull String resPackagePath,
                             @NotNull String finalPath,
                             boolean generateUnsigendApk,
                             boolean releaseBuild,
                             @NotNull Module module) {
      mySdkPath = sdkPath;
      myManifestFile = manifestFile;
      myResPackagePath = resPackagePath;
      myFinalPath = finalPath;
      myGenerateUnsigendApk = generateUnsigendApk;
      myReleaseBuild = releaseBuild;
      myModule = module;
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

    @NotNull
    public VirtualFile[] getNativeLibsFolders() {
      return myNativeLibsFolders;
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
      return new MyValidityState(myManifestFile, myResPackagePath, myClassesDexPath, myFinalPath, myGenerateUnsigendApk, myReleaseBuild,
                                 mySourceRoots, myExternalLibraries, myNativeLibsFolders);
    }
  }

  private static class MyValidityState implements ValidityState {
    private final Map<String, Long> myResourceTimestamps = new HashMap<String, Long>();
    private final String myApkPath;
    private final boolean myGenerateUnsignedApk;
    private final boolean myReleaseBuild;

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
    }

    MyValidityState(VirtualFile manifestFile,
                    String resPackagePath,
                    String classesDexPath,
                    String apkPath,
                    boolean generateUnsignedApk,
                    boolean releaseBuild,
                    VirtualFile[] sourceRoots,
                    VirtualFile[] externalLibs,
                    VirtualFile[] nativeLibFolders) {
      //myResourceTimestamps.put(manifestFile.getPath(), manifestFile.getTimeStamp());
      myResourceTimestamps.put(FileUtil.toSystemIndependentName(resPackagePath), new File(resPackagePath).lastModified());
      myResourceTimestamps.put(FileUtil.toSystemIndependentName(classesDexPath), new File(classesDexPath).lastModified());
      myApkPath = apkPath;
      myGenerateUnsignedApk = generateUnsignedApk;
      myReleaseBuild = releaseBuild;
      for (VirtualFile sourceRoot : sourceRoots) {
        myResourceTimestamps.put(sourceRoot.getPath(), sourceRoot.getTimeStamp());
      }
      for (VirtualFile externalLib : externalLibs) {
        myResourceTimestamps.put(externalLib.getPath(), externalLib.getTimeStamp());
      }
      ArrayList<VirtualFile> nativeLibs = new ArrayList<VirtualFile>();
      for (VirtualFile nativeLibFolder : nativeLibFolders) {
        for (VirtualFile child : nativeLibFolder.getChildren()) {
          AndroidApkBuilder.collectNativeLibraries(child, nativeLibs);
        }
      }
      for (VirtualFile nativeLib : nativeLibs) {
        myResourceTimestamps.put(nativeLib.getPath(), nativeLib.getTimeStamp());
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
             mvs.myApkPath.equals(myApkPath);
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
    }
  }
}
