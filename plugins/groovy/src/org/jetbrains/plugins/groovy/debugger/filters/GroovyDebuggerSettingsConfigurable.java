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
package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author ilyas
 */
public class GroovyDebuggerSettingsConfigurable implements SearchableConfigurable {
  private JCheckBox myIgnoreGroovyMethods;
  private JPanel myPanel;
  private JCheckBox myEnableHotSwap;
  private boolean isModified = false;
  private final GroovyDebuggerSettings mySettings;

  public GroovyDebuggerSettingsConfigurable(final GroovyDebuggerSettings settings) {
    mySettings = settings;
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    myIgnoreGroovyMethods.setSelected(flag == null || flag.booleanValue());
    myIgnoreGroovyMethods.setSelected(mySettings.ENABLE_GROOVY_HOTSWAP);

    ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        isModified = mySettings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS.booleanValue() != myIgnoreGroovyMethods.isSelected() ||
                     mySettings.ENABLE_GROOVY_HOTSWAP != myEnableHotSwap.isSelected();
      }
    };
    myIgnoreGroovyMethods.addActionListener(listener);
    myEnableHotSwap.addActionListener(listener);
  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("groovy.debug.caption");
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.groovy";
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return isModified;
  }

  public void apply() throws ConfigurationException {
    if (isModified) {
      mySettings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = myIgnoreGroovyMethods.isSelected();
      mySettings.ENABLE_GROOVY_HOTSWAP = myEnableHotSwap.isSelected();
    }
    isModified = false;
  }

  public void reset() {
    final Boolean flag = mySettings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    myIgnoreGroovyMethods.setSelected(flag == null || flag.booleanValue());
    myEnableHotSwap.setSelected(mySettings.ENABLE_GROOVY_HOTSWAP);
  }

  public void disposeUIResources() {
  }
}
