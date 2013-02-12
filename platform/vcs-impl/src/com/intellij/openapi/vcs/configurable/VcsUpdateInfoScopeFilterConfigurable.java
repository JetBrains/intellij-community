/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author Kirill Likhodedov
 */
class VcsUpdateInfoScopeFilterConfigurable implements Configurable, NamedScopesHolder.ScopeListener {
  
  private final JCheckBox myCheckbox;
  private final JComboBox myComboBox;
  private final Project myProject;
  private final VcsConfiguration myVcsConfiguration;
  private final NamedScopesHolder[] myNamedScopeHolders;

  VcsUpdateInfoScopeFilterConfigurable(Project project, VcsConfiguration vcsConfiguration) {
    myProject = project;
    myVcsConfiguration = vcsConfiguration;
    myCheckbox = new JCheckBox(VcsBundle.getString("settings.filter.update.project.info.by.scope"));
    myComboBox = new JComboBox();
    
    myComboBox.setEnabled(myCheckbox.isSelected());
    myCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myComboBox.setEnabled(myCheckbox.isSelected());
      }
    });

    myNamedScopeHolders = NamedScopesHolder.getAllNamedScopeHolders(myProject);
    for (NamedScopesHolder holder : myNamedScopeHolders) {
      holder.addScopeListener(this);
    }
  }
  
  @Override
  public void scopesChanged() {
    reset();
  }
  
  @Nls
  @Override
  public String getDisplayName() {
    return VcsBundle.getString("settings.filter.update.project.info.by.scope");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.add(myCheckbox);
    panel.add(myComboBox);
    panel.add(Box.createHorizontalStrut(UIUtil.DEFAULT_HGAP));
    panel.add(new LinkLabel("Edit scopes", null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        final OptionsEditor optionsEditor = OptionsEditor.KEY.getData(DataManager.getInstance().getDataContext(panel));
        if (optionsEditor != null) {
          SearchableConfigurable configurable = optionsEditor.findConfigurableById(new ScopeChooserConfigurable(myProject).getId());
          if (configurable != null) {
            optionsEditor.select(configurable);
          }
        }
      }
    }));
    return panel;
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME, getScopeFilterName());
  }

  @Override
  public void apply() throws ConfigurationException {
    myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME = getScopeFilterName();
  }

  @Override
  public void reset() {
    myComboBox.removeAllItems();
    boolean selection = false;
    for (NamedScopesHolder holder : myNamedScopeHolders) {
      for (NamedScope scope : holder.getEditableScopes()) {
        myComboBox.addItem(scope.getName());
        if (!selection && scope.getName().equals(myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME)) {
          selection = true;
        }
      }
    }
    if (selection) {
      myComboBox.setSelectedItem(myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME);
    }
    myCheckbox.setSelected(selection);
  }

  @Override
  public void disposeUIResources() {
    for (NamedScopesHolder holder : myNamedScopeHolders) {
      holder.removeScopeListener(this);
    }
  }
  
  private String getScopeFilterName() {
    if (!myCheckbox.isSelected()) {
      return null;
    }
    return (String)myComboBox.getSelectedItem();
  }

}
