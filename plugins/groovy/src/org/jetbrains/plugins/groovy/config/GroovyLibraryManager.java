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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.io.File;

/**
 * @author peter
 */
public class GroovyLibraryManager extends AbstractGroovyLibraryManager {
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return LibrariesUtil.getGroovyLibraryHome(libraryFiles) != null;
  }

  @Nls
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    final String home = LibrariesUtil.getGroovyLibraryHome(libraryFiles);
    if (home == null) return AbstractConfigUtils.UNDEFINED_VERSION;

    return GroovyConfigUtils.getInstance().getSDKVersion(home);
  }

  @NotNull
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    return GroovyConfigUtils.getInstance().isSDKHome(file);
  }

  @Override
  protected void fillLibrary(String path, Library.ModifiableModel model) {
    File srcRoot = new File(path + "/src/main");
      if (srcRoot.exists()) {
        model.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);
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
            model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          }
        }
      }
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    return GroovyConfigUtils.getInstance().getSDKVersion(path);
  }

  @Override
  public Icon getDialogIcon() {
    return GroovyIcons.GROOVY_ICON_32x32;
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy";
  }

}
