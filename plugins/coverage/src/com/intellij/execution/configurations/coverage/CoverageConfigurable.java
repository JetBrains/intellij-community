/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.execution.configurations.coverage;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.util.JreVersionDetector;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.PackageChooser;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.peer.PeerFactory;
import com.intellij.psi.PsiPackage;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Base {@link com.intellij.openapi.options.Configurable} for configuring code coverage
 * To obtain a full configurable use
 * <code>
 * SettingsEditorGroup<YourConfiguration> group = new SettingsEditorGroup<YourConfiguration>();
 * group.addEditor(title, yourConfigurable);
 * group.addEditor(title, yourCoverageConfigurable);
 * </code>
 * @author ven
 */
public class CoverageConfigurable<T extends ModuleBasedConfiguration & RunJavaConfiguration> extends SettingsEditor<T> {
  private final JreVersionDetector myVersionDetector = new JreVersionDetector();
  Project myProject;
  private MyClassFilterEditor myClassFilterEditor;
  private JCheckBox myCoverageEnabledCheckbox;
  private JCheckBox myMergeDataCheckbox;
  private JComboBox myMergedCoverageSuiteCombo;
  private JLabel myCoverageNotSupportedLabel;
  private JComboBox myCoverageRunnerCb;
  private JPanel myRunnerPanel;
  private JCheckBox myTrackPerTestCoverageCb;
  private JCheckBox myTrackTestSourcesCb;

  private JRadioButton myTracingRb;
  private JRadioButton mySamplingRb;
  private final T myConfig;

  private static class MyClassFilterEditor extends ClassFilterEditor {
    public MyClassFilterEditor(Project project) {
      super(project);
    }

    protected void addPatternFilter() {
      PackageChooser chooser = PeerFactory.getInstance().getUIHelper().
        createPackageChooser(CodeInsightBundle.message("coverage.pattern.filter.editor.choose.package.title"), myProject);
      chooser.show();
      if (chooser.isOK()) {
        List<PsiPackage> packages = chooser.getSelectedPackages();
        if (!packages.isEmpty()) {
          for (final PsiPackage aPackage : packages) {
            final String fqName = aPackage.getQualifiedName();
            final String pattern = fqName.length() > 0 ? fqName + ".*" : "*";
            myTableModel.addRow(createFilter(pattern));
          }
          int row = myTableModel.getRowCount() - 1;
          myTable.getSelectionModel().setSelectionInterval(row, row);
          myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
          myTable.requestFocus();
        }
      }
    }

    protected String getAddPatternButtonText() {
      return CodeInsightBundle.message("coverage.button.add.package");
    }
  }

  public CoverageConfigurable(T config) {
    myConfig = config;
    myProject = config.getProject();
  }

  protected void resetEditorFrom(final T runConfiguration) {
    final boolean isJre50 = myVersionDetector.isJre50Configured(runConfiguration);

    myCoverageNotSupportedLabel.setVisible(!isJre50);

    myCoverageEnabledCheckbox.setEnabled(isJre50);

    final CoverageEnabledConfiguration configuration = CoverageEnabledConfiguration.get(runConfiguration);
    final CoverageRunner runner = configuration.getCoverageRunner();
    if (runner != null) {
      myCoverageRunnerCb.setSelectedItem(runner);
    } else {
      myCoverageRunnerCb.setSelectedIndex(0);
    }
    UIUtil.setEnabled(myRunnerPanel, isJre50 && configuration.isCoverageEnabled(), true);

    myMergeDataCheckbox.setEnabled(isJre50 && configuration.isCoverageEnabled());
    myMergeDataCheckbox.setSelected(configuration.isMergeWithPreviousResults());

    myMergedCoverageSuiteCombo.setEnabled(myMergeDataCheckbox.isEnabled() && myMergeDataCheckbox.isSelected());
    final DefaultComboBoxModel model = (DefaultComboBoxModel)myMergedCoverageSuiteCombo.getModel();
    model.removeAllElements();
    final CoverageSuite[] suites = CoverageDataManager.getInstance(myProject).getSuites();
    for (CoverageSuite suite : suites) {
      if (suite.isValid()) {
        model.addElement(suite.getPresentableName());
      }
    }
    myMergedCoverageSuiteCombo.setSelectedItem(configuration.getSuiteToMergeWith());

    myCoverageEnabledCheckbox.setSelected(isJre50 && configuration.isCoverageEnabled());
    myClassFilterEditor.setEnabled(myCoverageEnabledCheckbox.isSelected());

    myClassFilterEditor.setFilters(configuration.getCoveragePatterns());
    myTracingRb.setEnabled(runner != null && runner.isCoverageByTestApplicable());
    mySamplingRb.setSelected(configuration.isSampling() || !myTracingRb.isEnabled());
    myTracingRb.setSelected(!mySamplingRb.isSelected());

    myTrackPerTestCoverageCb.setSelected(configuration.isTrackPerTestCoverage());
    myTrackPerTestCoverageCb.setEnabled(!mySamplingRb.isSelected() && canHavePerTestCoverage() && runner != null && runner.isCoverageByTestApplicable());

    myTrackTestSourcesCb.setSelected(configuration.isTrackTestFolders());
  }
  
  protected boolean canHavePerTestCoverage() {
    return CoverageEnabledConfiguration.get(myConfig).canHavePerTestCoverage();
  }

