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

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * @author ilyas
 */
public class GroovyFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.config.ui.GroovyFacetTab");

  private GrovySDKComboBox myComboBox;
  private JButton myNewButton;
  private JPanel myPanel;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;


  private boolean isSdkChanged = false;

  public GroovyFacetTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    myNewButton.setMnemonic(KeyEvent.VK_N);
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;
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
    GrovySDKComboBox.DefaultGroovySDKComboBoxItem item = (GrovySDKComboBox.DefaultGroovySDKComboBoxItem) myComboBox.getSelectedItem();
    Module module = myEditorContext.getModule();
    if (module != null) {
      GroovyConfigUtils.updateGroovyLibInModule(module, item.getGroovySDK());
    }
    isSdkChanged = false;
  }

  public void reset() {

  }

  public void disposeUIResources() {

  }

  private void createUIComponents() {
    myComboBox = new GrovySDKComboBox();
    myComboBox.insertItemAt(new GrovySDKComboBox.NoGroovySDKComboBoxItem(), 0);
    myComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        isSdkChanged = true;
      }
    });
  }
}
