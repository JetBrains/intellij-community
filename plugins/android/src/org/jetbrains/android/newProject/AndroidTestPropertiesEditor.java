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
package org.jetbrains.android.newProject;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidTestPropertiesEditor {
  private JPanel myContentPanel;
  private AndroidModulesComboBox myModulesCombo;
  private JLabel myModulesLabel;

  public AndroidTestPropertiesEditor(@NotNull Project project) {
    myModulesCombo.init(project);
    myModulesLabel.setLabelFor(myModulesCombo);
  }

  public Module getModule() {
    return myModulesCombo.getModule();
  }

  public void validate() throws ConfigurationException {
    doValidate(myModulesCombo.getModule());
  }

  static void doValidate(Module module) throws ConfigurationException {
    if (module == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.specify.tested.module.error"));
    }
    else if (AndroidFacet.getInstance(module) == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.tested.module.without.facet.error"));
    }
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    if (moduleDirPath == null) {
      throw new ConfigurationException(AndroidBundle.message("android.wizard.cannot.find.module.parent.dir.error", module.getName()));
    }
  }

  public JPanel getContentPanel() {
    return myContentPanel;
  }
}
