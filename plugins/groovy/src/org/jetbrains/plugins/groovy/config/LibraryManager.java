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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author peter
 */
public abstract class LibraryManager {

  @Nullable
  public static LibraryManager findManagerFor(@NotNull Library library, final LibraryManager[] managers, final LibrariesContainer container) {
    for (final LibraryManager manager : managers) {
      final String name = library.getName();
      if (name != null && manager.managesName(name) && manager.managesLibrary(container.getLibraryFiles(library, OrderRootType.CLASSES))) {
        return manager;
      }
    }

    for (final LibraryManager manager : managers) {
      if (manager.managesLibrary(container.getLibraryFiles(library, OrderRootType.CLASSES))) {
        return manager;
      }
    }
    return null;
  }

  public abstract boolean managesLibrary(final VirtualFile[] libraryFiles);

  @Nullable
  @Nls
  public abstract String getLibraryVersion(final VirtualFile[] libraryFiles);

  @NotNull
  public abstract Icon getIcon();

  public abstract boolean isSDKHome(@NotNull VirtualFile file);

  public abstract @NotNull String getSDKVersion(String path);

  @NotNull @Nls public abstract String getLibraryCategoryName();

  @NotNull
  @Nls
  public String getLibraryPrefix() {
    return StringUtil.toLowerCase(getLibraryCategoryName());
  }

  public boolean managesName(@NotNull String name) {
    return StringUtil.startsWithIgnoreCase(name, getLibraryPrefix());
  }

}
