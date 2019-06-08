// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * @author Kirill Likhodedov
 */
class VcsUpdateInfoScopeFilterConfigurable implements Configurable, NamedScopesHolder.ScopeListener, Disposable {
  private final JCheckBox myCheckbox;
  private final JComboBox myComboBox;
  private final VcsConfiguration myVcsConfiguration;
  private final NamedScopesHolder[] myNamedScopeHolders;

  VcsUpdateInfoScopeFilterConfigurable(@NotNull Project project, VcsConfiguration vcsConfiguration) {
    myVcsConfiguration = vcsConfiguration;
    myCheckbox = new JCheckBox(VcsBundle.getString("settings.filter.update.project.info.by.scope"));
    myComboBox = new ComboBox();

    myComboBox.setEnabled(myCheckbox.isSelected());
    myCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(@NotNull ChangeEvent e) {
        myComboBox.setEnabled(myCheckbox.isSelected());
      }
    });

    myNamedScopeHolders = NamedScopesHolder.getAllNamedScopeHolders(project);
    for (NamedScopesHolder holder : myNamedScopeHolders) {
      holder.addScopeListener(this, this);
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
  public JComponent createComponent() {
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.add(myCheckbox);
    panel.add(myComboBox);
    panel.add(Box.createHorizontalStrut(UIUtil.DEFAULT_HGAP));
    panel.add(LinkLabel.create("Manage Scopes", () -> {
      Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(panel));
      if (settings != null) {
        settings.select(settings.find(ScopeChooserConfigurable.PROJECT_SCOPES));
      }
    }));
    return panel;
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(myVcsConfiguration.UPDATE_FILTER_SCOPE_NAME, getScopeFilterName());
  }

  @Override
  public void apply() {
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
  public void dispose() {
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(this);
  }

  private String getScopeFilterName() {
    if (!myCheckbox.isSelected()) {
      return null;
    }
    return (String)myComboBox.getSelectedItem();
  }
}
