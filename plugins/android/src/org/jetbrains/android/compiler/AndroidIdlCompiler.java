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
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import org.jetbrains.android.compiler.tools.AndroidIdl;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.fileTypes.AndroidIdlFileType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Android IDL compiler.
 *
 * @author Alexey Efimov
 */
public class AndroidIdlCompiler implements SourceGeneratingCompiler {
  private static final GenerationItem[] EMPTY_GENERATION_ITEM_ARRAY = {};

  private final Project myProject;

  public AndroidIdlCompiler(Project project) {
    myProject = project;
  }

  public GenerationItem[] getGenerationItems(CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new PrepareAction(context));
  }

  public GenerationItem[] generate(CompileContext context, GenerationItem[] items, VirtualFile outputRootDirectory) {
    if (items != null && items.length > 0) {
      context.getProgressIndicator().setText("Generating AIDL files...");
      //Application application = ApplicationManager.getApplication();
      GenerationItem[] generationItems = /*application.runReadAction(new GenerateAction(context, items))*/ doGenerate(context, items);
      List<VirtualFile> generatedVFiles = new ArrayList<VirtualFile>();
      for (GenerationItem item : generationItems) {
        File generatedFile = ((IdlGenerationItem)item).myGeneratedFile;
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

  @NotNull
  public String getDescription() {
    return FileUtil.getNameWithoutExtension(SdkConstants.FN_AIDL);
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Nullable
  public ValidityState createValidityState(DataInput is) throws IOException {
    return null;
  }

  private final static class IdlGenerationItem implements GenerationItem {
    final Module myModule;
    final VirtualFile myFile;
    final boolean myTestSource;
    final IAndroidTarget myAndroidTarget;
    final File myGeneratedFile;
    final String myPackageName;
    final String mySourceRootPath;

    public IdlGenerationItem(@NotNull Module module,
                             @NotNull VirtualFile file,
                             @NotNull String sourceRootPath,
                             boolean testSource,
                             @NotNull IAndroidTarget androidTarget,
                             @NotNull String packageName) {
      myModule = module;
      myFile = file;
      myTestSource = testSource;
      myAndroidTarget = androidTarget;
      myPackageName = packageName;
      mySourceRootPath = sourceRootPath;
      myGeneratedFile =
        new File(sourceRootPath, packageName.replace('.', File.separatorChar) + File.separator + file.getNameWithoutExtension() + ".java");
    }

    @Nullable
    public String getPath() {
      return null;
    }

    @Nullable
    public ValidityState getValidityState() {
      return null;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean isTestSource() {
      return myTestSource;
    }
  }

  private final class PrepareAction implements Computable<GenerationItem[]> {
    private final CompileContext myContext;

    public PrepareAction(CompileContext context) {
      myContext = context;
    }

    public GenerationItem[] compute() {
      if (myContext.getProject().isDisposed()) {
        return EMPTY_GENERATION_ITEM_ARRAY;
      }
      VirtualFile[] files = myContext.getProjectCompileScope().getFiles(AndroidIdlFileType.ourFileType, false);
      List<GenerationItem> items = new ArrayList<GenerationItem>(files.length);
      for (VirtualFile file : files) {
        Module module = myContext.getModuleByFile(file);
        AndroidFacet facet = FacetManager.getInstance(module).getFacetByType(AndroidFacet.ID);
        if (facet != null) {
          IAndroidTarget target = facet.getConfiguration().getAndroidTarget();
          if (target == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR,
                                 AndroidBundle.message("android.compilation.error.specify.platform", module.getName()), null, -1, -1);
            continue;
          }
          String packageName = AndroidUtils.getPackageName(module, file);
          if (packageName == null) {
            myContext.addMessage(CompilerMessageCategory.ERROR, "Cannot compute package for file", file.getUrl(), -1, -1);
            continue;
          }

          addItem(file, facet, target, packageName, items);

          if (facet.getConfiguration().LIBRARY_PROJECT) {
            HashSet<Module> usingModules = new HashSet<Module>();
            AndroidUtils.collectModulesDependingOn(module, usingModules);

            for (Module module1 : usingModules) {
              AndroidFacet facet1 = AndroidFacet.getInstance(module1);
              if (facet1 != null) {
                addItem(file, facet1, target, packageName, items);
              }
            }
          }
        }
      }
      return items.toArray(new GenerationItem[items.size()]);
    }

    private void addItem(VirtualFile file,
                         AndroidFacet facet,
                         IAndroidTarget target,
                         String packageName,
                         List<GenerationItem> items) {
      Module module = facet.getModule();
      String sourceRootPath = facet.getAidlGenSourceRootPath();
      if (sourceRootPath == null) {
        myContext.addMessage(CompilerMessageCategory.ERROR,
                             AndroidBundle.message("android.compilation.error.apt.gen.not.specified", module.getName()), null, -1, -1);
        return;
      }
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      IdlGenerationItem generationItem =
        new IdlGenerationItem(module, file, sourceRootPath, fileIndex.isInTestSourceContent(file), target, packageName);
      if (myContext.isMake()) {
        File generatedFile = generationItem.myGeneratedFile;
        if (generatedFile == null || !generatedFile.exists() || generatedFile.lastModified() <= file.getModificationCount()) {
          AndroidCompileUtil.createSourceRootIfNotExist(sourceRootPath, module);
          items.add(generationItem);
        }
      }
      else {
        AndroidCompileUtil.createSourceRootIfNotExist(sourceRootPath, module);
        items.add(generationItem);
      }
    }
  }

  private static GenerationItem[] doGenerate(final CompileContext context, GenerationItem[] items) {
    if (context.getProject().isDisposed()) {
      return EMPTY_GENERATION_ITEM_ARRAY;
    }
    List<GenerationItem> results = new ArrayList<GenerationItem>(items.length);
    for (GenerationItem item : items) {
      if (item instanceof IdlGenerationItem) {
        final IdlGenerationItem idlItem = (IdlGenerationItem)item;

        if (!AndroidCompileUtil.isModuleAffected(context, idlItem.myModule)) {
          continue;
        }

        try {
          VirtualFile[] sourceRoots = AndroidPackagingCompiler.getSourceRootsForModuleAndDependencies(idlItem.myModule);
          final Map<CompilerMessageCategory, List<String>> messages = AndroidIdl
            .execute(idlItem.myAndroidTarget, idlItem.myFile.getPath(), idlItem.myGeneratedFile.getPath(), sourceRoots);
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (context.getProject().isDisposed()) return;
              addMessages(context, messages, idlItem.myFile.getUrl());
            }
          });
          if (messages.get(CompilerMessageCategory.ERROR).isEmpty()) {
            results.add(idlItem);
          }
          if (idlItem.myGeneratedFile.exists()) {
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                if (idlItem.myModule.getProject().isDisposed()) return;
                String className = FileUtil.getNameWithoutExtension(idlItem.myGeneratedFile);
                AndroidCompileUtil.removeDuplicatingClasses(idlItem.myModule, idlItem.myPackageName, className,
                                                            idlItem.myGeneratedFile.exists() ? idlItem.myGeneratedFile : null,
                                                            idlItem.mySourceRootPath);
              }
            });
          }
        }
        catch (final IOException e) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              if (context.getProject().isDisposed()) return;
              context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), idlItem.myFile.getUrl(), -1, -1);
            }
          });
        }
      }
    }
    return results.toArray(new GenerationItem[results.size()]);
  }

  private static void addMessages(CompileContext context, Map<CompilerMessageCategory, List<String>> messages, String url) {
    for (CompilerMessageCategory category : messages.keySet()) {
      List<String> messageList = messages.get(category);
      for (String message : messageList) {
        context.addMessage(category, message, url, -1, -1);
      }
    }
  }
}