  protected void applyEditorTo(final T runConfiguration) throws ConfigurationException {
    final CoverageEnabledConfiguration configuration = CoverageEnabledConfiguration.get(runConfiguration);
    configuration.setCoverageEnabled(myCoverageEnabledCheckbox.isSelected());
    configuration.setMergeWithPreviousResults(myMergeDataCheckbox.isSelected());
    configuration.setCoveragePatterns(myClassFilterEditor.getFilters());
    configuration.setSuiteToMergeWith((String)myMergedCoverageSuiteCombo.getSelectedItem());
    configuration.setCoverageRunner((CoverageRunner)myCoverageRunnerCb.getSelectedItem());
    configuration.setTrackPerTestCoverage(myTrackPerTestCoverageCb.isSelected());
    configuration.setSampling(mySamplingRb.isSelected());
    configuration.setTrackTestFolders(myTrackTestSourcesCb.isSelected());
  }

  @NotNull
  protected JComponent createEditor() {
    JPanel result = new JPanel(new VerticalFlowLayout());

    myCoverageEnabledCheckbox = new JCheckBox(ExecutionBundle.message("enable.coverage.with.emma"));
    result.add(myCoverageEnabledCheckbox);

    final DefaultComboBoxModel runnersModel = new DefaultComboBoxModel();
    myCoverageRunnerCb = new JComboBox(runnersModel);
    for (CoverageRunner runner : Extensions.getExtensions(CoverageRunner.EP_NAME)) {
      runnersModel.addElement(runner);
    }
    myCoverageRunnerCb.setRenderer(new DefaultListCellRenderer(){
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(((CoverageRunner)value).getPresentableName());
        return rendererComponent;
      }
    });
    myCoverageRunnerCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final CoverageRunner runner = (CoverageRunner)myCoverageRunnerCb.getSelectedItem();
        enableTracingPanel(runner != null && runner.isCoverageByTestApplicable());
        myTrackPerTestCoverageCb.setEnabled(myTracingRb.isSelected() && canHavePerTestCoverage() && runner != null && runner.isCoverageByTestApplicable());
      }
    });
    myRunnerPanel = new JPanel(new BorderLayout());
    myRunnerPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    myRunnerPanel.add(new JLabel("Choose coverage runner:"), BorderLayout.NORTH);
    myRunnerPanel.add(myCoverageRunnerCb, BorderLayout.CENTER);
    final JPanel cPanel = new JPanel(new VerticalFlowLayout());

    mySamplingRb = new JRadioButton("Sampling");
    cPanel.add(mySamplingRb);
    myTracingRb = new JRadioButton("Tracing");
    cPanel.add(myTracingRb);

    final ButtonGroup group = new ButtonGroup();
    group.add(mySamplingRb);
    group.add(myTracingRb);

    ActionListener samplingListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final CoverageRunner runner = (CoverageRunner)myCoverageRunnerCb.getSelectedItem();
        myTrackPerTestCoverageCb.setEnabled(canHavePerTestCoverage() && myTracingRb.isSelected() && runner != null && runner.isCoverageByTestApplicable());
      }
    };

    mySamplingRb.addActionListener(samplingListener);
    myTracingRb.addActionListener(samplingListener);

    myTrackPerTestCoverageCb = new JCheckBox("Track per test coverage");
    final JPanel tracingPanel = new JPanel(new BorderLayout());
    tracingPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
    tracingPanel.add(myTrackPerTestCoverageCb, BorderLayout.CENTER);
    cPanel.add(tracingPanel);
    myRunnerPanel.add(cPanel, BorderLayout.SOUTH);

    result.add(myRunnerPanel);

    myMergeDataCheckbox = new JCheckBox(ExecutionBundle.message("merge.coverage.data"));
    result.add(myMergeDataCheckbox);
    myMergeDataCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myMergedCoverageSuiteCombo.setEnabled(myMergeDataCheckbox.isSelected());
      }
    });

    final JPanel serFilePanel = new JPanel(new GridBagLayout());
    myMergedCoverageSuiteCombo = new JComboBox(new DefaultComboBoxModel());
    serFilePanel.add(myMergedCoverageSuiteCombo, new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                                    GridBagConstraints.HORIZONTAL, new Insets(0, 20, 5, 5), 0, 0));
    result.add(serFilePanel);

    JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(ExecutionBundle.message("record.coverage.filters.title")));
    myClassFilterEditor = new MyClassFilterEditor(myProject);
    panel.add(myClassFilterEditor);
    myTrackTestSourcesCb = new JCheckBox("Enable coverage in test folders");
    panel.add(myTrackTestSourcesCb);
    result.add(panel);

    myCoverageEnabledCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final boolean isCoverageEnabled = myCoverageEnabledCheckbox.isSelected();
        myClassFilterEditor.setEnabled(isCoverageEnabled);
        myMergeDataCheckbox.setEnabled(isCoverageEnabled);
        UIUtil.setEnabled(myRunnerPanel, isCoverageEnabled, true);
        myMergedCoverageSuiteCombo.setEnabled(isCoverageEnabled && myMergeDataCheckbox.isSelected());
        final CoverageRunner runner = (CoverageRunner)myCoverageRunnerCb.getSelectedItem();
        enableTracingPanel(isCoverageEnabled && runner != null && runner.isCoverageByTestApplicable());
        myTrackPerTestCoverageCb.setEnabled(myTracingRb.isSelected() && isCoverageEnabled && canHavePerTestCoverage() && runner != null && runner.isCoverageByTestApplicable());
      }
    });

    myCoverageNotSupportedLabel = new JLabel(CodeInsightBundle.message("code.coverage.is.not.supported"));
    myCoverageNotSupportedLabel.setIcon(UIUtil.getOptionPanelWarningIcon());
    result.add(myCoverageNotSupportedLabel);
    return result;
  }

  private void enableTracingPanel(final boolean enabled) {
    myTracingRb.setEnabled(enabled);
    if (myTracingRb.isSelected() && !enabled) {
      mySamplingRb.setSelected(true);
    }
  }

  protected void disposeEditor() {}
}
