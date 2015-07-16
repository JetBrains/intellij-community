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

import java.io.IOException;

import static com.intellij.openapi.roots.ModuleRootModificationUtil.updateModel;


public class LightProjectDescriptor {
  public static final LightProjectDescriptor EMPTY_PROJECT_DESCRIPTOR = new LightProjectDescriptor();

  @NotNull
  public ProjectInfo setUpProject(@NotNull Project project) throws Exception {
    Module module = createMainModule(project);
    VirtualFile contentRoot = createSourcesRoot(module);
    return new ProjectInfo(module, contentRoot);
  }

  @NotNull
  public Module createMainModule(@NotNull final Project project) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return ModuleManager.getInstance(project).newModule("light_idea_test_case.iml", getModuleType().getId());
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

    return srcRoot;
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
  
  public static class ProjectInfo {
    @NotNull public final Module module;
    @Nullable public final VirtualFile moduleSourcesRoot;

    public ProjectInfo(@NotNull Module module, @Nullable VirtualFile moduleSourcesRoot) {
      this.module = module;
      this.moduleSourcesRoot = moduleSourcesRoot;
    }
  }
}
