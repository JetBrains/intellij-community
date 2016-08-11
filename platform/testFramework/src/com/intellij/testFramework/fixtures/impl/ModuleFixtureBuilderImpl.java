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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
public abstract class ModuleFixtureBuilderImpl<T extends ModuleFixture> implements ModuleFixtureBuilder<T> {
  private static int ourIndex;

  private final ModuleType myModuleType;
  protected final List<String> myContentRoots = new ArrayList<>();
  protected final List<String> mySourceRoots = new ArrayList<>();
  protected final TestFixtureBuilder<? extends IdeaProjectTestFixture> myFixtureBuilder;
  private T myModuleFixture;
  protected String myOutputPath;
  protected String myTestOutputPath;

  public ModuleFixtureBuilderImpl(@NotNull final ModuleType moduleType, TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    myModuleType = moduleType;
    myFixtureBuilder = fixtureBuilder;
  }

  @Override
  public ModuleFixtureBuilder<T> addContentRoot(final String contentRootPath) {
    myContentRoots.add(contentRootPath);
    return this;
  }

  @Override
  public ModuleFixtureBuilder<T> addSourceRoot(final String sourceRootPath) {
    Assert.assertFalse("content root should be added first", myContentRoots.isEmpty());
    mySourceRoots.add(sourceRootPath);
    return this;
  }

  @Override
  public void setOutputPath(final String outputPath) {
    myOutputPath = outputPath;
  }

  @Override
  public void setTestOutputPath(String outputPath) {
    myTestOutputPath = outputPath;
  }

  protected Module createModule() {
    final Project project = myFixtureBuilder.getFixture().getProject();
    Assert.assertNotNull(project);
    final String moduleFilePath = PathUtil.getParentPath(project.getBasePath()) + "/" + getNextIndex() + ModuleFileType.DOT_DEFAULT_EXTENSION;
    return ModuleManager.getInstance(project).newModule(moduleFilePath, myModuleType.getId());
  }

  private static int getNextIndex() {
    return ourIndex++;
  }

  @Override
  public synchronized T getFixture() {
    if (myModuleFixture == null) {
      myModuleFixture = instantiateFixture();
    }
    return myModuleFixture;
  }

  @Override
  public void addSourceContentRoot(final String path) {
    addContentRoot(path);
    addSourceRoot(path);
  }

  protected abstract T instantiateFixture();

  Module buildModule() {
    final Module[] module = {null};

    ApplicationManager.getApplication().runWriteAction(() -> {
      module[0] = createModule();
      initModule(module[0]);
    });

    return module[0];
  }

  protected void initModule(Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();

    for (String contentRoot : myContentRoots) {
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentRoot);
      Assert.assertNotNull("cannot find content root: " + contentRoot, virtualFile);
      final ContentEntry contentEntry = rootModel.addContentEntry(virtualFile);

      for (String sourceRoot: mySourceRoots) {
        String s = contentRoot + "/" + sourceRoot;
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(s);
        if (vf == null) {
          final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourceRoot);
          if (file != null && VfsUtilCore.isAncestor(virtualFile, file, false)) vf = file;
        }
//        assert vf != null : "cannot find source root: " + sourceRoot;
        if (vf != null) {
          contentEntry.addSourceFolder(vf, false);
        }
        else {
          // files are not created yet
          contentEntry.addSourceFolder(VfsUtilCore.pathToUrl(s), false);
        }
      }
    }
    setupRootModel(rootModel);
    rootModel.commit();
  }

  protected void setupRootModel(ModifiableRootModel rootModel) {
  }

}
