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
package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

import javax.swing.*;

/**
 * @author peter
 */
public class GradleConfigurable extends SdkHomeConfigurable {

  public GradleConfigurable(Project project) {
    super(project, "Gradle");
  }

  public Icon getIcon() {
    return GradleLibraryManager.GRADLE_ICON;
  }

  public String getHelpTopic() {
    return "reference.settingsdialog.project.gradle";
  }

  @Override
  protected boolean isSdkHome(VirtualFile file) {
    return GradleLibraryManager.isGradleSdkHome(file);
  }

  @Override
  protected GradleSettings getFrameworkSettings() {
    return GradleSettings.getInstance(myProject);
  }

}