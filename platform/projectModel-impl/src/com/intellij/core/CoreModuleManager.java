// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author yole
 */
public class CoreModuleManager extends ModuleManagerImpl {
  private final Disposable myParentDisposable;

  public CoreModuleManager(Project project, Disposable parentDisposable) {
    super(project);

    myParentDisposable = parentDisposable;
  }

  @NotNull
  @Override
  protected ModuleEx createModule(@NotNull String filePath) {
    return new CoreModule(myParentDisposable, myProject, Paths.get(filePath));
  }

  @NotNull
  @Override
  protected ModuleEx createAndLoadModule(@NotNull String filePath) throws IOException {
    final ModuleEx module = createModule(filePath);
    VirtualFile vFile = StandardFileSystems.local().findFileByPath(filePath);
    try {
      assert vFile != null;
      ModuleRootManagerImpl.ModuleRootManagerState state = new ModuleRootManagerImpl.ModuleRootManagerState();
      state.readExternal(CoreProjectLoader.loadStorageFile(module, vFile).get("NewModuleRootManager"));
      ((ModuleRootManagerImpl)ModuleRootManager.getInstance(module)).loadState(state);
    }
    catch (JDOMException e) {
      throw new IOException(e);
    }
    return module;
  }

  public void loadModules() {
    loadModules(myModuleModel);
  }

  protected Disposable getLifetime() {
    return myParentDisposable;
  }
}
