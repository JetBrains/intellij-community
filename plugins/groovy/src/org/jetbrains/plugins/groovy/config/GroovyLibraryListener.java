/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyLibraryListener extends AbstractProjectComponent implements ModuleRootListener {
  private final Set<Module> groovyModules = new HashSet<Module>();

  public GroovyLibraryListener(Project project) {
    super(project);
  }

  @Override
  public void projectOpened() {
    final MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, this);

    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      if (module.isDisposed()) continue;
      if (LibrariesUtil.hasGroovySdk(module)) {
        groovyModules.add(module);
      }
    }
  }

  public void beforeRootsChange(ModuleRootEvent event) {
  }

  public void rootsChanged(ModuleRootEvent event) {
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      if (module.isDisposed()) continue;
      final Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module);
      if ((libraries.length > 0 && !groovyModules.contains(module))) {
        final Application app = ApplicationManager.getApplication();
        if (app.isRestartCapable()) {
          if (Messages.showYesNoDialog(myProject, "You have added Groovy SDK library '" +
                                                  libraries[0].getName() +
                                                  "' to module '" +
                                                  module.getName() +
                                                  "'. To add Groovy support " +
                                                  ApplicationNamesInfo.getInstance().getFullProductName() +
                                                  " must be restarted. Would you like to add Groovy support and restart now?",
                                       "Add Groovy support", Messages.getInformationIcon()) == 0) {
            FSRecords.invalidateCaches();
            app.restart();
          }
        }
        else {
          Messages.showMessageDialog(myProject, "You have added Groovy SDK library '" +
                                                libraries[0].getName() +
                                                "' to module '" +
                                                module.getName() +
                                                "'. To add Groovy support you need to restart " +
                                                ApplicationNamesInfo.getInstance().getFullProductName() +
                                                ".", "Add Groovy support", Messages.getInformationIcon());
        }
      }
      else if ((libraries.length == 0 && groovyModules.contains(module))) {
        groovyModules.remove(module);
      }
    }
  }
}
