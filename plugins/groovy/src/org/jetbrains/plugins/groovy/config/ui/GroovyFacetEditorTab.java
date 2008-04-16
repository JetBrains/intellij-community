/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config.ui;

import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;

import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyApplicationSettings;

import java.awt.*;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class GroovyFacetEditorTab extends FacetEditorTab {
  private TextFieldWithBrowseButton myPathToGroovy;
  private JPanel myPanel;
  private JComboBox myComboBox;

  public static GroovyFacetEditorTab createEditorTab() {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    return new GroovyFacetEditorTab(
        settings.GROOVY_VERSIONS.toArray(new String[settings.GROOVY_VERSIONS.size()]),
        settings.DEFAULT_GROOVY_VERSION
    );
  }

  public GroovyFacetEditorTab(String[] versions, String defaultVersion) {
    if (versions.length > 0) {
      if (defaultVersion == null) {
        defaultVersion = versions[versions.length - 1];
      }
      String maxValue = "";
      for (String version : versions) {
        myComboBox.addItem(version);
        FontMetrics fontMetrics = myComboBox.getFontMetrics(myComboBox.getFont());
        if (fontMetrics.stringWidth(version) > fontMetrics.stringWidth(maxValue)) {
          maxValue = version;
        }
      }
      myComboBox.setPrototypeDisplayValue(maxValue + "_");
      myComboBox.setSelectedItem(defaultVersion);
    }
  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("file.template.group.title.groovy");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return false;
  }

  public void apply() throws ConfigurationException {

  }

  public void reset() {

  }

  public void disposeUIResources() {

  }
}
