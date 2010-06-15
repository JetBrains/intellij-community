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

/*
 * User: anna
 * Date: 15-Jun-2010
 */
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.OrderPanel;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestsConfigDialog extends DialogWrapper{
  @NonNls private static final String INCLUDE = "Include";
  private final LinkedHashMap<String, Boolean> myTests;
  private final JUnitConfigurationModel myModel;
  private final JUnitConfiguration myConfigurationCopy;
  private Project myProject;

  protected TestsConfigDialog(Component parent, JUnitConfigurationModel model, JUnitConfiguration configurationCopy) {
    super(parent, true);
    myModel = model;
    myConfigurationCopy = configurationCopy;
    myTests = new LinkedHashMap<String, Boolean>();
    for (Map.Entry<String, Boolean> entry : model.getPatterns().entrySet()) {
      myTests.put(entry.getKey(), entry.getValue());
    }
    myProject = configurationCopy.getProject();
    init();
    setTitle("Configure suite tests");
  }

  @Override
  protected JComponent createCenterPanel() {
    final OrderPanel<String> testsPanel = new OrderPanel<String>(String.class) {
      @Override
      public boolean isCheckable(String entry) {
        return true;
      }

      @Override
      public boolean isChecked(String entry) {
        return myTests.get(entry);
      }

      @Override
      public void setChecked(String entry, boolean checked) {
        myTests.put(entry, checked);
      }

      @Override
      public String getCheckboxColumnName() {
        return INCLUDE;
      }
    };
    testsPanel.setCheckboxColumnName(INCLUDE);
    for (String testName : myTests.keySet()) {
      testsPanel.add(testName);
    }
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(testsPanel, BorderLayout.CENTER);
    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout());
    final JButton addButton = new JButton(ApplicationBundle.message("button.add"));
    addButton.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e) {
        try {                                         //todo multiple selection
          final SourceScope sourceScope = myConfigurationCopy.getPersistentData().getScope().getSourceScope(myConfigurationCopy);
          final GlobalSearchScope searchScope =
            sourceScope.getGlobalSearchScope();
          final TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject)
            .createNoInnerClassesScopeChooser("Choose test", searchScope, TestClassFilter.create(sourceScope, myConfigurationCopy.getConfigurationModule().getModule()), null);
          chooser.showDialog();
          final PsiClass selectedClass = chooser.getSelectedClass();
          if (selectedClass != null) {
            myTests.put(selectedClass.getQualifiedName(), Boolean.TRUE);
            testsPanel.add(selectedClass.getQualifiedName());
          }
        }
        catch (JUnitUtil.NoJUnitException e1) {
          //todo show warning?
        }
      }
    });
    buttonsPanel.add(addButton);

    final JButton removeButton = new JButton(ApplicationBundle.message("button.remove"));
    removeButton.setEnabled(false);
    testsPanel.getEntryTable().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        removeButton.setEnabled(testsPanel.getEntryTable().getSelectedRows() != null);
      }
    });
    removeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int selectedRow = testsPanel.getEntryTable().getSelectedRow();
        testsPanel.remove(testsPanel.getValueAt(selectedRow));
      }
    });
    buttonsPanel.add(removeButton);

    panel.add(buttonsPanel, BorderLayout.EAST);
    return panel;
  }


  public String getPattern() {
    final List<String> enabledTests = new ArrayList<String>();
    for (String testName : myTests.keySet()) {
      if (myTests.get(testName)) {
        enabledTests.add(testName);
      }
    }
    return StringUtil.join(enabledTests, "||");
  }

  @Override
  protected void doOKAction() {
    myModel.setPatterns(myTests);
    super.doOKAction();
  }
}