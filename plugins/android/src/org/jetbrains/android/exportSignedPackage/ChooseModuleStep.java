/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.exportSignedPackage;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class ChooseModuleStep extends ExportSignedPackageWizardStep {
  private JComboBox myModuleCombo;
  private JPanel myContentPanel;
  private CheckModulePanel myCheckModulePanel;

  private final ExportSignedPackageWizard myWizard;

  protected ChooseModuleStep(ExportSignedPackageWizard wizard, List<AndroidFacet> facets) {
    myWizard = wizard;
    assert facets.size() > 0;
    myModuleCombo.setModel(new CollectionComboBoxModel(facets, facets.get(0)));
    myModuleCombo.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Module module = ((AndroidFacet)value).getModule();
        setText(module.getName());
        setIcon(module.getModuleType().getNodeIcon(false));
        return this;
      }
    });
    myModuleCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCheckModulePanel.updateMessages(getSelectedFacet());
      }
    });
    myCheckModulePanel.updateMessages(getSelectedFacet());
  }

  private AndroidFacet getSelectedFacet() {
    return (AndroidFacet)myModuleCombo.getSelectedItem();
  }

  @Override
  public String getHelpId() {
    return "reference.android.reference.extract.signed.package.choose.module";
  }

  @Override
  protected void commitForNext() throws CommitStepException {
    if (myCheckModulePanel.hasError()) {
      throw new CommitStepException(AndroidBundle.message("android.project.contains.errors.error"));
    }
    AndroidFacet selectedFacet = getSelectedFacet();
    assert selectedFacet != null;
    myWizard.setFacet(selectedFacet);
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }
}
