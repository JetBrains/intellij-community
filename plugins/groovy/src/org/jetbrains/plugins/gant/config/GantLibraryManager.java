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

package org.jetbrains.plugins.gant.config;

import com.intellij.facet.ui.ProjectSettingsContext;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.groovy.config.AbstractGroovyLibraryManager;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyLibraryConfigurer;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;

import javax.swing.*;

/**
 * @author peter
 */
public class GantLibraryManager extends AbstractGroovyLibraryManager {
  public boolean managesLibrary(@NotNull Library library, LibrariesContainer container) {
    final VirtualFile[] files = container.getLibraryFiles(library, OrderRootType.CLASSES);
    return GantConfigUtils.isGantLibrary(files) && !GroovyConfigUtils.isGroovyLibrary(files) && !GrailsConfigUtils.containsGrailsJar(files);
  }

  @Nls
  public String getLibraryVersion(@NotNull Library library, LibrariesContainer librariesContainer) {
    return GantConfigUtils.getInstance().getSDKVersion(GantConfigUtils.getGantLibraryHome(librariesContainer.getLibraryFiles(library, OrderRootType.CLASSES)));
  }

  @NotNull
  public Icon getIcon() {
    return GantIcons.GANT_SDK_ICON;
  }

  public Library createLibrary(@NotNull ProjectSettingsContext context) {
    return createLibrary(context, new GroovyLibraryConfigurer() {
      @NotNull
      public String getSDKLibPrefix() {
        return "gant-";
      }

      public boolean isSDKHome(VirtualFile file) {
        return GantConfigUtils.isGantSdkHome(file);
      }

      @NotNull
      public String getSDKVersion(String path) {
        return GantConfigUtils.getGantVersion(path);
      }

      public Library createSDKLibrary(String path, String name, Project project, boolean inModuleSettings, boolean inProject) {
        return GantConfigUtils.getInstance().createSDKLibImmediately(path, name, project, inModuleSettings, inProject);
      }
    }, GantIcons.GANT_ICON_16x16);
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Gant";
  }

}