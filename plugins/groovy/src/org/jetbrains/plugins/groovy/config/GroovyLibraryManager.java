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

import com.intellij.facet.ui.ProjectSettingsContext;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyLibraryManager extends AbstractGroovyLibraryManager {
  public boolean managesLibrary(@NotNull Library library, LibrariesContainer container) {
    final VirtualFile[] files = container.getLibraryFiles(library, OrderRootType.CLASSES);
    return GroovyConfigUtils.isGroovyLibrary(files) && !GrailsConfigUtils.containsGrailsJar(files);
  }

  @Nls
  public String getLibraryVersion(@NotNull Library library, LibrariesContainer librariesContainer) {
    return GroovyConfigUtils.getInstance().getSDKVersion(LibrariesUtil.getGroovyOrGrailsLibraryHome(librariesContainer.getLibraryFiles(library, OrderRootType.CLASSES)));
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

  public Library createLibrary(@NotNull ProjectSettingsContext context) {
    return createLibrary(context, GroovyConfigUtils.getInstance(), GroovyIcons.GROOVY_ICON_32x32);
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy";
  }

}
