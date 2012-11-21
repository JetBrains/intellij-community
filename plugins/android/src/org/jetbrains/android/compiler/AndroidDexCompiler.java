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

import com.android.SdkConstants;
import com.android.sdklib.IAndroidTarget;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidDxWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.maven.AndroidMavenProvider;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.util.*;

/**
 * Android Dex compiler.
 *
 * @author Alexey Efimov
 */
public class AndroidDexCompiler implements ClassPostProcessingCompiler {

  @NotNull
  public ProcessingItem[] getProcessingItems(CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new PrepareAction(context));
  }

  public ProcessingItem[] process(CompileContext context, ProcessingItem[] items) {
    if (!AndroidCompileUtil.isFullBuild(context)) {
      return ProcessingItem.EMPTY_ARRAY;
    }

    if (items != null && items.length > 0) {
      context.getProgressIndicator().setText("Generating " + AndroidCommonUtils.CLASSES_FILE_NAME + "...");
      return new ProcessAction(context, items).compute();
    }
    return ProcessingItem.EMPTY_ARRAY;
  }

  @NotNull
  public String getDescription() {
    return FileUtil.getNameWithoutExtension(SdkConstants.FN_DX);
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return new ClassesAndJarsValidityState(in);
  }

  public static VirtualFile getOutputDirectoryForDex(@NotNull Module module) {
    if (AndroidMavenUtil.isMavenizedModule(module)) {
      AndroidMavenProvider mavenProvider = AndroidMavenUtil.getMavenProvider();
      if (mavenProvider != null) {
        String buildDirPath = mavenProvider.getBuildDirectory(module);
        if (buildDirPath != null) {
          VirtualFile buildDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(buildDirPath));
          if (buildDir != null) {
            return buildDir;
          }
        }
      }
    }
    return CompilerModuleExtension.getInstance(module).getCompilerOutputPath();
  }

  static void addModuleOutputDir(Collection<VirtualFile> files, VirtualFile dir) {
    // only include files inside packages
    for (VirtualFile child : dir.getChildren()) {
      if (child.isDirectory()) {
        files.add(child);
      }
    }
  }

  private static final class PrepareAction implements Computable<ProcessingItem[]> {
    private final CompileContext myContext;

    public PrepareAction(CompileContext context) {
      myContext = context;
    }

    public ProcessingItem[] compute() {
      Module[] modules = ModuleManager.getInstance(myContext.getProject()).getModules();
      List<ProcessingItem> items = new ArrayList<ProcessingItem>();
      for (Module module : modules) {
        AndroidFacet facet = FacetManager.getInstance(module).getFacetByType(AndroidFacet.ID);
        if (facet != null && !facet.getConfiguration().LIBRARY_PROJECT) {
          
          final VirtualFile dexOutputDir = getOutputDirectoryForDex(module);
          
          Collection<VirtualFile> files;
            
          final boolean shouldRunProguard = AndroidCompileUtil.getProguardConfigFilePathIfShouldRun(facet, myContext) != null;

          if (shouldRunProguard) {
            final VirtualFile obfuscatedSourcesJar = dexOutputDir.findChild(AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME);
            if (obfuscatedSourcesJar == null) {
              myContext.addMessage(CompilerMessageCategory.INFORMATION, "Dex won't be launched for module " +
                                                                        module.getName() +
                                                                        " because file " +
                                                                        AndroidCommonUtils.PROGUARD_OUTPUT_JAR_NAME +
                                                                        " doesn't exist", null, -1, -1);
              continue;
            }
            
            files = Collections.singleton(obfuscatedSourcesJar);
          }
          else {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            VirtualFile outputDir = extension.getCompilerOutputPath();
            
            if (outputDir == null) {
              myContext.addMessage(CompilerMessageCategory.INFORMATION,
                                   "Dex won't be launched for module " + module.getName() + " because it doesn't contain compiled files",
                                   null, -1, -1);
              continue;
            }
            
            files = new HashSet<VirtualFile>();
            addModuleOutputDir(files, outputDir);
            files.addAll(AndroidRootUtil.getExternalLibraries(module));

            for (VirtualFile file : AndroidRootUtil.getDependentModules(module, outputDir)) {
              if (file.isDirectory()) {
                addModuleOutputDir(files, file);
              }
              else {
                files.add(file);
              }
            }

            if (facet.getConfiguration().PACK_TEST_CODE) {
              VirtualFile outputDirForTests = extension.getCompilerOutputPathForTests();

              if (outputDirForTests != null) {
                addModuleOutputDir(files, outputDirForTests);
              }
            }
          }

          final AndroidFacetConfiguration configuration = facet.getConfiguration();
          final AndroidPlatform platform = configuration.getAndroidPlatform();
          
          if (platform == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }

          items.add(new DexItem(module, dexOutputDir, platform.getTarget(), files));
        }
      }
      return items.toArray(new ProcessingItem[items.size()]);
    }
  }

  private final static class ProcessAction implements Computable<ProcessingItem[]> {
    private final CompileContext myContext;
    private final ProcessingItem[] myItems;

    public ProcessAction(CompileContext context, ProcessingItem[] items) {
      myContext = context;
      myItems = items;
    }

    public ProcessingItem[] compute() {
      List<ProcessingItem> results = new ArrayList<ProcessingItem>(myItems.length);
      for (ProcessingItem item : myItems) {
        if (item instanceof DexItem) {
          DexItem dexItem = (DexItem)item;

          if (!AndroidCompileUtil.isModuleAffected(myContext, dexItem.myModule)) {
            continue;
          }

          String outputDirPath = FileUtil.toSystemDependentName(dexItem.myClassDir.getPath());
          String[] files = new String[dexItem.myFiles.size()];
          int i = 0;
          for (VirtualFile file : dexItem.myFiles) {
            files[i++] = FileUtil.toSystemDependentName(file.getPath());
          }

          Map<CompilerMessageCategory, List<String>> messages = AndroidCompileUtil.toCompilerMessageCategoryKeys(
            AndroidDxWrapper.execute(dexItem.myModule, dexItem.myAndroidTarget, outputDirPath, files));

          addMessages(messages, dexItem.myModule);
          if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
            results.add(dexItem);
          }
        }
      }
      return results.toArray(new ProcessingItem[results.size()]);
    }

    private void addMessages(Map<CompilerMessageCategory, List<String>> messages, Module module) {
      for (CompilerMessageCategory category : messages.keySet()) {
        List<String> messageList = messages.get(category);
        for (String message : messageList) {
          myContext.addMessage(category, '[' + module.getName() + "] " + message, null, -1, -1);
        }
      }
    }
  }

  private final static class DexItem implements ProcessingItem {
    final Module myModule;
    final VirtualFile myClassDir;
    final IAndroidTarget myAndroidTarget;
    final Collection<VirtualFile> myFiles;

    public DexItem(@NotNull Module module,
                   @NotNull VirtualFile classDir,
                   @NotNull IAndroidTarget target,
                   Collection<VirtualFile> files) {
      myModule = module;
      myClassDir = classDir;
      myAndroidTarget = target;
      myFiles = files;
    }

    @NotNull
    public VirtualFile getFile() {
      return myClassDir;
    }

    @Nullable
    public ValidityState getValidityState() {
      return new ClassesAndJarsValidityState(myFiles);
    }
  }
}
