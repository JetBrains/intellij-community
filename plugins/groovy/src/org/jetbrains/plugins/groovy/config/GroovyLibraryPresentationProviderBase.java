// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.util.List;

public abstract class GroovyLibraryPresentationProviderBase extends LibraryPresentationProvider<GroovyLibraryProperties> {
  public GroovyLibraryPresentationProviderBase(LibraryKind kind) {
    super(kind);
  }

  @Override
  public String getDescription(@NotNull GroovyLibraryProperties properties) {
    final String version = properties.getVersion();
    if (version == null) {
      return GroovyBundle.message("framework.0.library", getLibraryCategoryName());
    }
    else {
      return GroovyBundle.message("framework.0.library.version.1", getLibraryCategoryName(), version);
    }
  }

  @Override
  public GroovyLibraryProperties detect(@NotNull List<VirtualFile> classesRoots) {
    final VirtualFile[] libraryFiles = VfsUtilCore.toVirtualFileArray(classesRoots);
    if (managesLibrary(libraryFiles)) {
      final String version = getLibraryVersion(libraryFiles);
      return new GroovyLibraryProperties(version);
    }
    return null;
  }

  protected abstract void fillLibrary(String path, LibraryEditor libraryEditor);

  public abstract boolean managesLibrary(final VirtualFile[] libraryFiles);

  @Nullable
  @Nls
  public abstract String getLibraryVersion(final VirtualFile[] libraryFiles);

  @Override
  @NotNull
  public abstract Icon getIcon(GroovyLibraryProperties properties);

  public abstract boolean isSDKHome(@NotNull VirtualFile file);

  public abstract @Nullable String getSDKVersion(String path);

  @NotNull @Nls public abstract String getLibraryCategoryName();

  @NotNull
  @NlsSafe
  public String getLibraryPrefix() {
    return StringUtil.toLowerCase(getLibraryCategoryName());
  }

  public boolean managesName(@NotNull String name) {
    return StringUtil.startsWithIgnoreCase(name, getLibraryPrefix());
  }
}
