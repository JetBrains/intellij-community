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
package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.charset.Charset;

/**
 * Component that adds "IDE Encoding" option to General settings tab. Register it as generalOptionsProvider extension
 * if you want to use it in your product. 
 *
 * @author yole
 */
public class PlatformFileEncodingConfigurable implements SearchableConfigurable {
  private static final String SYSTEM_DEFAULT = IdeBundle.message("encoding.name.system.default", CharsetToolkit.getDefaultSystemCharset().displayName());
  private PlatformEncodingOptionsPanel myPanel;

  @Override
  @NotNull
  public String getId() {
    return "GeneralEncodingOptions";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "";
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new PlatformEncodingOptionsPanel();
    }
    return myPanel.getMainPanel();
  }

  @Override
  public boolean isModified() {
    final Object item = myPanel.myIDEEncodingComboBox.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(item)) {
      return !StringUtil.isEmpty(EncodingManager.getInstance().getDefaultCharsetName());
    }

    return !Comparing.equal(item, EncodingManager.getInstance().getDefaultCharset());
  }

  @Override
  public void apply() throws ConfigurationException {
    final Object item = myPanel.myIDEEncodingComboBox.getSelectedItem();
    if (SYSTEM_DEFAULT.equals(item)) {
      EncodingManager.getInstance().setDefaultCharsetName("");
    }
    else if (item != null) {
      EncodingManager.getInstance().setDefaultCharsetName(((Charset)item).name());
    }
  }

  @Override
  public void reset() {
    final DefaultComboBoxModel encodingsModel = new DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets());
    encodingsModel.insertElementAt(SYSTEM_DEFAULT, 0);
    myPanel.myIDEEncodingComboBox.setModel(encodingsModel);

    final String name = EncodingManager.getInstance().getDefaultCharsetName();
    if (StringUtil.isEmpty(name)) {
      myPanel.myIDEEncodingComboBox.setSelectedItem(SYSTEM_DEFAULT);
    }
    else {
      myPanel.myIDEEncodingComboBox.setSelectedItem(EncodingManager.getInstance().getDefaultCharset());
    }
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  public static class PlatformEncodingOptionsPanel {
    JComboBox myIDEEncodingComboBox;
    private JPanel myMainPanel;

    public JPanel getMainPanel() {
      return myMainPanel;
    }
  }
}
