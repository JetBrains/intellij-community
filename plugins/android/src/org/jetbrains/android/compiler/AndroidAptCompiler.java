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
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Apt compiler.
 *
 * @author Alexey Efimov
 */
public class AndroidAptCompiler implements SourceGeneratingCompiler {
  private static final GenerationItem[] EMPTY_GENERATION_ITEM_ARRAY = {};

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
      Computable<GenerationItem[]> computation = new Computable<GenerationItem[]>() {
        public GenerationItem[] compute() {
          if (context.getProject().isDisposed()) {
            return EMPTY_GENERATION_ITEM_ARRAY;
          }
          return doGenerate(context, items);
        }
      };
      GenerationItem[] generationItems = computation.compute();
      List<VirtualFile> generatedVFiles = new ArrayList<VirtualFile>();
      for (GenerationItem item : generationItems) {
        File generatedFile = ((AptGenerationItem)item).myGeneratedFile;
        if (generatedFile != null) {
          CompilerUtil.refreshIOFile(generatedFile);
          VirtualFile generatedVFile = LocalFileSystem.getInstance().findFileByIoFile(generatedFile);
          if (generatedVFile != null) {
            generatedVFiles.add(generatedVFile);
          }
        }
      }
      if (context instanceof CompileContextEx) {
        ((CompileContextEx)context).markGenerated(generatedVFiles);
      }
      return generationItems;
    }
    return EMPTY_GENERATION_ITEM_ARRAY;
  }

  private static GenerationItem[] doGenerate(final CompileContext context, GenerationItem[] items) {
    List<GenerationItem> results = new ArrayList<GenerationItem>(items.length);
    for (GenerationItem item : items) {
      if (item instanceof AptGenerationItem) {
        final AptGenerationItem aptItem = (AptGenerationItem)item;

        if (!AndroidCompileUtil.isModuleAffected(context, aptItem.myModule)) {
          continue;
        }

        try {
          Map<CompilerMessageCategory, List<String>> messages = AndroidApt
            .compile(aptItem.myAndroidTarget, aptItem.myManifestFile.getPath(), aptItem.mySourceRootPath, aptItem.myResourcesPaths,
                     aptItem.myAssetsPath, aptItem.myCustomPackage ? aptItem.myPackage : null
            );
          AndroidCompileUtil.addMessages(context, messages);
          if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
            results.add(aptItem);
          }
          if (aptItem.myGeneratedFile.exists()) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                if (context.getProject().isDisposed()) {
                  return;
                }
                String className = FileUtil.getNameWithoutExtension(aptItem.myGeneratedFile);
                AndroidCompileUtil.removeDuplicatingClasses(aptItem.myModule, aptItem.myPackage, className, aptItem.myGeneratedFile,
                                                            aptItem.mySourceRootPath);
              }
            });
          }
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
    final String myAssetsPath;
    final String mySourceRootPath;
    final IAndroidTarget myAndroidTarget;
    final File myGeneratedFile;
    final String myPackage;
    final boolean myCustomPackage;

    private AptGenerationItem(@NotNull Module module,
                              @NotNull VirtualFile manifestFile,
                              @NotNull String[] resourcesPaths,
                              @Nullable String assetsPath,
                              @NotNull String sourceRootPath,
                              @NotNull IAndroidTarget target,
                              @NotNull String aPackage,
                              boolean customPackage) {
      myModule = module;
      myManifestFile = manifestFile;
      myResourcesPaths = resourcesPaths;
      myAssetsPath = assetsPath;
      mySourceRootPath = sourceRootPath;
      myAndroidTarget = target;
      myPackage = aPackage;
      myCustomPackage = customPackage;
      myGeneratedFile =
        new File(sourceRootPath, aPackage.replace('.', File.separatorChar) + File.separator + AndroidUtils.R_JAVA_FILENAME);
    }

    public File getGeneratedFile() {
      return myGeneratedFile;
    }

    public String getPath() {
      return myPackage.replace('.', '/') + '/' + AndroidUtils.R_JAVA_FILENAME;
    }

    public ValidityState getValidityState() {
      return new MyValidityState(myModule);
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

          IAndroidTarget target = configuration.getAndroidTarget();
          if (target == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          String[] resPaths = AndroidCompileUtil.collectResourceDirs(facet);
          if (resPaths.length <= 0) {
            continue;
          }

          VirtualFile assetsDir = !configuration.LIBRARY_PROJECT ? AndroidRootUtil.getAssetsDir(module) : null;

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
                                 AndroidBundle.message("android.compilation.error.apt.gen.not.specified", module.getName()), null, -1, -1);
            continue;
          }
          AndroidCompileUtil.createSourceRootIfNotExist(sourceRootPath, module);
          String assetsDirPath = assetsDir != null ? assetsDir.getPath() : null;

          items.add(new AptGenerationItem(module, manifestFile, resPaths, assetsDirPath, sourceRootPath, target,
                                        packageName, false));

          for (String libPackage : AndroidUtils.getDepLibsPackages(module)) {
            items.add(new AptGenerationItem(module, manifestFile, resPaths, assetsDirPath, sourceRootPath, target,
                                            libPackage, true));
          }
        }
      }
      return items.toArray(new GenerationItem[items.size()]);
    }
  }

  private static class MyValidityState extends ResourcesValidityState {
    private final String myCustomGenPathR;

    MyValidityState(Module module) {
      super(module);
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
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      if (!super.equalsTo(otherState)) {
        return false;
      }
      return Comparing.equal(myCustomGenPathR, ((MyValidityState)otherState).myCustomGenPathR);
    }

    @Override
    public void save(DataOutput os) throws IOException {
      super.save(os);
      os.writeUTF(myCustomGenPathR);
    }
  }
}
