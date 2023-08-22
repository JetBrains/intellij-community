// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.JarVersionDetectionUtil;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;

import static org.jetbrains.plugins.groovy.util.LibrariesUtil.SOME_GROOVY_CLASS;

public class GroovyLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  public static final LibraryKind GROOVY_KIND = LibraryKind.create("groovy");

  public GroovyLibraryPresentationProvider() {
    super(GROOVY_KIND);
  }

  @Override
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return LibrariesUtil.getGroovyLibraryHome(libraryFiles) != null;
  }

  @Override
  @Nls
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    String jarVersion = JarVersionDetectionUtil.detectJarVersion(SOME_GROOVY_CLASS, Arrays.asList(libraryFiles));
    if (jarVersion != null) {
      return jarVersion;
    }
    String home = LibrariesUtil.getGroovyLibraryHome(libraryFiles);
    if (home == null) {
      return GroovyBundle.message("undefined.library.version");
    }
    String version = GroovyConfigUtils.getInstance().getSDKVersionOrNull(home);
    if (version == null) {
      return GroovyBundle.message("undefined.library.version");
    }
    return version;
  }

  @Override
  @NotNull
  public Icon getIcon(GroovyLibraryProperties properties) {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    return GroovyConfigUtils.getInstance().isSDKHome(file);
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File srcRoot = new File(path + "/src/main");
      if (srcRoot.exists()) {
        libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);
      }

      File[] jars;
      File libDir = new File(path + "/lib");
      if (libDir.exists()) {
        jars = libDir.listFiles();
      } else {
        jars = new File(path + "/embeddable").listFiles();
      }
      if (jars != null) {
        for (File file : jars) {
          if (file.getName().endsWith(".jar")) {
            libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          }
        }
      }
  }

  @Override
  public @Nullable String getSDKVersion(String path) {
    return GroovyConfigUtils.getInstance().getSDKVersionOrNull(path);
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return GroovyBundle.message("language.groovy");
  }
}
