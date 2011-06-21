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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.facet.AndroidFacet;
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
 * @author Eugene.Kudelevsky
 */
public class AndroidIncludingCompiler implements SourceGeneratingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.compiler.AndroidIncludingCompiler");

  @Nullable
  @Override
  public VirtualFile getPresentableFile(CompileContext context, Module module, VirtualFile outputRoot, VirtualFile generatedFile) {
    String generatedFileRelativePath = VfsUtil.getRelativePath(generatedFile, outputRoot, '/');
    if (generatedFileRelativePath == null) {
      return null;
    }

    for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
      String genSrcRootPath = depFacet.getAptGenSourceRootPath();
      VirtualFile genSrcRoot = genSrcRootPath != null ? LocalFileSystem.getInstance().findFileByPath(genSrcRootPath) : null;
      VirtualFile[] srcRoots = ModuleRootManager.getInstance(depFacet.getModule()).getSourceRoots();

      for (VirtualFile depSourceRoot : srcRoots) {
        if (depSourceRoot != genSrcRoot) {
          VirtualFile file = depSourceRoot.findFileByRelativePath(generatedFileRelativePath);
          if (file != null) {
            return file;
          }
        }
      }
    }

    return null;
  }

  @Override
  public GenerationItem[] getGenerationItems(final CompileContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GenerationItem[]>() {
      @Override
      public GenerationItem[] compute() {
        List<MyItem> result = new ArrayList<MyItem>();
        for (Module module : context.getProjectCompileScope().getAffectedModules()) {
          Map<String, MyItem> qName2Item = new HashMap<String, MyItem>();
          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null || !facet.getConfiguration().LIBRARY_PROJECT) {
            for (AndroidFacet depFacet : AndroidUtils.getAllAndroidDependencies(module, true)) {
              String genSrcRootPath = depFacet.getAptGenSourceRootPath();
              VirtualFile genSrcRoot = genSrcRootPath != null ? LocalFileSystem.getInstance().findFileByPath(genSrcRootPath) : null;
              VirtualFile[] srcRoots = ModuleRootManager.getInstance(depFacet.getModule()).getSourceRoots();

              for (VirtualFile depSourceRoot : srcRoots) {
                if (depSourceRoot != genSrcRoot) {
                  collectCompilableFiles(module, depFacet.getModule(), context, depSourceRoot, qName2Item);
                }
              }
            }
          }
          result.addAll(qName2Item.values());
        }
        return result.toArray(new MyItem[result.size()]);
      }
    });
  }

  private static void collectCompilableFiles(final Module module,
                                             final Module depModule,
                                             final CompileContext context,
                                             @NotNull final VirtualFile sourceRoot,
                                             final Map<String, MyItem> relativePath2GenItem) {

    ModuleFileIndex fileIndex = ModuleRootManager.getInstance(depModule).getFileIndex();

    fileIndex.iterateContentUnderDirectory(sourceRoot, new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        if (fileOrDir.isDirectory()) {
          return true;
        }
        if (fileOrDir.getFileType() == StdFileTypes.JAVA) {
          String relativePath = VfsUtil.getRelativePath(fileOrDir, sourceRoot, '/');
          LOG.assertTrue(relativePath != null);
          MyItem existingItem = relativePath2GenItem.get(relativePath);
          if (existingItem != null) {
            String path1 = FileUtil.toSystemDependentName(existingItem.mySourceFile.getPath());
            String path2 = FileUtil.toSystemDependentName(fileOrDir.getPath());
            context.addMessage(CompilerMessageCategory.ERROR, "Duplicate file for " + relativePath +
                                                              "\nOrigin 1: " + path1 +
                                                              "\nOrigin 2: " + path2, null, -1, -1);
            return false;
          }
          relativePath2GenItem.put(relativePath, new MyItem(module, fileOrDir, relativePath));
        }
        return true;
      }
    });
  }

  @Override
  public GenerationItem[] generate(CompileContext context, GenerationItem[] items, VirtualFile outputRootDirectory) {
    if (items.length > 0) {
      context.getProgressIndicator().setText(AndroidBundle.message("android.compile.messages.copying.sources.from.libraries"));
    }
    List<GenerationItem> result = new ArrayList<GenerationItem>();
    for (GenerationItem item : items) {

      if (!AndroidCompileUtil.isModuleAffected(context, ((MyItem)item).myModule)) {
        continue;
      }

      String fromPath = ((MyItem)item).mySourceFile.getPath();
      File from = new File(fromPath);
      File to = new File(outputRootDirectory.getPath() + '/' + item.getPath());
      try {
        FileUtil.copy(from, to);
        result.add(item);
      }
      catch (IOException e) {
        LOG.info(e);
        String message = "Cannot copy file " + from.getPath() + " to " + to.getPath() + "\nI/O error" +
                         (e.getMessage() != null ? ": " + e.getMessage() : "");
        context.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      }
    }
    return result.toArray(new GenerationItem[result.size()]);
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Android Including Compiler";
  }

  @Override
  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  @Override
  public ValidityState createValidityState(DataInput in) throws IOException {
    return TimestampValidityState.load(in);
  }

  private static class MyItem implements GenerationItem {
    final Module myModule;
    final VirtualFile mySourceFile;
    final String mySourceRelativePath;

    private MyItem(Module module, VirtualFile sourceFile, String sourceRelativePath) {
      myModule = module;
      mySourceFile = sourceFile;
      mySourceRelativePath = sourceRelativePath;
    }

    @Override
    public String getPath() {
      return mySourceRelativePath;
    }

    @Nullable
    @Override
    public ValidityState getValidityState() {
      return new TimestampValidityState(mySourceFile.getTimeStamp());
    }

    @Override
    public Module getModule() {
      return myModule;
    }

    @Override
    public boolean isTestSource() {
      return false;
    }
  }
}
