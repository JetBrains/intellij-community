/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyLibraryManager extends AbstractGroovyLibraryManager {
  public boolean managesLibrary(@NotNull Library library, LibrariesContainer container) {
    return GroovyConfigUtils.isGroovyLibrary(container.getLibraryFiles(library, OrderRootType.CLASSES));
  }

  @Nls
  public String getLibraryVersion(@NotNull Library library, LibrariesContainer librariesContainer) {
    return GroovyConfigUtils.getInstance().getSDKVersion(LibrariesUtil.getGroovyLibraryHome(librariesContainer.getLibraryFiles(library, OrderRootType.CLASSES)));
  }

  @NotNull
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NotNull
  @Override
  public String getAddActionText() {
    return "Create new Groovy SDK...";
  }

  @Override
  public Library createSDKLibrary(String path, String name, Project project, boolean inModuleSettings, boolean inProject) {
    return GroovyConfigUtils.getInstance().createSDKLibImmediately(path, name, project, inModuleSettings, inProject);
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    return GroovyConfigUtils.getInstance().isSDKHome(file);
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
