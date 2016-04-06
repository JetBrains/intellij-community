/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.ExternalSystemAutoImportAware;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 6/8/13 3:49 PM
 */
public class GradleAutoImportAware implements ExternalSystemAutoImportAware {

  @Nullable
  @Override
  public String getAffectedExternalProjectPath(@NotNull String changedFileOrDirPath, @NotNull Project project) {
    if (!changedFileOrDirPath.endsWith(GradleConstants.EXTENSION)) {
      return null;
    }

    ExternalSystemManager<?,?,?,?,?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    assert manager != null;
    AbstractExternalSystemSettings<?, ?,?> systemSettings = manager.getSettingsProvider().fun(project);
    Collection<? extends ExternalProjectSettings> projectsSettings = systemSettings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return null;
    }

    for (ExternalProjectSettings setting : projectsSettings) {
      String rootProjectPath = setting.getExternalProjectPath();
      if (FileUtil.isAncestor(rootProjectPath, changedFileOrDirPath, false)) {
        return rootProjectPath;
      } else {
        // honor multi-projects with flat structure
        for (String modulePath : setting.getModules()) {
          if(StringUtil.equals(modulePath, rootProjectPath)) continue;
          if (FileUtil.isAncestor(modulePath, changedFileOrDirPath, false)) {
            return rootProjectPath;
          }
        }
      }
    }
    return null;
  }
}
