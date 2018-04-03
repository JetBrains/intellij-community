// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importWizard;

import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.EclipseBundle;
import org.jetbrains.idea.eclipse.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.importer.EclipseProjectCodeStyleData;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class EclipseCodeStyleImportStep extends ProjectImportWizardStep {
  private JPanel myTopPanel;
  private JBList<EclipseProjectCodeStyleData> myProjectList;
  private JBRadioButton myUseDefaultCodeStyleRB;
  private JBRadioButton myImportCodeStyleRB;
  private JPanel myTitlePanel;
  private JLabel myImportCodeStyleLabel;
  private JBLabel myUseDefaultHint;
  private JBLabel myImportHint;
  private final DefaultListModel<EclipseProjectCodeStyleData> myProjectListModel;

  public EclipseCodeStyleImportStep(WizardContext context) {
    super(context);
    myProjectListModel = new DefaultListModel<>();
    myProjectList.setModel(myProjectListModel);
    ButtonGroup group = new ButtonGroup();
    group.add(myUseDefaultCodeStyleRB);
    group.add(myImportCodeStyleRB);
    myTitlePanel.setBorder(IdeBorderFactory.createTitledBorder("Choose project code style"));
    myProjectList.setEmptyText("Code styles not found");
    myUseDefaultCodeStyleRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateProjectListEnabledStatus();
      }
    });
    myImportCodeStyleRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateProjectListEnabledStatus();
      }
    });
    myProjectList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myUseDefaultHint.setText(EclipseBundle.message("eclipse.import.code.style.default.hint"));
    customizeHintLabel(myUseDefaultHint);
    myImportHint.setText(EclipseBundle.message("eclipse.import.code.style.import.hint"));
    customizeHintLabel(myImportHint);
  }

  private static void customizeHintLabel(@NotNull JBLabel hintLabel) {
    hintLabel.setForeground(JBColor.GRAY);
    hintLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
  }

  @Override
  public JComponent getComponent() {
    return myTopPanel;
  }

  @Override
  public void updateDataModel() {
    EclipseImportBuilder builder = (EclipseImportBuilder)getWizardContext().getProjectBuilder();
    assert builder != null;
    if (myImportCodeStyleRB.isSelected()) {
      builder.getParameters().codeStyleData = myProjectList.getSelectedValue();
    }
  }

  @Override
  public void updateStep() {
    myProjectListModel.clear();
    for(EclipseProjectCodeStyleData codeStyleData : getCodeStyleDataList()) {
      myProjectListModel.addElement(codeStyleData);
    }
    boolean isEnabled = !myProjectListModel.isEmpty();
    myImportCodeStyleRB.setEnabled(isEnabled);
    myImportCodeStyleLabel.setEnabled(isEnabled);
    myUseDefaultCodeStyleRB.setSelected(true);
    updateProjectListEnabledStatus();
  }

  private void updateProjectListEnabledStatus() {
    boolean isEnabled = myImportCodeStyleRB.isSelected() && !myProjectListModel.isEmpty();
    myProjectList.setEnabled(isEnabled);
    if (isEnabled) {
      myProjectList.getSelectionModel().setSelectionInterval(0, 0);
    }
    else {
      myProjectList.getSelectionModel().clearSelection();
    }
  }


  private List<EclipseProjectCodeStyleData> getCodeStyleDataList() {
    List<EclipseProjectCodeStyleData> codeStyleDataList = new ArrayList<>();
    ProjectBuilder builder = getWizardContext().getProjectBuilder();
    if (builder instanceof EclipseImportBuilder) {
      EclipseImportBuilder eclipseImportBuilder = (EclipseImportBuilder)builder;
      for (String projectPath : eclipseImportBuilder.getParameters().projectsToConvert) {
        String projectName = EclipseProjectFinder.findProjectName(projectPath);
        if (projectName != null) {
          EclipseProjectCodeStyleData codeStyleData = new EclipseProjectCodeStyleData(projectName, projectPath);
          if (codeStyleData.loadEclipsePreferences()) {
            codeStyleDataList.add(codeStyleData);
          }
        }
      }
    }
    return codeStyleDataList;
  }

}
