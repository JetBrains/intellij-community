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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
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

  private final Map<String, Configurable> myVcsNameToConfigurableMap = new HashMap<String, Configurable>();
  private static final ColoredListCellRenderer VCS_LIST_RENDERER = new ColoredListCellRenderer() {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      String name = value == null ? NONE : ((AbstractVcs)value).getDisplayName();
      append(name, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, list.getForeground()));
    }
  };
  private JBScrollPane myVcsesScrollPane;

  @Nullable
  private final JComboBox myVcsesToUpdate;

  public VcsConfigurationsDialog(Project project, @Nullable JComboBox vcses, AbstractVcs selectedVcs) {
    super(project, false);
    myProject = project;

    AbstractVcs[] abstractVcses = collectAvailableNames();
    initList(abstractVcses);

    initVcsConfigurable(abstractVcses);

    updateConfiguration();
    myVcsesToUpdate = vcses;
    for (String vcsName : myVcsNameToConfigurableMap.keySet()) {
       Configurable configurable = myVcsNameToConfigurableMap.get(vcsName);
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
    final AbstractVcs currentVcs;
    currentVcs = selectedIndex >= 0 ? (AbstractVcs)(myVcses.getModel()).getElementAt(selectedIndex) : null;
    String currentName = currentVcs == null ? NONE : currentVcs.getName();
    ((CardLayout)myVcsConfigurationPanel.getLayout()).show(myVcsConfigurationPanel, currentName);
  }

  private void initVcsConfigurable(AbstractVcs[] vcses) {
    myVcsConfigurationPanel.setLayout(new CardLayout());
    MyNullConfigurable nullConfigurable = new MyNullConfigurable();
    myVcsNameToConfigurableMap.put(NONE, nullConfigurable);
    myVcsConfigurationPanel.add(nullConfigurable.createComponent(), NONE);
    for (AbstractVcs vcs : vcses) {
      addConfigurationPanelFor(vcs);
    }
  }

  private void addConfigurationPanelFor(final AbstractVcs vcs) {
    String name = vcs.getName();
    myVcsNameToConfigurableMap.put(name, vcs.getConfigurable());
    myVcsConfigurationPanel.add(createPanelForConfiguration(vcs), name);
  }

  private void initList(AbstractVcs[] names) {
    DefaultListModel model = new DefaultListModel();

    for (AbstractVcs name : names) {
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


  private AbstractVcs[] collectAvailableNames() {
    return ProjectLevelVcsManager.getInstance(myProject).getAllVcss();
  }

  private Component createPanelForConfiguration(AbstractVcs vcs) {
    if (vcs != null) {
      Configurable configurable = myVcsNameToConfigurableMap.get(vcs.getName());
      if (configurable != null) {
        JComponent result = configurable.createComponent();
        configurable.reset();
        return result;
      }
    }
    return new JPanel();

  }

  protected JComponent createCenterPanel() {
    return myVersionControlConfigurationsPanel;
  }

  protected void doOKAction() {
    for (String vcsName : myVcsNameToConfigurableMap.keySet()) {
      Configurable configurable = myVcsNameToConfigurableMap.get(vcsName);
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
      final VcsWrapper wrapper = new VcsWrapper((AbstractVcs)myVcses.getSelectedValue());
      vcsesToUpdate.setSelectedItem(wrapper);
      final ComboBoxModel model = vcsesToUpdate.getModel();
      for(int i = 0; i < model.getSize(); i++){
        final Object vcsWrapper = model.getElementAt(i);
        if (vcsWrapper instanceof VcsWrapper){
          final VcsWrapper defaultVcsWrapper = (VcsWrapper)vcsWrapper;
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

    public Icon getIcon() {
      return null;
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
