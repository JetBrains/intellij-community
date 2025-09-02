// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.testFramework.fixtures.impl;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectStoreOwner;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.NotNullProducer;
import com.intellij.util.SmartList;
import com.intellij.util.UriUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.nio.file.Path;
import java.util.List;

public abstract class ModuleFixtureBuilderImpl<T extends ModuleFixture> implements ModuleFixtureBuilder<T> {
  private static int ourIndex;

  private final NotNullProducer<? extends ModuleType<?>> myModuleTypeProducer;
  protected final List<String> myContentRoots = new SmartList<>();
  protected final List<String> mySourceRoots = new SmartList<>();
  protected final TestFixtureBuilder<? extends IdeaProjectTestFixture> myFixtureBuilder;
  private T myModuleFixture;
  protected String myOutputPath;
  protected String myTestOutputPath;

  public ModuleFixtureBuilderImpl(@NotNull ModuleType<?> moduleType, TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    myModuleTypeProducer = () -> moduleType;
    myFixtureBuilder = fixtureBuilder;
  }

  public ModuleFixtureBuilderImpl(final @NotNull NotNullProducer<? extends ModuleType<?>> moduleTypeProducer, TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    myModuleTypeProducer = moduleTypeProducer;
    myFixtureBuilder = fixtureBuilder;
  }

  @NotNull 
  public List<String> getContentRoots() {
    return myContentRoots;
  }

  @Override
  public @NotNull ModuleFixtureBuilder<T> addContentRoot(final @NotNull String contentRootPath) {
    myContentRoots.add(contentRootPath);
    return this;
  }

  @Override
  public @NotNull ModuleFixtureBuilder<T> addSourceRoot(final @NotNull String sourceRootPath) {
    Assert.assertFalse("content root should be added first", myContentRoots.isEmpty());
    mySourceRoots.add(sourceRootPath);
    return this;
  }

  @Override
  public void setOutputPath(final @NotNull String outputPath) {
    myOutputPath = outputPath;
  }

  @Override
  public void setTestOutputPath(@NotNull String outputPath) {
    myTestOutputPath = outputPath;
  }

  protected @NotNull Module createModule() {
    Project project = myFixtureBuilder.getFixture().getProject();
    Assert.assertNotNull(project);
    Path moduleFilePath = ((ProjectStoreOwner)project).getComponentStore().getProjectBasePath().getParent().resolve(getNextIndex() + ModuleFileType.DOT_DEFAULT_EXTENSION);
    return ModuleManager.getInstance(project).newModule(moduleFilePath, myModuleTypeProducer.produce().getId());
  }

  private static int getNextIndex() {
    return ourIndex++;
  }

  @Override
  public synchronized @NotNull T getFixture() {
    if (myModuleFixture == null) {
      myModuleFixture = instantiateFixture();
    }
    return myModuleFixture;
  }

  @Override
  public void addSourceContentRoot(final @NotNull String path) {
    addContentRoot(path);
    addSourceRoot(path);
  }

  protected abstract @NotNull T instantiateFixture();

  @NotNull
  Module buildModule() {
    Module[] module = new Module[1];
    WriteAction.run(() -> {
      ProjectRootManagerEx.getInstanceEx(myFixtureBuilder.getFixture().getProject()).mergeRootsChangesDuring(() -> {
        module[0] = createModule();
        initModule(module[0]);
      });
    });
    return module[0];
  }

  protected void initModule(Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();

    try {
      for (String contentRoot : myContentRoots) {
        final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentRoot);
        Assert.assertNotNull("cannot find content root: " + contentRoot, virtualFile);
        final ContentEntry contentEntry = rootModel.addContentEntry(virtualFile);

        for (String sourceRoot: mySourceRoots) {
          String s = UriUtil.trimTrailingSlashes(contentRoot + "/" + sourceRoot);

          VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(s);
          if (vf == null) {
            final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(sourceRoot);
            if (file != null && VfsUtilCore.isAncestor(virtualFile, file, false)) vf = file;
          }
  //        assert vf != null : "cannot find source root: " + sourceRoot;
          if (vf != null) {
            VirtualFile finalVf = vf;

            if (!ContainerUtil.exists(contentEntry.getSourceFolders(), folder -> finalVf.equals(folder.getFile()))) {
              contentEntry.addSourceFolder(finalVf, false);
            }
          }
          else {
            // files are not created yet

            String url = VfsUtilCore.pathToUrl(s);
            if (!ContainerUtil.exists(contentEntry.getSourceFolders(), folder -> url.equals(folder.getUrl()))) {
              contentEntry.addSourceFolder(url, false);
            }
          }
        }
      }
      setupRootModel(rootModel);
    }
    catch (Throwable e) {
      rootModel.dispose();
      throw e;
    }
    rootModel.commit();
  }

  protected void setupRootModel(ModifiableRootModel rootModel) {
  }

}
