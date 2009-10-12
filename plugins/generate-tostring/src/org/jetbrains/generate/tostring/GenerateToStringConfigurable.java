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
package org.jetbrains.generate.tostring;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.generate.tostring.config.Config;
import org.jetbrains.generate.tostring.view.ConfigUI;

import javax.swing.*;
import java.net.URL;

/**
 * @author yole
 */
public class GenerateToStringConfigurable implements Configurable {
  private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringConfigurable"); 

  private ConfigUI configUI;

  public String getDisplayName() {
      return "Settings";
  }

  public Icon getIcon() {
      URL resource = getClass().getResource("/resources/configurableToStringPlugin.png");
      if (resource != null) {
          return new ImageIcon(resource);
      }
      return null;
  }

  public String getHelpTopic() {
      return null;
  }

  public JComponent createComponent() {
      return configUI = new ConfigUI(GenerateToStringContext.getConfig());
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
