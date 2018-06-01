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
package org.jetbrains.plugins.groovy.griffon;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;
import org.jetbrains.plugins.groovy.config.GroovyLibraryProperties;

import javax.swing.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nik
 */
public class GriffonLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  public static final LibraryKind GRIFFON_KIND = LibraryKind.create("griffon");
  @NonNls private static final Pattern GRIFFON_JAR_FILE_PATTERN = Pattern.compile("griffon-rt-(\\d.*)\\.jar");

  public GriffonLibraryPresentationProvider() {
    super(GRIFFON_KIND);
  }


  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    String[] jars = new File(path + "/dist").list();
    if (jars != null) {
      for (String fileName : jars) {
        if (fileName.endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(new File(path + ("/dist/") + fileName)), OrderRootType.CLASSES);
        }
      }
    }

    jars = new File(path + "/lib").list();
    if (jars != null) {
      for (String fileName : jars) {
        if (fileName.endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(new File(path + "/lib/" + fileName)), OrderRootType.CLASSES);
        }
      }
    }
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    final VirtualFile dist = file.findChild("dist");
    if (dist == null) {
      return false;
    }

    return isGriffonSdk(dist.getChildren());
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    for (VirtualFile virtualFile : file.findChild("dist").getChildren()) {
      final String version = getGriffonCoreJarVersion(virtualFile);
      if (version != null) {
        return version;
      }
    }
    throw new AssertionError(path);
  }

  @Override
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return isGriffonSdk(libraryFiles);
  }

  static boolean isGriffonCoreJar(VirtualFile file) {
    return GRIFFON_JAR_FILE_PATTERN.matcher(file.getName()).matches();
  }

  @Nls
  @Override
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    return getGriffonVersion(libraryFiles);
  }

  public static boolean isGriffonSdk(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (isGriffonCoreJar(file)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getGriffonVersion(@NotNull Module module) {
    for (final OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final VirtualFile[] files = ((LibraryOrderEntry)orderEntry).getRootFiles(OrderRootType.CLASSES);
        if (isGriffonSdk(files)) {
          return getGriffonVersion(files);
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getGriffonVersion(VirtualFile[] libraryFiles) {
    for (VirtualFile file : libraryFiles) {
      final String version = getGriffonCoreJarVersion(file);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  @Nullable
  private static String getGriffonCoreJarVersion(VirtualFile file) {
    final Matcher matcher = GRIFFON_JAR_FILE_PATTERN.matcher(file.getName());
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  @NotNull
  @Override
  public Icon getIcon(GroovyLibraryProperties properties) {
    return JetgroovyIcons.Griffon.Griffon;
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Griffon";
  }
}
