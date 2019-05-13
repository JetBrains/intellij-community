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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

/**
 * @author peter
 */
public class GantConfigurable extends SdkHomeConfigurable implements Configurable.NoScroll {

  public GantConfigurable(Project project) {
    super(project, "Gant");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settingsdialog.project.gant";
  }

  @Override
  protected boolean isSdkHome(VirtualFile file) {
    return GantUtils.isGantSdkHome(file);
  }

  @Override
  protected GantSettings getFrameworkSettings() {
    return GantSettings.getInstance(myProject);
  }

}
