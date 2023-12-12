// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.configurations.coverage;

import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.JavaCoverageEngine;
import com.intellij.coverage.JavaCoverageRunner;
import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.util.JreVersionDetector;
import com.intellij.icons.AllIcons;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Objects;

/**
 * Base {@link com.intellij.openapi.options.Configurable} for configuring code coverage
 * To obtain a full configurable use
 * <code>
 * SettingsEditorGroup<YourConfiguration> group = new SettingsEditorGroup<YourConfiguration>();
 * group.addEditor(title, yourConfigurable);
 * group.addEditor(title, yourCoverageConfigurable);
 * </code>
 */
public final class CoverageConfigurable extends SettingsEditor<RunConfigurationBase<?>> {
  private static final Logger LOG = Logger.getInstance(CoverageConfigurable.class);

  private final JreVersionDetector myVersionDetector = new JreVersionDetector();
  final Project myProject;
  private CoverageClassFilterEditor myClassFilterEditor;
  private CoverageClassFilterEditor myExcludeClassFilterEditor;
  private JLabel myCoverageNotSupportedLabel;
  private ComboBox<CoverageRunnerItem> myCoverageRunnerCb;
  private JPanel myRunnerPanel;
  private JCheckBox myTrackPerTestCoverageCb;
  private JCheckBox myTrackTestSourcesCb;

  private JCheckBox myBranchCoverageCb;
  private final RunConfigurationBase<?> myConfig;

  public CoverageConfigurable(RunConfigurationBase<?> config) {
    myConfig = config;
    myProject = config.getProject();
  }

  @Override
  protected void resetEditorFrom(@NotNull final RunConfigurationBase<?> runConfiguration) {
    final boolean isJre50;
    if (runConfiguration instanceof CommonJavaRunConfigurationParameters && myVersionDetector.isJre50Configured((CommonJavaRunConfigurationParameters)runConfiguration)) {
      isJre50 = true;
    } else if (runConfiguration instanceof ModuleBasedConfiguration){
      isJre50 = myVersionDetector.isModuleJre50Configured((ModuleBasedConfiguration<?, ?>)runConfiguration);
    } else {
      isJre50 = true;
    }

    myCoverageNotSupportedLabel.setVisible(!isJre50);

    final JavaCoverageEnabledConfiguration configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
    CoverageRunner runner = configuration.getCoverageRunner();
    if (runner != null) {
      myCoverageRunnerCb.setSelectedItem(new CoverageRunnerItem(runner));
    } else {
      final String runnerId = configuration.getRunnerId();
      if (runnerId != null){
        final CoverageRunnerItem runnerItem = new CoverageRunnerItem(runnerId);
        final DefaultComboBoxModel<CoverageRunnerItem> model = (DefaultComboBoxModel<CoverageRunnerItem>)myCoverageRunnerCb.getModel();
        if (model.getIndexOf(runnerItem) == -1) {
          model.addElement(runnerItem);
        }
        myCoverageRunnerCb.setSelectedItem(runnerItem);
      } else {
        myCoverageRunnerCb.setSelectedIndex(0);
      }
      runner = ((CoverageRunnerItem)Objects.requireNonNull(myCoverageRunnerCb.getSelectedItem())).getRunner();
    }
    myRunnerPanel.setEnabled(isJre50);

    myClassFilterEditor.setFilters(getCoveragePatterns(configuration, true));
    myExcludeClassFilterEditor.setFilters(getCoveragePatterns(configuration, false));
    setUpBranchCoverage(runner, configuration.isBranchCoverageEnabled(), configuration.isTrackPerTestCoverage());
    myTrackTestSourcesCb.setSelected(configuration.isTrackTestFolders());
  }

