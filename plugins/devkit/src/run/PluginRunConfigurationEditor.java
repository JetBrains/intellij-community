/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package org.jetbrains.idea.devkit.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ComboboxWithBrowseButton;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.sandbox.Sandbox;
import org.jetbrains.idea.devkit.sandbox.SandboxConfigurable;
import org.jetbrains.idea.devkit.sandbox.SandboxManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class PluginRunConfigurationEditor extends SettingsEditor<PluginRunConfiguration> {
  private EditorPanel myEditor;
  private Project myProject;

  public void resetEditorFrom(PluginRunConfiguration prc) {
    myProject = prc.getProject();
    Sandbox sandbox = prc.getSandbox();
    myEditor.setSelectedBox(sandbox);
    updateModules(sandbox);
    myEditor.setModules(prc.getModules());
  }

  private void updateModules(Sandbox sandbox) {
    if (myProject != null && sandbox != null) {
      myEditor.setModulesList(sandbox.getModules(myProject));
    }
    else {
      myEditor.setModulesList(new Module[0]);
    }
  }

  public void applyEditorTo(PluginRunConfiguration prc) throws ConfigurationException {
    prc.setSandbox(myEditor.getSelectedBox());
    prc.setModules(myEditor.getModules());
  }

  public JComponent createEditor() {
    myEditor = new EditorPanel();
    return myEditor.getComponent();
  }

  private class EditorPanel {
    private JPanel myWholePanel;
    private ComboboxWithBrowseButton mySandboxCombo;
    private JList myModules;

    public EditorPanel() {
      mySandboxCombo.getComboBox().setRenderer(new DefaultListCellRenderer() {
        public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b1) {
          super.getListCellRendererComponent(jList, o, i, b, b1);
          if (o != null) {
            setText(((Sandbox)o).getName());
          }
          return this;
        }
      });

      mySandboxCombo.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          Sandbox selected = getSelectedBox();
          ShowSettingsUtil.getInstance().editConfigurable(myWholePanel, SandboxConfigurable.getInstance());
          mySandboxCombo.getComboBox().setModel(new DefaultComboBoxModel(SandboxManager.getInstance().getRegisteredSandboxes()));
          setSelectedBox(selected);
        }
      });

      myModules.setCellRenderer(new DefaultListCellRenderer() {
        public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b1) {
          super.getListCellRendererComponent(jList, o, i, b, b1);
          if (o != null) {
            Module module = (Module)o;
            setText(module.getName());
            setIcon(PluginModuleType.getInstance().getNodeIcon(false));
          }
          return this;
        }
      });


      mySandboxCombo.getComboBox().setModel(new DefaultComboBoxModel(SandboxManager.getInstance().getRegisteredSandboxes()));
      mySandboxCombo.getComboBox().addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          updateModules((Sandbox)mySandboxCombo.getComboBox().getSelectedItem());
        }
      });
    }

    public void setSelectedBox(Sandbox box) {
      mySandboxCombo.getComboBox().setSelectedItem(box);
    }

    public Sandbox getSelectedBox() {
      return (Sandbox)mySandboxCombo.getComboBox().getSelectedItem();
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public Module[] getModules() {
      Object[] values = myModules.getSelectedValues();
      if (values == null) return new Module[0];
      Module[] modules = new Module[values.length];
      for (int i = 0; i < modules.length; i++) {
        modules[i] = (Module)values[i];
      }
      return modules;
    }

    public void setModules(Module[] modules) {
      ArrayList<Module> allModules = new ArrayList<Module>();
      ListModel model = myModules.getModel();
      for (int i = 0; i < model.getSize(); i++) {
        allModules.add((Module)model.getElementAt(i));
      }

      for (int i = 0; i < modules.length; i++) {
        int idx = allModules.indexOf(modules[i]);
        myModules.getSelectionModel().addSelectionInterval(idx, idx);
      }
    }

    public void setModulesList(Module[] modules) {
      DefaultListModel model = new DefaultListModel();
      for (int i = 0; i < modules.length; i++) {
        model.addElement(modules[i]);
      }
      myModules.setModel(model);
    }
  }

  public void disposeEditor() {
  }
}