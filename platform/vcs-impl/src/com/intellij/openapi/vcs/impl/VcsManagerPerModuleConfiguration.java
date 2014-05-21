/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public class VcsManagerPerModuleConfiguration implements JDOMExternalizable, ModuleComponent {
  private final Module myModule;
  public String ACTIVE_VCS_NAME = "";
  public boolean USE_PROJECT_VCS = true;

  public static VcsManagerPerModuleConfiguration getInstance(Module module) {
    return module.getComponent(VcsManagerPerModuleConfiguration.class);
  }

  public VcsManagerPerModuleConfiguration(final Module module) {
    myModule = module;
  }

  @Override
  public void moduleAdded() {

  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() { }

  @Override
  @NotNull
  public String getComponentName() {
    return "VcsManagerConfiguration";
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManagerEx.getInstanceEx(myModule.getProject());
    if (!USE_PROJECT_VCS) {
      final VirtualFile[] roots = ModuleRootManager.getInstance(myModule).getContentRoots();

      StartupManager.getInstance(myModule.getProject()).runWhenProjectIsInitialized(new Runnable() {
        @Override
        public void run() {
          for(VirtualFile file: roots) {
            vcsManager.setDirectoryMapping(file.getPath(), ACTIVE_VCS_NAME);
          }
          vcsManager.cleanupMappings();
        }
      });
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }
}