  private void setUpBranchCoverage(CoverageRunner runner, boolean branchCoverage, boolean testTracking) {
    if (runner instanceof JavaCoverageRunner javaRunner) {
      final boolean alwaysAvailable = javaRunner.isBranchInfoAvailable(false);
      final boolean neverAvailable = !javaRunner.isBranchInfoAvailable(true);
      myBranchCoverageCb.setEnabled(!(alwaysAvailable || neverAvailable));
      myBranchCoverageCb.setSelected(javaRunner.isBranchInfoAvailable(branchCoverage));
    } else {
      myBranchCoverageCb.setEnabled(true);
      myBranchCoverageCb.setSelected(branchCoverage);
    }

    final boolean isCoverageByTestApplicable = runner != null && runner.isCoverageByTestApplicable();
    myTrackPerTestCoverageCb.setSelected(testTracking);
    myTrackPerTestCoverageCb.setEnabled(isCoverageByTestApplicable && myBranchCoverageCb.isSelected() && canHavePerTestCoverage());
  }

  static ClassFilter[] getCoveragePatterns(@NotNull JavaCoverageEnabledConfiguration configuration, boolean include) {
    return Arrays.stream(ObjectUtils.chooseNotNull(configuration.getCoveragePatterns(), ClassFilter.EMPTY_ARRAY))
      .filter(classFilter -> classFilter.INCLUDE == include).toArray(ClassFilter[]::new);
  }

  private boolean canHavePerTestCoverage() {
    return CoverageEnabledConfiguration.getOrCreate(myConfig).canHavePerTestCoverage();
  }

