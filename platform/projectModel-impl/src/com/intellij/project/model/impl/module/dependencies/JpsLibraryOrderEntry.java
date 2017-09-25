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

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.model.impl.module.JpsRootModel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.module.JpsLibraryDependency;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsLibraryOrderEntry extends JpsExportableOrderEntry<JpsLibraryDependency> implements LibraryOrderEntry {
  public JpsLibraryOrderEntry(JpsRootModel rootModel, JpsLibraryDependency dependencyElement) {
    super(rootModel, dependencyElement);
  }

  @Override
  public Library getLibrary() {
    return null;
  }

  @Override
  @NotNull
  public String getLibraryName() {
    return myDependencyElement.getLibraryReference().getLibraryName();
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return getLibraryName();
  }

  @NotNull
  @Override
  public VirtualFile[] getFiles(@NotNull OrderRootType type) {
    return getRootFiles(type);
  }

  @NotNull
  @Override
  public String[] getUrls(@NotNull OrderRootType rootType) {
    return getRootUrls(rootType);
  }

  @NotNull
  @Override
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    final Library library = getLibrary();
    return library != null ? library.getFiles(type) : VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String[] getRootUrls(@NotNull OrderRootType type) {
    final Library library = getLibrary();
    return library != null ? library.getUrls(type) : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public String getLibraryLevel() {
    final JpsElementReference<? extends JpsCompositeElement> reference = myDependencyElement.getLibraryReference().getParentReference();
    final JpsCompositeElement parent = reference.resolve();
    if (parent instanceof JpsGlobal) return LibraryTablesRegistrar.APPLICATION_LEVEL;
    if (parent instanceof JpsProject) return LibraryTablesRegistrar.PROJECT_LEVEL;
    if (parent instanceof JpsModule) return LibraryTableImplUtil.MODULE_LEVEL;
    return LibraryTablesRegistrar.PROJECT_LEVEL;
  }

  @Override
  public boolean isModuleLevel() {
    return LibraryTableImplUtil.MODULE_LEVEL.equals(getLibraryLevel());
  }

  @Override
  public <R> R accept(@NotNull RootPolicy<R> policy, @Nullable R initialValue) {
    return policy.visitLibraryOrderEntry(this, initialValue);
  }
}
