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
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.tools.AndroidApt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidResourcesPackagingCompiler implements ClassPostProcessingCompiler {
  @NotNull
  @Override
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    final List<ProcessingItem> items = new ArrayList<ProcessingItem>();
    for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
        VirtualFile manifestFile = AndroidRootUtil.getManifestFileForCompiler(facet);
        VirtualFile assetsDir = AndroidRootUtil.getAssetsDir(module);
        if (manifestFile == null) {
          context.addMessage(CompilerMessageCategory.ERROR, AndroidBundle.message("android.compilation.error.manifest.not.found"),
                             null, -1, -1);
          continue;
        }
        AndroidFacetConfiguration configuration = facet.getConfiguration();
        VirtualFile outputDir = AndroidDexCompiler.getOutputDirectoryForDex(module);
        if (outputDir != null) {
          String outputPath = getOutputFile(module, outputDir).getPath();
          IAndroidTarget target = configuration.getAndroidTarget();
          if (target != null) {
            String assetsDirPath = assetsDir != null ? assetsDir.getPath() : null;
            String[] resourcesDirPaths = AndroidCompileUtil.collectResourceDirs(facet);
            if (resourcesDirPaths.length == 0) {
              context.addMessage(CompilerMessageCategory.WARNING, "Resource directory not found for module " + module.getName(),
                                 null, -1, -1);
            }
            items.add(new MyItem(module, target, manifestFile, resourcesDirPaths, assetsDirPath, outputPath));
          }
        }
      }
    }
    return items.toArray(new ProcessingItem[items.size()]);
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

      try {
        Map<CompilerMessageCategory, List<String>> messages = AndroidApt.packageResources(item.myAndroidTarget,
                                                                                          item.myManifestFile.getPath(),
                                                                                          item.myResourceDirPaths, item.myAssetsDirPath,
                                                                                          item.myOutputPath);
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
      if (context.getMessages(CompilerMessageCategory.ERROR).length == 0) {
        result.add(item);
      }
    }
    return result.toArray(new ProcessingItem[result.size()]);
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
    final String myAssetsDirPath;
    final String myOutputPath;

    private final boolean myFileExists;

    private MyItem(Module module,
                   IAndroidTarget androidTarget,
                   VirtualFile manifestFile,
                   String[] resourceDirPaths,
                   String assetsDirPath,
                   String outputPath) {
      myModule = module;
      myAndroidTarget = androidTarget;
      myManifestFile = manifestFile;
      myResourceDirPaths = resourceDirPaths;
      myAssetsDirPath = assetsDirPath;
      myOutputPath = outputPath;
      myFileExists = new File(outputPath).exists();
    }

    @NotNull
    @Override
    public VirtualFile getFile() {
      VirtualFile moduleFile = myModule.getModuleFile();
      return moduleFile != null ? moduleFile : myManifestFile;
    }

    @Override
    public ValidityState getValidityState() {
      return new MyValidityState(myModule, myFileExists);
    }
  }

  private static class MyValidityState extends ResourcesValidityState {
    private final boolean myOutputFileExists;

    public MyValidityState(Module module, boolean outputFileExists) {
      super(module);
      myOutputFileExists = outputFileExists;
    }

    public MyValidityState(DataInput is) throws IOException {
      super(is);
      myOutputFileExists = true;
    }

    @Override
    public boolean equalsTo(ValidityState otherState) {
      if (!(otherState instanceof MyValidityState)) {
        return false;
      }
      if (myOutputFileExists != ((MyValidityState)otherState).myOutputFileExists) {
        return false;
      }
      return super.equalsTo(otherState);
    }
  }
}
