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

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.model.impl.module.JpsRootModel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceDependency;

import java.util.ArrayList;

/**
 * @author nik
 */
public class JpsModuleSourceOrderEntry extends JpsOrderEntry<JpsModuleSourceDependency> implements ModuleSourceOrderEntry {
  public JpsModuleSourceOrderEntry(JpsRootModel rootModel, JpsModuleSourceDependency dependencyElement) {
    super(rootModel, dependencyElement);
  }

  @Override
  public <R> R accept(RootPolicy<R> policy, @Nullable R initialValue) {
    return policy.visitModuleSourceOrderEntry(this, initialValue);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return ProjectBundle.message("project.root.module.source");
  }

  @Override
  @NotNull
  public VirtualFile[] getFiles(OrderRootType type) {
    if (OrderRootType.SOURCES.equals(type)) {
      return getRootModel().getSourceRoots();
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String[] getUrls(OrderRootType type) {
    final ArrayList<String> result = new ArrayList<>();
    if (OrderRootType.SOURCES.equals(type)) {
      final ContentEntry[] content = getRootModel().getContentEntries();
      for (ContentEntry contentEntry : content) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (SourceFolder sourceFolder : sourceFolders) {
          final String url = sourceFolder.getUrl();
          result.add(url);
        }
      }
      return ArrayUtil.toStringArray(result);
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public ModuleRootModel getRootModel() {
    return myRootModel;
  }
}
