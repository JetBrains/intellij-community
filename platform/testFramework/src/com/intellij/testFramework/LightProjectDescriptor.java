/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IndexableFileSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.IOException;

public class LightProjectDescriptor {
  public static final LightProjectDescriptor EMPTY_PROJECT_DESCRIPTOR = new LightProjectDescriptor();

  public static final String TEST_MODULE_NAME = "light_idea_test_case";

  public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
    WriteAction.run(() -> {
      Module module = createMainModule(project);
      handler.moduleCreated(module);
      VirtualFile sourceRoot = createDirForSources(module);
      if (sourceRoot != null) {
        handler.sourceRootCreated(sourceRoot);
        createContentEntry(module, sourceRoot);
      }
    });
  }

  public void registerSdk(Disposable disposable) {
    Sdk sdk = getSdk();
    if (sdk != null) {
      registerJdk(sdk, disposable);
    }
  }

  @NotNull
  public Module createMainModule(@NotNull Project project) {
    return createModule(project, FileUtil.join(FileUtil.getTempDirectory(), TEST_MODULE_NAME + ".iml"));
  }

  protected Module createModule(@NotNull Project project, @NotNull String moduleFilePath) {
    return WriteAction.compute(() -> {
      File imlFile = new File(moduleFilePath);
      if (imlFile.exists()) {
        //temporary workaround for IDEA-147530: otherwise if someone saved module with this name before the created module will get its settings
        FileUtil.delete(imlFile);
      }
      return ModuleManager.getInstance(project).newModule(moduleFilePath, getModuleTypeId());
    });
  }

  @NotNull
  public String getModuleTypeId() {
    return EmptyModuleType.EMPTY_MODULE;
  }

  /**
   * Creates in-memory directory {@code temp:///some/path} where sources for test project will be placed.
   * Please keep in mind that this directory will be marked as "Source root". If you want to disable this
   * behaviour use {@link #markDirForSourcesAsSourceRoot()}.
   * @see #markDirForSourcesAsSourceRoot()
   */
  @Nullable
  public VirtualFile createDirForSources(@NotNull Module module) {
    return createSourceRoot(module, "src");
  }

  /**
   * Configures whether directory created by {@link #createDirForSources(Module)} should be marked as "Source root".
   * <p></p>
   * If you wonder about when this can be helpful: RubyMine does this. See this method overrides and according JavaDoc.
   */
  protected boolean markDirForSourcesAsSourceRoot() {
    return true;
  }

  protected VirtualFile createSourceRoot(@NotNull Module module, String srcPath) {
    VirtualFile dummyRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    assert dummyRoot != null;
    dummyRoot.refresh(false, false);
    VirtualFile srcRoot = doCreateSourceRoot(dummyRoot, srcPath);
    registerSourceRoot(module.getProject(), srcRoot);
    return srcRoot;
  }

  protected VirtualFile doCreateSourceRoot(VirtualFile root, String srcPath) {
    VirtualFile srcRoot;
    try {
      srcRoot = root.createChildDirectory(this, srcPath);
      cleanSourceRoot(srcRoot);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return srcRoot;
  }

  protected void registerSourceRoot(Project project, VirtualFile srcRoot) {
    IndexableFileSet indexableFileSet = new IndexableFileSet() {
      @Override
      public boolean isInSet(@NotNull VirtualFile file) {
        return file.getFileSystem() == srcRoot.getFileSystem() && project.isOpen();
      }

      @Override
      public void iterateIndexableFilesIn(@NotNull VirtualFile file, @NotNull ContentIterator iterator) {
        VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
          @Override
          public boolean visitFile(@NotNull VirtualFile file) {
            iterator.processFile(file);
            return true;
          }
        });
      }
    };
    FileBasedIndex.getInstance().registerIndexableSet(indexableFileSet, null);
    Disposer.register(project, () -> FileBasedIndex.getInstance().removeIndexableSet(indexableFileSet));
  }

  protected void createContentEntry(@NotNull Module module, @NotNull VirtualFile srcRoot) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      Sdk sdk = getSdk();
      if (sdk != null) {
        model.setSdk(sdk);
      }

      ContentEntry contentEntry = model.addContentEntry(srcRoot);
      if (markDirForSourcesAsSourceRoot()) {
        contentEntry.addSourceFolder(srcRoot, getSourceRootType());
      }

      configureModule(module, model, contentEntry);
    });
  }

  private static void registerJdk(Sdk jdk, Disposable parentDisposable) {
    WriteAction.run(() -> {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
      ((ProjectJdkTableImpl)jdkTable).addTestJdk(jdk, parentDisposable);
    });
  }

  @NotNull
  protected JpsModuleSourceRootType<?> getSourceRootType() {
    return JavaSourceRootType.SOURCE;
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

  protected void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) { }

  public interface SetupHandler {
    default void moduleCreated(@NotNull Module module) { }

    default void sourceRootCreated(@NotNull VirtualFile sourceRoot) { }
  }
}
