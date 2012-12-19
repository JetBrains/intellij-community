/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.impl.stores.StorageData;
import com.intellij.openapi.module.impl.ModuleEx;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.IOException;

/**
 * @author yole
 */
public class CoreModuleManager extends ModuleManagerImpl {
  private final Disposable myParentDisposable;

  public CoreModuleManager(Project project, Disposable parentDisposable) {
    super(project, project.getMessageBus());
    myParentDisposable = parentDisposable;
  }

  @Override
  protected ModuleEx createModule(String filePath) {
    return new CoreModule(myParentDisposable, myProject, filePath);
  }

  @Override
  protected ModuleEx createAndLoadModule(String filePath) throws IOException {
    final ModuleEx module = createModule(filePath);
    VirtualFile vFile = StandardFileSystems.local().findFileByPath(filePath);
    try {
      StorageData storageData = CoreProjectLoader.loadStorageFile(module, vFile);
      final Element element = storageData.getState("NewModuleRootManager");
      ModuleRootManagerImpl.ModuleRootManagerState state = new ModuleRootManagerImpl.ModuleRootManagerState();
      state.readExternal(element);
      ((ModuleRootManagerImpl) ModuleRootManager.getInstance(module)).loadState(state);
    }
    catch (JDOMException e) {
      throw new StateStorageException(e);
    }
    catch (InvalidDataException e) {
      throw new StateStorageException(e);
    }
    return module;
  }

  @Override
  protected void deliverPendingEvents() {
  }

  public void loadModules() {
    loadModules(myModuleModel);
  }

  protected Disposable getLifetime() {
    return myParentDisposable;
  }
}
