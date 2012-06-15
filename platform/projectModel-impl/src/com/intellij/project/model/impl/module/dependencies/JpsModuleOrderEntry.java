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
package com.intellij.project.model.impl.module.dependencies;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.model.JpsModelManager;
import com.intellij.project.model.impl.module.JpsRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

/**
 * @author nik
 */
public class JpsModuleOrderEntry extends JpsExportableOrderEntry<JpsModuleDependency> implements ModuleOrderEntry {
  public JpsModuleOrderEntry(JpsRootModel rootModel, JpsModuleDependency dependencyElement) {
    super(rootModel, dependencyElement);
  }

  @Override
  public Module getModule() {
    final JpsModule module = myDependencyElement.getModuleReference().resolve();
    if (module != null) return null;
    return JpsModelManager.getInstance(myRootModel.getProject()).getModuleManager().getModule(module);
  }

  @Override
  public String getModuleName() {
    return myDependencyElement.getModuleReference().getModuleName();
  }

  @Override
  public String getPresentableName() {
    return getModuleName();
  }

  @NotNull
  @Override
  public VirtualFile[] getFiles(OrderRootType type) {
    throw new UnsupportedOperationException("'getFiles' not implemented in " + getClass().getName());
  }

  @NotNull
  @Override
  public String[] getUrls(OrderRootType rootType) {
    throw new UnsupportedOperationException("'getUrls' not implemented in " + getClass().getName());
  }

  @Override
  public <R> R accept(RootPolicy<R> policy, @Nullable R initialValue) {
    return policy.visitModuleOrderEntry(this, initialValue);
  }
}
