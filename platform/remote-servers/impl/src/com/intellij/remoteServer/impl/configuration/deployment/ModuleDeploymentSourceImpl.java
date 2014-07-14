/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import com.intellij.remoteServer.configuration.deployment.ModuleDeploymentSource;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author nik
 */
public class ModuleDeploymentSourceImpl implements ModuleDeploymentSource {
  private final ModulePointer myPointer;

  public ModuleDeploymentSourceImpl(@NotNull ModulePointer pointer) {
    myPointer = pointer;
  }

  @NotNull
  public ModulePointer getModulePointer() {
    return myPointer;
  }

  @Nullable
  public Module getModule() {
    return myPointer.getModule();
  }

  @Override
  @Nullable
  public VirtualFile getContentRoot() {
    Module module = myPointer.getModule();
    if (module == null) {
      return null;
    }
    return getContentRoot(module);
  }

  public static VirtualFile getContentRoot(Module module) {
    return ArrayUtil.getFirstElement(ModuleRootManager.getInstance(module).getContentRoots());
  }

  @Nullable
  @Override
  public File getFile() {
    VirtualFile contentRoot = getContentRoot();
    if (contentRoot == null) {
      return null;
    }
    return VfsUtilCore.virtualToIoFile(contentRoot);
  }

  @Nullable
  @Override
  public String getFilePath() {
    File file = getFile();
    if (file == null) {
      return null;
    }
    return file.getAbsolutePath();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return myPointer.getModuleName();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Module;
  }

  @Override
  public boolean isValid() {
    return getModule() != null;
  }

  @Override
  public boolean isArchive() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModuleDeploymentSource)) return false;

    return myPointer.equals(((ModuleDeploymentSource)o).getModulePointer());
  }

  @Override
  public int hashCode() {
    return myPointer.hashCode();
  }

  @NotNull
  @Override
  public DeploymentSourceType<?> getType() {
    return DeploymentSourceType.EP_NAME.findExtension(ModuleDeploymentSourceType.class);
  }
}
