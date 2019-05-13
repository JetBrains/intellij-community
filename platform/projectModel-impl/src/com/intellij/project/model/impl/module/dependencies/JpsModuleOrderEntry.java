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
import com.intellij.openapi.roots.OrderRootsEnumerator;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.model.impl.module.JpsRootModel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    return null;
  }

  @Override
  public String getModuleName() {
    return myDependencyElement.getModuleReference().getModuleName();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getModuleName();
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    final OrderRootsEnumerator enumerator = getEnumerator(type);
    return enumerator != null ? enumerator.getRoots() : VirtualFile.EMPTY_ARRAY;
  }

  @Nullable
  private OrderRootsEnumerator getEnumerator(OrderRootType type) {
    final Module module = getModule();
    if (module == null) return null;

    return ModuleRootManagerImpl.getCachingEnumeratorForType(type, module);
  }

  @Override
  @NotNull
  public String[] getUrls(@NotNull OrderRootType rootType) {
    final OrderRootsEnumerator enumerator = getEnumerator(rootType);
    return enumerator != null ? enumerator.getUrls() : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, @Nullable R initialValue) {
    return policy.visitModuleOrderEntry(this, initialValue);
  }
}
