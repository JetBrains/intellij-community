/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexableFileSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel;


public class LightProjectDescriptor {
  public static final LightProjectDescriptor EMPTY_PROJECT_DESCRIPTOR = new LightProjectDescriptor();

  public void setUpProject(@NotNull final Project project, @NotNull final SetupHandler handler) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        Module module = createMainModule(project);
        handler.moduleCreated(module);
        VirtualFile sourceRoot = createSourcesRoot(module);
        if (sourceRoot != null) {
          handler.sourceRootCreated(sourceRoot);
          createContentEntry(module, sourceRoot);
        }
      }
    });
  }

  @NotNull
  public Module createMainModule(@NotNull final Project project) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        String moduleFilePath = "light_idea_test_case.iml";
        File imlFile = new File(moduleFilePath);
        if (imlFile.exists()) {
          //temporary workaround for IDEA-147530: otherwise if someone saved module with this name before the created module will get its settings
          FileUtil.delete(imlFile);
        }
        return ModuleManager.getInstance(project).newModule(moduleFilePath, getModuleType().getId());
      }
    });
  }

  @NotNull
  public ModuleType getModuleType() {
    return EmptyModuleType.getInstance();
  }

  @Nullable
  public VirtualFile createSourcesRoot(@NotNull final Module module) {
    VirtualFile dummyRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assert dummyRoot != null;
    dummyRoot.refresh(false, false);

    final VirtualFile srcRoot;
    try {
      srcRoot = dummyRoot.createChildDirectory(this, "src");
      cleanSourceRoot(srcRoot);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    final IndexableFileSet indexableFileSet = new IndexableFileSet() {
      @Override
      public boolean isInSet(@NotNull final VirtualFile file) {
        return file.getFileSystem() == srcRoot.getFileSystem() &&
               module.getProject().isOpen();
      }

      @Override
      public void iterateIndexableFilesIn(@NotNull final VirtualFile file, @NotNull final ContentIterator iterator) {
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            iterator.processFile(file);
            return true;
          }
        });
      }
    };
    FileBasedIndex.getInstance().registerIndexableSet(indexableFileSet, null);
    Disposer.register(module.getProject(), new Disposable() {
      @Override
      public void dispose() {
        FileBasedIndex.getInstance().removeIndexableSet(indexableFileSet);
      }
    });

    return srcRoot;
  }

  protected void createContentEntry(@NotNull final Module module, @NotNull final VirtualFile srcRoot) {
    updateModel(module, new Consumer<ModifiableRootModel>() {
      @Override
      public void consume(ModifiableRootModel model) {
        Sdk sdk = getSdk();
        if (sdk != null) {
          model.setSdk(sdk);
        }

        ContentEntry contentEntry = model.addContentEntry(srcRoot);
        contentEntry.addSourceFolder(srcRoot, false);

        configureModule(module, model, contentEntry);
      }
    });
  }

  @Nullable
  public Sdk getSdk() {
    return null;
  }

  private void cleanSourceRoot(@NotNull VirtualFile contentRoot) throws IOException {
    TempFileSystem tempFs = (TempFileSystem)contentRoot.getFileSystem();
    for (VirtualFile child : contentRoot.getChildren()) {
      if (!tempFs.exists(child)) {
        tempFs.createChildFile(this, contentRoot, child.getName());
      }
      child.delete(this);
    }
  }

  protected void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
  }
  
  public interface SetupHandler {
    void moduleCreated(@NotNull Module module);
    void sourceRootCreated(@NotNull VirtualFile sourceRoot); 
  } 
}
