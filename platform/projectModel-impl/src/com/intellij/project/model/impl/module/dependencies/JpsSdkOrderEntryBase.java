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

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.model.JpsSdkManager;
import com.intellij.project.model.impl.module.JpsRootModel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.module.JpsSdkDependency;

/**
 * @author nik
 */
public abstract class JpsSdkOrderEntryBase extends JpsOrderEntry<JpsSdkDependency> implements JdkOrderEntry {
  public JpsSdkOrderEntryBase(JpsRootModel rootModel, JpsSdkDependency dependencyElement) {
    super(rootModel, dependencyElement);
  }

  @Override
  public String getJdkName() {
    final JpsSdkReference<?> reference = myDependencyElement.getSdkReference();
    return reference != null ? reference.getSdkName() : null;
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

  @Override
  public Sdk getJdk() {
    final JpsLibrary library = myDependencyElement.resolveSdk();
    if (library == null) return null;
    return JpsSdkManager.getInstance().getSdk(library);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "< " + getJdkName() + " >";
  }

  @NotNull
  @Override
  public VirtualFile[] getRootFiles(@NotNull OrderRootType type) {
    final Sdk sdk = getJdk();
    if (sdk == null) return VirtualFile.EMPTY_ARRAY;
    return sdk.getRootProvider().getFiles(type);
  }

  @NotNull
  @Override
  public String[] getRootUrls(@NotNull OrderRootType type) {
    final Sdk jdk = getJdk();
    if (jdk == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    return jdk.getRootProvider().getUrls(type);
  }

  @Override
  public boolean isSynthetic() {
    return true;
  }
}
