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
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class VcsConfigurationsDialog extends DialogWrapper{
  private JList myVcses;
  private JPanel myVcsConfigurationPanel;
  private final Project myProject;
  private JPanel myVersionControlConfigurationsPanel;
  private static final String NONE = VcsBundle.message("none.vcs.presentation");

  private final Map<String, UnnamedConfigurable> myVcsNameToConfigurableMap = new HashMap<>();
  private static final ColoredListCellRenderer VCS_LIST_RENDERER = new ColoredListCellRenderer() {
    protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
      String name = value == null ? NONE : ((VcsDescriptor) value).getDisplayName();
      append(name, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
    }
  };
  private JScrollPane myVcsesScrollPane;

  @Nullable
  private final JComboBox myVcsesToUpdate;

  public VcsConfigurationsDialog(Project project, @Nullable JComboBox vcses, VcsDescriptor selectedVcs) {
    super(project, false);
    myProject = project;

    VcsDescriptor[] abstractVcses = collectAvailableNames();
    initList(abstractVcses);

    initVcsConfigurable(abstractVcses);

    updateConfiguration();
    myVcsesToUpdate = vcses;
    for (String vcsName : myVcsNameToConfigurableMap.keySet()) {
       UnnamedConfigurable configurable = myVcsNameToConfigurableMap.get(vcsName);
       if (configurable != null && configurable.isModified()) configurable.reset();
    }
    updateConfiguration();
    if (selectedVcs != null){
      myVcses.setSelectedValue(selectedVcs, true);
    }
    init();
    setTitle(VcsBundle.message("dialog.title.version.control.configurations"));
  }

  private void updateConfiguration() {
    int selectedIndex = myVcses.getSelectionModel().getMinSelectionIndex();
    final VcsDescriptor currentVcs;
    currentVcs = selectedIndex >= 0 ? (VcsDescriptor)(myVcses.getModel()).getElementAt(selectedIndex) : null;
    String currentName = currentVcs == null ? NONE : currentVcs.getName();
    if (currentVcs != null) {
      final UnnamedConfigurable unnamedConfigurable = myVcsNameToConfigurableMap.get(currentName);
      unnamedConfigurable.createComponent();
      unnamedConfigurable.reset();
    }
    final CardLayout cardLayout = (CardLayout)myVcsConfigurationPanel.getLayout();
    cardLayout.show(myVcsConfigurationPanel, currentName);
  }

  private void initVcsConfigurable(VcsDescriptor[] vcses) {
    myVcsConfigurationPanel.setLayout(new CardLayout());
    MyNullConfigurable nullConfigurable = new MyNullConfigurable();
    myVcsNameToConfigurableMap.put(NONE, nullConfigurable);
    myVcsConfigurationPanel.add(nullConfigurable.createComponent(), NONE);
    for (VcsDescriptor vcs : vcses) {
      addConfigurationPanelFor(vcs);
    }
  }

  private void addConfigurationPanelFor(final VcsDescriptor vcs) {
    String name = vcs.getName();
    final JPanel parentPanel = new JPanel();
    final LazyConfigurable lazyConfigurable = new LazyConfigurable(new Getter<Configurable>() {
      @Override
      public Configurable get() {
        return AllVcses.getInstance(myProject).getByName(vcs.getName()).getConfigurable();
      }
    }, parentPanel);
    myVcsNameToConfigurableMap.put(name, lazyConfigurable);
    myVcsConfigurationPanel.add(parentPanel, name);
  }

  private void initList(VcsDescriptor[] names) {
    DefaultListModel model = new DefaultListModel();

    for (VcsDescriptor name : names) {
      model.addElement(name);
    }

    myVcses.setModel(model);
    myVcses.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateConfiguration();
      }
    });

    myVcses.setCellRenderer(VCS_LIST_RENDERER);

    myVcsesScrollPane.setMinimumSize(myVcsesScrollPane.getPreferredSize());
  }


  private VcsDescriptor[] collectAvailableNames() {
    return ProjectLevelVcsManager.getInstance(myProject).getAllVcss();
  }

  protected JComponent createCenterPanel() {
    return myVersionControlConfigurationsPanel;
  }

  protected void doOKAction() {
    for (String vcsName : myVcsNameToConfigurableMap.keySet()) {
      UnnamedConfigurable configurable = myVcsNameToConfigurableMap.get(vcsName);
      if (configurable != null && configurable.isModified()) {
        try {
          configurable.apply();
        }
        catch (ConfigurationException e) {
          Messages.showErrorDialog(VcsBundle.message("message.text.unable.to.save.settings", e.getMessage()),
                                   VcsBundle.message("message.title.unable.to.save.settings"));
        }
      }
    }

    final JComboBox vcsesToUpdate = myVcsesToUpdate;
    if (vcsesToUpdate != null) {
      final VcsDescriptor wrapper = (VcsDescriptor) myVcses.getSelectedValue();
      vcsesToUpdate.setSelectedItem(wrapper);
      final ComboBoxModel model = vcsesToUpdate.getModel();
      for(int i = 0; i < model.getSize(); i++){
        final Object vcsWrapper = model.getElementAt(i);
        if (vcsWrapper instanceof VcsDescriptor){
          final VcsDescriptor defaultVcsWrapper = (VcsDescriptor) vcsWrapper;
          if (defaultVcsWrapper.equals(wrapper)){
            vcsesToUpdate.setSelectedIndex(i);
            break;
          }
        }
      }
    }

    super.doOKAction();
  }

  protected void dispose() {
    myVcses.setCellRenderer(new DefaultListCellRenderer());
    super.dispose();
  }

  private static class MyNullConfigurable implements Configurable {
    public String getDisplayName() {
      return NONE;
    }

    public String getHelpTopic() {
      return "project.propVCSSupport";
    }

    public JComponent createComponent() {
      return new JPanel();
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
}