  @Override
  protected void applyEditorTo(@NotNull final RunConfigurationBase runConfiguration) {
    final JavaCoverageEnabledConfiguration configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
    ClassFilter[] newCoveragePatterns = ArrayUtil.mergeArrays(myClassFilterEditor.getFilters(), myExcludeClassFilterEditor.getFilters());
    ClassFilter[] oldCoveragePatterns = ObjectUtils.chooseNotNull(configuration.getCoveragePatterns(), ClassFilter.EMPTY_ARRAY);
    //apply new order if something else was changed as well
    if (newCoveragePatterns.length != oldCoveragePatterns.length ||
        !ContainerUtil.newHashSet(newCoveragePatterns).equals(ContainerUtil.newHashSet(oldCoveragePatterns))) {
      configuration.setCoveragePatterns(newCoveragePatterns);
    }
    configuration.setCoverageRunner(getSelectedRunner());
    configuration.setTrackPerTestCoverage(myTrackPerTestCoverageCb.isSelected());
    configuration.setBranchCoverage(myBranchCoverageCb.isSelected());
    configuration.setTrackTestFolders(myTrackTestSourcesCb.isSelected());
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    JPanel result = new JPanel(new GridBagLayout());

    final DefaultComboBoxModel<CoverageRunnerItem> runnersModel = new DefaultComboBoxModel<>();
    myCoverageRunnerCb = new ComboBox<>(runnersModel);

    final JavaCoverageEnabledConfiguration javaCoverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(myConfig);
    LOG.assertTrue(javaCoverageEnabledConfiguration != null);
    final JavaCoverageEngine provider = JavaCoverageEngine.getInstance();
    for (CoverageRunner runner : CoverageRunner.EP_NAME.getExtensionList()) {
      if (runner.acceptsCoverageEngine(provider)) {
        runnersModel.addElement(new CoverageRunnerItem(runner));
      }
    }
    myCoverageRunnerCb.setRenderer(SimpleListCellRenderer.create("", CoverageRunnerItem::getPresentableName));
    myCoverageRunnerCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        setUpBranchCoverage(getSelectedRunner(), myBranchCoverageCb.isSelected(), myTrackPerTestCoverageCb.isSelected());
      }
    });
    myRunnerPanel = new JPanel(new GridBagLayout());
    myRunnerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    myRunnerPanel.add(new JLabel(JavaCoverageBundle.message("run.configuration.choose.coverage.runner")), new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.insetsRight(10), 0, 0));
    myRunnerPanel.add(myCoverageRunnerCb, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                                 JBInsets.emptyInsets(), 0, 0));
    final JPanel cPanel = new JPanel(new VerticalFlowLayout());

    myBranchCoverageCb = new JCheckBox(JavaCoverageBundle.message("run.configuration.coverage.branches"));
    final JPanel branchCoveragePanel = UI.PanelFactory.panel(myBranchCoverageCb)
      .withComment(JavaCoverageBundle.message("run.configuration.coverage.branches.comment"))
      .createPanel();
    cPanel.add(branchCoveragePanel);

    final ActionListener branchCoverageListener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        setUpBranchCoverage(getSelectedRunner(), myBranchCoverageCb.isSelected(), myTrackPerTestCoverageCb.isSelected());
      }
    };

    myBranchCoverageCb.addActionListener(branchCoverageListener);

    myTrackPerTestCoverageCb = new JCheckBox(JavaCoverageBundle.message("run.configuration.track.per.test.coverage"));
    final JBPanel<BorderLayoutPanel> testTrackingPanel = JBUI.Panels.simplePanel(myTrackPerTestCoverageCb).withBorder(JBUI.Borders.emptyLeft(15));
    cPanel.add(testTrackingPanel);
    myRunnerPanel.add(cPanel, new GridBagConstraints(0, 1, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                                                     JBInsets.emptyInsets(), 0, 0));

    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE,
                                                         1, 1, 1, 0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                         JBInsets.emptyInsets(), 0, 0);
    result.add(myRunnerPanel, gc);

    JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints bagConstraints =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                             JBInsets.emptyInsets(), 0, 0);
    //noinspection DialogTitleCapitalization
    panel.add(new TitledSeparator(JavaCoverageBundle.message("record.coverage.filters.title")), bagConstraints);
    myClassFilterEditor = new CoverageClassFilterEditor(myProject);
    panel.add(myClassFilterEditor, bagConstraints);

    //noinspection DialogTitleCapitalization
    panel.add(new TitledSeparator(JavaCoverageBundle.message("exclude.coverage.filters.title")), bagConstraints);
    myExcludeClassFilterEditor = new CoverageClassFilterEditor(myProject) {
      @NotNull
      @Override
      protected ClassFilter createFilter(String pattern) {
        ClassFilter filter = super.createFilter(pattern);
        filter.setInclude(false);
        return filter;
      }
    };
    panel.add(myExcludeClassFilterEditor, bagConstraints);

    bagConstraints.weighty = 0;
    myTrackTestSourcesCb = new JCheckBox(JavaCoverageBundle.message("run.configuration.enable.coverage.in.test.folders"));
    panel.add(myTrackTestSourcesCb, bagConstraints);

    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    result.add(panel, gc);

    myCoverageNotSupportedLabel = new JLabel(JavaCoverageBundle.message("code.coverage.is.not.supported"));
    myCoverageNotSupportedLabel.setIcon(AllIcons.General.WarningDialog);
    result.add(myCoverageNotSupportedLabel, gc);
    return result;
  }

  @Nullable
  private CoverageRunner getSelectedRunner() {
    final CoverageRunnerItem runnerItem = (CoverageRunnerItem)myCoverageRunnerCb.getSelectedItem();
    if (runnerItem == null) {
      LOG.debug("Available runners: " + myCoverageRunnerCb.getModel().getSize());
    }
    return runnerItem != null ? runnerItem.getRunner() : null;
  }

  private static final class CoverageRunnerItem {
    private CoverageRunner myRunner;
    private @NotNull final String myRunnerId;

    private CoverageRunnerItem(@NotNull CoverageRunner runner) {
      myRunner = runner;
      myRunnerId = runner.getId();
    }

    private CoverageRunnerItem(@NotNull String runnerId) {
      myRunnerId = runnerId;
    }

    public CoverageRunner getRunner() {
      return myRunner;
    }

    public String getPresentableName() {
      return myRunner != null ? myRunner.getPresentableName() : myRunnerId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CoverageRunnerItem that = (CoverageRunnerItem)o;

      if (!myRunnerId.equals(that.myRunnerId)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myRunnerId.hashCode();
    }
  }
}
