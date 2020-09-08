// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;
import org.jetbrains.plugins.groovy.config.GroovyLibraryProperties;

import javax.swing.*;
import java.io.File;

public class GantLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  private static final LibraryKind GANT_KIND = LibraryKind.create("gant");

  public GantLibraryPresentationProvider() {
    super(GANT_KIND);
  }

  @Override
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return GantUtils.isGantLibrary(libraryFiles);
  }

  @Override
  @Nls
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    String version = GantUtils.getGantVersionOrNull(GantUtils.getGantLibraryHome(libraryFiles));
    return version == null ? GroovyBundle.message("undefined.library.version") : version;
  }

  @Override
  @NotNull
  public Icon getIcon(GroovyLibraryProperties properties) {
    return JetgroovyIcons.Groovy.Gant_sdk;
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    return GantUtils.isGantSdkHome(file);
  }

  @Override
  public @Nullable String getSDKVersion(String path) {
    return GantUtils.getGantVersionOrNull(path);
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return GroovyBundle.message("framework.gant");
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File srcRoot = new File(path + "/src/main");
    if (srcRoot.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);
    }

    // Add Gant jars
    File lib = new File(path + "/lib");
    File[] jars = lib.exists() ? lib.listFiles() : new File[0];
    if (jars != null) {
      for (File file : jars) {
        if (file.getName().endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
        }
      }
    }
  }
}
