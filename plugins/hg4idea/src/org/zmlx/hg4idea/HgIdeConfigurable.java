// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.openapi.components.*;
import com.intellij.openapi.options.*;
import org.jetbrains.annotations.*;
import org.zmlx.hg4idea.ui.*;

import javax.swing.*;

public class HgIdeConfigurable implements ApplicationComponent, Configurable {

  private final HgConfigurationIdePanel panel;

  public HgIdeConfigurable(HgGlobalSettings globalSettings) {
    panel = new HgConfigurationIdePanel(globalSettings);
  }

  @Nls
  public String getDisplayName() {
    return HgVcsMessages.message("hg4idea.mercurial");
  }

  public Icon getIcon() {
    return HgVcs.MERCURIAL_ICON;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return panel.getBasePanel();
  }

  public boolean isModified() {
    return panel.isModified();
  }

  public void apply() throws ConfigurationException {
    panel.validate();
    panel.saveSettings();
  }

  public void reset() {
    panel.loadSettings();
  }

  public void disposeUIResources() {
  }

  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
