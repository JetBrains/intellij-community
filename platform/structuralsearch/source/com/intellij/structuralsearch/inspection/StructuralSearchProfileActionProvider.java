// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ui.InspectionProfileActionProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchProfileActionProvider extends InspectionProfileActionProvider {

  @NotNull
  @Override
  public List<AnAction> getActions(SingleInspectionProfilePanel panel) {
    final InspectionProfileModifiableModel profile = panel.getProfile();
    if (profile.getToolsOrNull(SSBasedInspection.SHORT_NAME, null) != null &&
        !profile.isToolEnabled(HighlightDisplayKey.find(SSBasedInspection.SHORT_NAME))) {
      // enable SSBasedInspection if it was manually disabled
      profile.setToolEnabled(SSBasedInspection.SHORT_NAME, true);

      for (ScopeToolState tool : profile.getAllTools()) {
        final InspectionToolWrapper<?, ?> wrapper = tool.getTool();
        if (wrapper instanceof StructuralSearchInspectionToolWrapper) {
          tool.setEnabled(false);
        }
      }
    }

    for (ScopeToolState tool : profile.getAllTools()) {
      final InspectionToolWrapper<?, ?> wrapper = tool.getTool();
      if (wrapper instanceof StructuralSearchInspectionToolWrapper) {
        ((StructuralSearchInspectionToolWrapper)wrapper).setProfile(profile);
      }
    }

    final DefaultActionGroup actionGroup = new DefaultActionGroup(
      new AddTemplateAction(panel, false),
      new AddTemplateAction(panel, true)
    );
    actionGroup.setPopup(true);
    actionGroup.registerCustomShortcutSet(CommonShortcuts.INSERT, panel);
    final Presentation presentation = actionGroup.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.Add);
    presentation.setText(SSRBundle.messagePointer("add.inspection.button"));
    return Arrays.asList(actionGroup, new RemoveTemplateAction(panel));
  }

  private static class RemoveTemplateAction extends DumbAwareAction {

    private final SingleInspectionProfilePanel myPanel;

    private RemoveTemplateAction(SingleInspectionProfilePanel panel) {
      super(SSRBundle.message("remove.inspection.button"), null, AllIcons.General.Remove);
      myPanel = panel;
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myPanel);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myPanel.getSelectedTool() instanceof StructuralSearchInspectionToolWrapper);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final InspectionToolWrapper<?, ?> selectedTool = myPanel.getSelectedTool();
      final String shortName = selectedTool.getShortName();
      myPanel.removeSelectedRow();
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final SSBasedInspection inspection = getStructuralSearchInspection(profile);
      inspection.removeConfigurationWithUuid(UUID.fromString(shortName));
      profile.removeTool(shortName);
      profile.getProfileManager().fireProfileChanged(profile);
    }
  }

  private static class AddTemplateAction extends DumbAwareAction {

    private final SingleInspectionProfilePanel myPanel;
    private final boolean myReplace;

    private AddTemplateAction(SingleInspectionProfilePanel panel, boolean replace) {
      super(replace
            ? SSRBundle.message("SSRInspection.add.replace.template.button")
            : SSRBundle.message("SSRInspection.add.search.template.button"));
      myPanel = panel;
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final SearchContext context = new SearchContext(e.getDataContext());
      final StructuralSearchDialog dialog = new StructuralSearchDialog(context, myReplace, true);
      if (!dialog.showAndGet()) {
        return;
      }
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final Project project = e.getData(CommonDataKeys.PROJECT);
      assert project != null;
      final Configuration configuration = dialog.getConfiguration();
      if (!createNewInspection(configuration, project, profile)) {
        return;
      }
      myPanel.selectInspectionTool(configuration.getUuid().toString());
    }
  }

  static SSBasedInspection getStructuralSearchInspection(InspectionProfileImpl profile) {
    final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, (Project)null);
    assert wrapper != null;
    return (SSBasedInspection)wrapper.getTool();
  }

  public static void createNewInspection(@NotNull Configuration configuration, @NotNull Project project) {
    createNewInspection(configuration, project, InspectionProfileManager.getInstance(project).getCurrentProfile());
  }

  static boolean createNewInspection(@NotNull Configuration configuration,
                                     @NotNull Project project,
                                     @NotNull InspectionProfileImpl profile) {
    final SSBasedInspection inspection = getStructuralSearchInspection(profile);
    configuration.setUuidFromName();
    if (!saveInspection(project, inspection, configuration)) {
      return false;
    }
    addConfigurationToProfile(project, profile, configuration);
    profile.getProfileManager().fireProfileChanged(profile);
    return true;
  }

  private static void addConfigurationToProfile(@NotNull Project project, InspectionProfileImpl profile, Configuration configuration) {
    final String shortName = configuration.getUuid().toString();
    final InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(shortName, project);
    if (toolWrapper != null) {
      // already added
      return;
    }
    final StructuralSearchInspectionToolWrapper wrapped = new StructuralSearchInspectionToolWrapper(configuration);
    wrapped.setProfile(profile);
    profile.addTool(project, wrapped, null);

    // enable inspection even when profile is locked, because either:
    // - user just added this inspection explicitly
    // - or inspection was just imported from enabled old SSR inspection
    profile.setToolEnabled(shortName, true);
  }

  public static boolean saveInspection(Project project, SSBasedInspection inspection, Configuration configuration) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      inspection.addConfiguration(configuration);
      return true;
    }
    final InspectionDataDialog dialog = new InspectionDataDialog(project, inspection, configuration);
    final boolean result = dialog.showAndGet();
    if (result) {
      inspection.removeConfiguration(configuration);
      final String name = dialog.getName();
      for (Configuration c : inspection.getConfigurationsWithUuid(configuration.getUuid())) {
        c.setName(name);
      }
      configuration.setName(name);
      configuration.setDescription(dialog.getDescription());
      configuration.setSuppressId(dialog.getSuppressId());
      configuration.setProblemDescriptor(dialog.getProblemDescriptor());
      inspection.addConfiguration(configuration);
    }
    return result;
  }

  private static class InspectionDataDialog extends DialogWrapper {
    private final Pattern mySuppressIdPattern = Pattern.compile(LocalInspectionTool.VALID_ID_PATTERN);

    private final SSBasedInspection myInspection;
    @NotNull private final Configuration myConfiguration;
    private final JTextField myNameTextField;
    private final JTextField myProblemDescriptorTextField;
    private final EditorTextField myDescriptionTextArea;
    private final JTextField mySuppressIdTextField;

    InspectionDataDialog(Project project, SSBasedInspection inspection, Configuration configuration) {
      super(null);
      myInspection = inspection;

      myConfiguration = configuration;
      assert myConfiguration.getOrder() == 0;
      myNameTextField = new JTextField(configuration.getName());
      myProblemDescriptorTextField = new JTextField(configuration.getProblemDescriptor());
      myDescriptionTextArea = new EditorTextField(ObjectUtils.notNull(configuration.getDescription(), ""), project, StdFileTypes.HTML);
      myDescriptionTextArea.setOneLineMode(false);
      final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      myDescriptionTextArea.setFont(scheme.getFont(EditorFontType.PLAIN));
      myDescriptionTextArea.setPreferredSize(new Dimension(375, 125));
      myDescriptionTextArea.setMinimumSize(new Dimension(200, 50));
      mySuppressIdTextField = new JTextField(configuration.getNewSuppressId());
      setTitle(SSRBundle.message("meta.data.dialog.title"));
      init();
    }

    @Override
    protected @NotNull List<ValidationInfo> doValidateAll() {
      final List<ValidationInfo> result = new SmartList<>();
      final List<Configuration> configurations = myInspection.getConfigurations();
      final String name = getName();
      if (StringUtil.isEmpty(name)) {
        result.add(new ValidationInfo(SSRBundle.message("name.must.not.be.empty.warning"), myNameTextField));
      }
      else {
        for (Configuration configuration : configurations) {
          if (configuration.getOrder() == 0 && !configuration.equals(myConfiguration) && configuration.getName().equals(name)) {
            result.add(new ValidationInfo(SSRBundle.message("inspection.with.name.exists.warning", name), myNameTextField));
            break;
          }
        }
      }
      final String suppressId = getSuppressId();
      if (!StringUtil.isEmpty(suppressId)) {
        if (!mySuppressIdPattern.matcher(suppressId).matches()) {
          result.add(new ValidationInfo(SSRBundle.message("suppress.id.must.match.regex.warning"), mySuppressIdTextField));
        }
        else {
          final HighlightDisplayKey key = HighlightDisplayKey.findById(suppressId);
          if (key != null && key != HighlightDisplayKey.find(myConfiguration.getUuid().toString())) {
            result.add(new ValidationInfo(SSRBundle.message("suppress.id.in.use.warning", suppressId),
                                          mySuppressIdTextField));
          }
          else {
            for (Configuration configuration : configurations) {
              if (suppressId.equals(configuration.getNewSuppressId()) && !myConfiguration.getUuid().equals(configuration.getUuid())) {
                result.add(new ValidationInfo(SSRBundle.message("suppress.id.in.use.warning", suppressId),
                                              mySuppressIdTextField));
                break;
              }
            }
          }
        }
      }
      return result;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return new FormBuilder()
        .addLabeledComponent(SSRBundle.message("inspection.name.label"), myNameTextField, true)
        .addLabeledComponent(SSRBundle.message("problem.descriptor.label"), myProblemDescriptorTextField, true)
        .addLabeledComponentFillVertically(SSRBundle.message("description.label"), myDescriptionTextArea)
        .addLabeledComponent(SSRBundle.message("suppress.id.label"), mySuppressIdTextField)
        .getPanel();
    }

    public String getName() {
      return convertEmptyToNull(myNameTextField.getText().trim());
    }

    public String getDescription() {
      return convertEmptyToNull(myDescriptionTextArea.getText().trim());
    }

    public String getSuppressId() {
      return convertEmptyToNull(mySuppressIdTextField.getText().trim());
    }

    public String getProblemDescriptor() {
      return convertEmptyToNull(myProblemDescriptorTextField.getText().trim());
    }

    private static String convertEmptyToNull(String s) {
      return StringUtil.isEmpty(s) ? null : s;
    }
  }
}
