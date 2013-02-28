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
package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * @author Denis Zhdanov
 * @since 2/21/13 2:40 PM
 */
public class GradleProjectManager {

  public void renameProject(@NotNull final String newName, @NotNull final Project project, boolean synchronous) {
    if (!(project instanceof ProjectEx) || newName.equals(project.getName())) {
      return;
    }
    GradleUtil.executeProjectChangeAction(project, project, synchronous, new Runnable() {
      @Override
      public void run() {
        ((ProjectEx)project).setProjectName(newName);
      }
    });
  }

  public void setLanguageLevel(@NotNull final LanguageLevel languageLevel, @NotNull Project project, boolean synchronous) {
    final LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
    if (languageLevel == languageLevelExtension.getLanguageLevel()) {
      return;
    }
    GradleUtil.executeProjectChangeAction(project, project, synchronous, new Runnable() {
      @Override
      public void run() {
        languageLevelExtension.setLanguageLevel(languageLevel); 
      }
    });
  }
}
