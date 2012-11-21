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

import com.android.sdklib.IAndroidTarget;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesPackagingCompiler implements ClassPostProcessingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidResourcesPackagingCompiler");

  public static final String RELEASE_SUFFIX = ".release";

  @NotNull
  @Override
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);

        final ArrayList<String> assetDirPathsList = new ArrayList<String>();
        collectAssetDirs(facet, assetDirPathsList);
        final String[] assetDirPaths = ArrayUtil.toStringArray(assetDirPathsList);
        
        if (manifestFile == null) {
          context.addMessage(CompilerMessageCategory.ERROR,
                             AndroidBundle.message("android.compilation.error.manifest.not.found", module.getName()), null, -1, -1);
          continue;
        }
        AndroidFacetConfiguration configuration = facet.getConfiguration();
        VirtualFile outputDir = AndroidDexCompiler.getOutputDirectoryForDex(module);
        if (outputDir != null) {
          String outputPath = getOutputFile(module, outputDir).getPath();
          
          final AndroidPlatform platform = configuration.getAndroidPlatform();
          
          if (platform != null) {
            String[] resourcesDirPaths = AndroidCompileUtil.collectResourceDirs(facet, true, context);
            final IAndroidTarget target = platform.getTarget();
            final int platformToolsRevision = platform.getSdkData().getPlatformToolsRevision();

            if (resourcesDirPaths.length == 0) {
              context.addMessage(CompilerMessageCategory.WARNING, "Resource directory not found for module " + module.getName(),
                                 null, -1, -1);
            }

            items.add(new MyItem(module, target, platformToolsRevision, manifestFile, resourcesDirPaths, assetDirPaths, outputPath,
                                 configuration.GENERATE_UNSIGNED_APK, AndroidCompileUtil.isReleaseBuild(context)));
            //items.add(new MyItem(module, target, manifestFile, resourcesDirPaths, assetsDirPath, outputPath + RELEASE_SUFFIX, true));
          }
        }
      }
    }
    return items.toArray(new ProcessingItem[items.size()]);
  }
  
  private static void collectAssetDirs(@NotNull AndroidFacet facet, @NotNull List<String> result) {
    final VirtualFile assetsDir = AndroidRootUtil.getAssetsDir(facet);
    if (assetsDir != null) {
      result.add(FileUtil.toSystemDependentName(assetsDir.getPath()));
    }
    if (facet.getConfiguration().isIncludeAssetsFromLibraries()) {
      for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(facet.getModule(), true)) {
        final VirtualFile depAssetsDir = AndroidRootUtil.getAssetsDir(depFacet);

        if (depAssetsDir != null) {
          result.add(FileUtil.toSystemDependentName(depAssetsDir.getPath()));
        }
      }
    }
  }

  static File getOutputFile(Module module, VirtualFile outputDir) {
    return new File(outputDir.getPath(), module.getName() + ".apk.res");
  }

  @Override
  public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items) {
    if (!AndroidCompileUtil.isFullBuild(context)) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    context.getProgressIndicator().setText("Packaging Android resources...");
    final List<ProcessingItem> result = new ArrayList<ProcessingItem>();
    for (ProcessingItem processingItem : items) {
      MyItem item = (MyItem)processingItem;

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

  private static void doProcess(final CompileContext context, MyItem item, boolean releasePackage) {
    if (!AndroidPackagingCompiler.shouldGenerateApk(item.myModule, context, releasePackage)) {
      return;
    }

    final VirtualFile preprocessedManifestFile;
    File manifestTmpDir = null;

    try {
      if (releasePackage) {
        preprocessedManifestFile = item.myManifestFile;
      }
      else {
        final Pair<VirtualFile, File> pair = copyManifestAndSetDebuggableToTrue(item.myModule, item.myManifestFile);
        preprocessedManifestFile = pair.getFirst();
        manifestTmpDir = pair.getSecond();
      }
    }
    catch (IOException e) {
      LOG.info(e);
      context.addMessage(CompilerMessageCategory.ERROR,
                         '[' + item.myModule.getName() + "] Cannot preprocess AndroidManifest.xml for debug build",
                         item.myManifestFile.getUrl(), -1, -1);
      return;
    }

    final Map<VirtualFile, VirtualFile> presentableFilesMap = Collections.singletonMap(item.myManifestFile, preprocessedManifestFile);

    try {
      final String outputPath = releasePackage
                                ? item.myOutputPath + RELEASE_SUFFIX
                                : item.myOutputPath;

      Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
        AndroidApt.packageResources(item.myAndroidTarget,
                                    item.myPlatformToolsRevision,
                                    preprocessedManifestFile.getPath(),
                                    item.myResourceDirPaths,
                                    item.myAssetsDirPaths,
                                    outputPath, null, !releasePackage, 0, new FileFilter() {
          @Override
          public boolean accept(File file) {
            final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
            return vFile != null && !ProjectRootManager.getInstance(context.getProject()).getFileIndex().isIgnored(vFile);
          }
        }));

      AndroidCompileUtil.addMessages(context, messages, presentableFilesMap, item.myModule);
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
    finally {
      if (manifestTmpDir != null) {
        FileUtil.delete(manifestTmpDir);
      }
    }
  }

  @NotNull
  private static Pair<VirtualFile, File> copyManifestAndSetDebuggableToTrue(@NotNull final Module module, @NotNull final VirtualFile manifestFile)
    throws IOException {

    final File dir = FileUtil.createTempDirectory("android_manifest_copy", "tmp");
    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    if (vDir == null) {
      throw new IOException("Cannot create temp directory for manifest copy");
    }

    final VirtualFile[] manifestFileCopy = new VirtualFile[1];

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                manifestFileCopy[0] = manifestFile.copy(module.getProject(), vDir, manifestFile.getName());
              }
              catch (IOException e) {
                LOG.info(e);
                return;
              }

              if (manifestFileCopy[0] == null) {
                return;
              }

              final Manifest manifestInCopy = AndroidUtils.loadDomElement(module, manifestFileCopy[0], Manifest.class);
              if (manifestInCopy == null) {
                return;
              }

              final Application applicationInCopy = manifestInCopy.getApplication();
              if (applicationInCopy == null) {
                return;
              }
              applicationInCopy.getDebuggable().setValue(Boolean.TRUE.toString());
            }
          });

          if (manifestFileCopy[0] != null) {
            EncodingManager.getInstance().setEncoding(manifestFileCopy[0], null);
          }

          ApplicationManager.getApplication().saveAll();
        }
      }, ModalityState.defaultModalityState());

    if (manifestFileCopy[0] == null) {
      FileUtil.delete(dir);
      throw new IOException("Cannot copy manifest file to " + vDir.getPath());
    }
    return Pair.create(manifestFileCopy[0], dir);
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Resources Packaging Compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return new MyValidityState(in);
  }

  private static class MyItem implements ProcessingItem {
    final Module myModule;
    final VirtualFile myManifestFile;
    final IAndroidTarget myAndroidTarget;
    final String[] myResourceDirPaths;
    final String[] myAssetsDirPaths;
    final String myOutputPath;

    private final boolean myFileExists;
    private final boolean myGenerateUnsignedApk;
    private boolean myReleaseBuild;
    final int myPlatformToolsRevision;

    private MyItem(Module module,
                   IAndroidTarget androidTarget,
                   int platformToolsRevision,
                   VirtualFile manifestFile,
                   String[] resourceDirPaths,
                   String[] assetsDirPath,
                   String outputPath,
                   boolean generateUnsignedApk,
                   boolean releaseBuild) {
      myModule = module;
      myAndroidTarget = androidTarget;
      myPlatformToolsRevision = platformToolsRevision;
      myManifestFile = manifestFile;
      myResourceDirPaths = resourceDirPaths;
      myAssetsDirPaths = assetsDirPath;
      myOutputPath = outputPath;
      myFileExists = new File(outputPath).exists();
      myGenerateUnsignedApk = generateUnsignedApk;
      myReleaseBuild = releaseBuild;
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      VirtualFile moduleFile = myModule.getModuleFile();
      return moduleFile != null ? moduleFile : myManifestFile;
    }

    @Override
    public ValidityState getValidityState() {
      return new MyValidityState(myModule, myFileExists, myGenerateUnsignedApk, myReleaseBuild, myPlatformToolsRevision);
    }
  }

  private static class MyValidityState extends ResourcesValidityState {
    private final boolean myOutputFileExists;
    private final boolean myGenerateUnsignedApk;
    private final boolean myReleaseBuild;
    private final int myPlatformToolsRevision;

    public MyValidityState(Module module,
                           boolean outputFileExists,
                           boolean generateUnsignedApk,
                           boolean releaseBuild,
                           int platformToolsRevision) {
      super(module);
      myOutputFileExists = outputFileExists;
      myGenerateUnsignedApk = generateUnsignedApk;
      myReleaseBuild = releaseBuild;
      myPlatformToolsRevision = platformToolsRevision;
    }

    public MyValidityState(DataInput is) throws IOException {
      super(is);
      myGenerateUnsignedApk = is.readBoolean();
      myReleaseBuild = is.readBoolean();
      myOutputFileExists = true;
      myPlatformToolsRevision = is.readInt();
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      final MyValidityState otherState1 = (MyValidityState)otherState;
      if (myOutputFileExists != otherState1.myOutputFileExists) {
        return false;
      }
      if (myGenerateUnsignedApk != otherState1.myGenerateUnsignedApk) {
        return false;
      }
      if (myReleaseBuild != otherState1.myReleaseBuild) {
        return false;
      }
      if (myPlatformToolsRevision != otherState1.myPlatformToolsRevision) {
        return false;
      }
      return super.equalsTo(otherState);
    }

    @Override
    public void save(DataOutput os) throws IOException {
      super.save(os);
      os.writeBoolean(myGenerateUnsignedApk);
      os.writeBoolean(myReleaseBuild);
      os.writeInt(myPlatformToolsRevision);
    }
  }
}
