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
import com.android.sdklib.SdkConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Apt compiler.
 *
 * @author Alexey Efimov
 */
public class AndroidAptCompiler implements SourceGeneratingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidAptCompiler");
  private static final GenerationItem[] EMPTY_GENERATION_ITEM_ARRAY = {};

  @Nullable
  @Override
  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    return null;
  }

  public static boolean isToCompileModule(Module module, AndroidFacetConfiguration configuration) {
    if (!(configuration.RUN_PROCESS_RESOURCES_MAVEN_TASK && AndroidMavenUtil.isMavenizedModule(module))) {
      return true;
    }
    return false;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new PrepareAction(context));
  }

  public GenerationItem[] generate(final CompileContext context, final GenerationItem[] items, VirtualFile outputRootDirectory) {
    if (items != null && items.length > 0) {
      context.getProgressIndicator().setText(AndroidBundle.message("android.compile.messages.generating.r.java"));

      if (!context.getProject().isDisposed()) {
        return doGenerate(context, items, outputRootDirectory);
      }
    }
    return EMPTY_GENERATION_ITEM_ARRAY;
  }

  private static GenerationItem[] doGenerate(final CompileContext context, GenerationItem[] items, VirtualFile outputRootDirectory) {
    List<GenerationItem> results = new ArrayList<GenerationItem>(items.length);
    for (GenerationItem item : items) {
      if (item instanceof AptGenerationItem) {
        final AptGenerationItem aptItem = (AptGenerationItem)item;

        if (!AndroidCompileUtil.isModuleAffected(context, aptItem.myModule)) {
          continue;
        }

        final String outputDirOsPath = FileUtil.toSystemDependentName(outputRootDirectory.getPath());

        try {
          Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
            AndroidApt.compile(aptItem.myAndroidTarget, aptItem.myPlatformToolsRevision,
                               aptItem.myManifestFile.getPath(), aptItem.myPackage,
                               outputDirOsPath, aptItem.myResourcesPaths,
                               aptItem.myLibraryPackages, aptItem.myIsLibrary));
          
          AndroidCompileUtil.addMessages(context, messages);
          if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
            results.add(aptItem);
          }
        }
        catch (final IOException e) {
          LOG.info(e);
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (context.getProject().isDisposed()) return;
              context.addMessage(CompilerMessageCategory.ERROR, "I/O error: " + e.getMessage(), null, -1, -1);
            }
          });
        }
      }
    }
    return results.toArray(new GenerationItem[results.size()]);
  }

  @NotNull
  public String getDescription() {
    return FileUtil.getNameWithoutExtension(SdkConstants.FN_AAPT);
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInput is) throws IOException {
    return new MyValidityState(is);
  }

  @Nullable
  public static VirtualFile getResourceDirForApkCompiler(Module module, AndroidFacet facet) {
    return facet.getConfiguration().USE_CUSTOM_APK_RESOURCE_FOLDER
           ? getCustomResourceDirForApt(facet)
           : AndroidRootUtil.getResourceDir(module);
  }

  final static class AptGenerationItem implements GenerationItem {
    final Module myModule;
    final VirtualFile myManifestFile;
    final String[] myResourcesPaths;
    final IAndroidTarget myAndroidTarget;
    
    final String myPackage;
    final String[] myLibraryPackages;
    final boolean myIsLibrary;
    
    final int myPlatformToolsRevision;

    private AptGenerationItem(@NotNull Module module,
                              @NotNull VirtualFile manifestFile,
                              @NotNull String[] resourcesPaths,
                              @NotNull IAndroidTarget target,
                              int platformToolsRevision,
                              @NotNull String aPackage,
                              @NotNull String[] libPackages,
                              boolean isLibrary) {
      myModule = module;
      myManifestFile = manifestFile;
      myResourcesPaths = resourcesPaths;
      myAndroidTarget = target;
      myPackage = aPackage;
      myLibraryPackages = libPackages;
      myIsLibrary = isLibrary;
      myPlatformToolsRevision = platformToolsRevision;
    }

    public String getPath() {
      return myPackage.replace('.', '/') + '/' + AndroidCommonUtils.R_JAVA_FILENAME;
    }

    public ValidityState getValidityState() {
      return new MyValidityState(myModule, Collections.<String>emptySet(), myPlatformToolsRevision);
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return false;
    }
  }

  @Nullable
  public static VirtualFile getCustomResourceDirForApt(@NotNull AndroidFacet facet) {
    return AndroidRootUtil.getFileByRelativeModulePath(facet.getModule(), facet.getConfiguration().CUSTOM_APK_RESOURCE_FOLDER, false);
  }

  private static final class PrepareAction implements Computable<GenerationItem[]> {
    private final CompileContext myContext;

    public PrepareAction(CompileContext context) {
      myContext = context;
    }

    public GenerationItem[] compute() {
      if (myContext.getProject().isDisposed()) {
        return EMPTY_GENERATION_ITEM_ARRAY;
      }
      Module[] modules = ModuleManager.getInstance(myContext.getProject()).getModules();
      List<GenerationItem> items = new ArrayList<GenerationItem>();
      for (Module module : modules) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          AndroidFacetConfiguration configuration = facet.getConfiguration();
          if (!isToCompileModule(module, configuration)) {
            continue;
          }

          final AndroidPlatform platform = configuration.getAndroidPlatform();
          if (platform == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          final IAndroidTarget target = platform.getTarget();
          final int platformToolsRevision = platform.getSdk().getPlatformToolsRevision();

          String[] resPaths = AndroidCompileUtil.collectResourceDirs(facet, false, myContext);
          if (resPaths.length <= 0) {
            continue;
          }

          VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);
          if (manifestFile == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR, AndroidBundle.message("android.compilation.error.manifest.not.found"),
                                 null, -1, -1);
            continue;
          }

          Manifest manifest = AndroidUtils.loadDomElement(module, manifestFile, Manifest.class);
          if (manifest == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR, "Cannot parse file", manifestFile.getUrl(), -1, -1);
            continue;
          }

          String packageName = manifest.getPackage().getValue();
          if (packageName != null) {
            packageName = packageName.trim();
          }
          if (packageName == null || packageName.length() <= 0) {
            myContext.addMessage(CompilerMessageCategory.ERROR, AndroidBundle.message("package.not.found.error"), manifestFile.getUrl(),
                                 -1, -1);
            continue;
          }
          String sourceRootPath = facet.getAptGenSourceRootPath();
          if (sourceRootPath == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.apt.gen.not.specified", module.getName()),
                                 null, -1, -1);
            continue;
          }

          final String[] libPackages = getLibPackages(module, packageName);

          items.add(new AptGenerationItem(module, manifestFile, resPaths, target, platformToolsRevision,
                                          packageName, libPackages, facet.getConfiguration().LIBRARY_PROJECT));
        }
      }
      return items.toArray(new GenerationItem[items.size()]);
    }

    @NotNull
    private static String[] getLibPackages(@NotNull Module module, @NotNull String packageName) {
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
  }

  private static class MyValidityState extends ResourceNamesValidityState {
    private final String myCustomGenPathR;
    private final Set<String> myNonExistingFiles;
    private final int myPlatformToolsRevision;

    MyValidityState(@NotNull Module module, @NotNull Set<String> nonExistingFiles, int platformToolsRevision) {
      super(module);
      myNonExistingFiles = nonExistingFiles;
      myPlatformToolsRevision = platformToolsRevision;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        myCustomGenPathR = "";
        return;
      }
      AndroidFacetConfiguration configuration = facet.getConfiguration();
      myCustomGenPathR = configuration.GEN_FOLDER_RELATIVE_PATH_APT;
    }

    public MyValidityState(DataInput is) throws IOException {
      super(is);
      String path = is.readUTF();
      myCustomGenPathR = path != null ? path : "";

      myNonExistingFiles = Collections.emptySet();
      myPlatformToolsRevision = is.readInt();
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }

      final MyValidityState otherState1 = (MyValidityState)otherState;
      if (!otherState1.myNonExistingFiles.equals(myNonExistingFiles)) {
        return false;
      }
      if (myPlatformToolsRevision != otherState1.myPlatformToolsRevision) {
        return false;
      }
      if (!super.equalsTo(otherState)) {
        return false;
      }
      return Comparing.equal(myCustomGenPathR, otherState1.myCustomGenPathR);
    }

    @Override
    public void save(DataOutput os) throws IOException {
      super.save(os);
      os.writeUTF(myCustomGenPathR);
      os.writeInt(myPlatformToolsRevision);
    }
  }
}
