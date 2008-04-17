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
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.awt.event.KeyEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;

/**
 * @author ilyas
 */
public class GroovyFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.config.ui.GroovyFacetTab");

  private JComboBox myComboBox;
  private JButton myNewButton;
  private JPanel myPanel;


  private boolean isSdkChanged = false;

  public GroovyFacetTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    initLibComboBox();
    myNewButton.setMnemonic(KeyEvent.VK_N);
  }

  protected void initLibComboBox() {

    myComboBox.removeAllItems();
    String maxValue = "";
    for (Library library : GroovyConfigUtils.getGroovyLibraries()) {
      String version = GroovyConfigUtils.getGroovyLibVersion(library);
      myComboBox.addItem(version);
      FontMetrics fontMetrics = myComboBox.getFontMetrics(myComboBox.getFont());
      if (version != null && fontMetrics.stringWidth(version) > fontMetrics.stringWidth(maxValue)) {
        maxValue = version;
      }
    }

    myComboBox.setPrototypeDisplayValue(maxValue + "_");

    myComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        isSdkChanged = true;
      }
    });

  }

  @Nls
  public String getDisplayName() {
    return GroovyBundle.message("groovy.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return isSdkChanged;
  }

  public void apply() throws ConfigurationException {
    isSdkChanged = false;
  }

  public void reset() {

  }

  public void disposeUIResources() {

  }
}
