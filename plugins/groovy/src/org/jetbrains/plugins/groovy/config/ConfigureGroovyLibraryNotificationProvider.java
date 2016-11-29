/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class ConfigureGroovyLibraryNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("configure.groovy.library");

  private final Project myProject;

  private final Set<FileType> supportedFileTypes;

  public ConfigureGroovyLibraryNotificationProvider(Project project, final EditorNotifications notifications) {
    myProject = project;
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        notifications.updateAllNotifications();
      }
    });

    supportedFileTypes = new HashSet<>();
    supportedFileTypes.add(GroovyFileType.GROOVY_FILE_TYPE);

    for (GroovyFrameworkConfigNotification configNotification : GroovyFrameworkConfigNotification.EP_NAME.getExtensions()) {
      Collections.addAll(supportedFileTypes, configNotification.getFrameworkFileTypes());
    }
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    try {
      if (!supportedFileTypes.contains(file.getFileType())) return null;
      // do not show the panel for Gradle build scripts
      // expecting groovy library to always be available at the gradle distribution
      if (StringUtil.endsWith(file.getName(), ".gradle")) return null;
      if (CompilerManager.getInstance(myProject).isExcludedFromCompilation(file)) return null;

      final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
      if (module == null) return null;

      if (isMavenModule(module)) return null;

      for (GroovyFrameworkConfigNotification configNotification : GroovyFrameworkConfigNotification.EP_NAME.getExtensions()) {
        if (configNotification.hasFrameworkStructure(module)) {
          if (!configNotification.hasFrameworkLibrary(module)) {
            return (EditorNotificationPanel)configNotification.createConfigureNotificationPanel(module);
          }
          return null;
        }
      }
    }
    catch (ProcessCanceledException ignored) {

    }
    catch (IndexNotReadyException ignored) {

    }

    return null;
  }

  private static boolean isMavenModule(@NotNull Module module) {
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.findChild("pom.xml") != null) return true;
    }

    return false;
  }

}
