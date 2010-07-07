/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Provides user interface for editing configuration settings.
 */
class SettingsUI {

  private final Configurable[] myConfigurables;

  private final JPanel myRoot = new JPanel(new BorderLayout());
  private final JTabbedPane myTabbedPane;


  SettingsUI(@NotNull final Project project, Configuration configuration) {
    myTabbedPane = new JTabbedPane(JTabbedPane.TOP);
    myRoot.add(myTabbedPane);

    final ArrayList<Configurable> configurables = new ArrayList<Configurable>();
    for (LanguageInjectionSupport support : Extensions.getExtensions(LanguageInjectionSupport.EP_NAME)) {
      ContainerUtil.addAll(configurables, support.createSettings(project, configuration));
    }
    Collections.sort(configurables, new Comparator<Configurable>() {
      public int compare(final Configurable o1, final Configurable o2) {
        return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
      }
    });
    configurables.add(0, new InjectionsSettingsUI(project, configuration));
    myConfigurables = configurables.toArray(new Configurable[configurables.size()]);
    for (Configurable configurable : configurables) {
      myTabbedPane.addTab(configurable.getDisplayName(), configurable.createComponent());
    }
  }

  public JComponent createComponent() {
    return myRoot;
  }

  @Nullable
  public Configurable getSelectedChild() {
    final int index = myTabbedPane.getSelectedIndex();
    if (index >= 0) return myConfigurables[index];
    return null;
  }

  public boolean isModified() {
    for (Configurable configurable : myConfigurables) {
      if (configurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable configurable : myConfigurables) {
      configurable.apply();
    }
  }

  public void reset() {
    for (Configurable configurable : myConfigurables) {
      configurable.reset();
    }
  }
}
