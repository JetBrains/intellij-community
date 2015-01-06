/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.java.generate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.java.generate.config.Config;
import org.jetbrains.java.generate.view.ConfigUI;

import javax.swing.*;

/**
 * @author yole
 */
public class GenerateToStringConfigurable implements Configurable {
  private static final Logger log = Logger.getInstance("#GenerateToStringConfigurable");

  private ConfigUI configUI;
  private final Project myProject;

  public GenerateToStringConfigurable(Project project) {
    myProject = project;
  }

  public String getDisplayName() {
      return "Settings";
  }

  public String getHelpTopic() {
      return "editing.altInsert.tostring.settings";
  }

  public JComponent createComponent() {
      return configUI = new ConfigUI(GenerateToStringContext.getConfig(), myProject);
  }

  public boolean isModified() {
      return ! GenerateToStringContext.getConfig().equals(configUI.getConfig());
  }

  public void apply() throws ConfigurationException {
      Config config = configUI.getConfig();
      GenerateToStringContext.setConfig(config); // update context

      if (log.isDebugEnabled()) log.debug("Config updated:\n" + config);
  }

  public void reset() {
      configUI.setConfig(GenerateToStringContext.getConfig());
  }

  public void disposeUIResources() {
      configUI = null;
  }
}
