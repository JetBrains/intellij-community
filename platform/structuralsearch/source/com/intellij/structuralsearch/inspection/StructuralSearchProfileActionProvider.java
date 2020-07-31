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
import java.util.Collections;
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

    final DefaultActionGroup actionGroup = new DefaultActionGroup(
      new AddInspectionAction(panel, false),
      new AddInspectionAction(panel, true)
    );
    actionGroup.setPopup(true);
    actionGroup.registerCustomShortcutSet(CommonShortcuts.INSERT, panel);
    final Presentation presentation = actionGroup.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.Add);
    presentation.setText(SSRBundle.messagePointer("add.inspection.button"));
    return Arrays.asList(actionGroup, new RemoveInspectionAction(panel));
  }

  private static class RemoveInspectionAction extends DumbAwareAction {

    private final SingleInspectionProfilePanel myPanel;

    private RemoveInspectionAction(SingleInspectionProfilePanel panel) {
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
      final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
      inspection.removeConfigurationsWithUuid(UUID.fromString(shortName));
      profile.removeTool(shortName);
      InspectionProfileUtil.fireProfileChanged(profile);
    }
  }

  private static class AddInspectionAction extends DumbAwareAction {

    private final SingleInspectionProfilePanel myPanel;
    private final boolean myReplace;

    private AddInspectionAction(SingleInspectionProfilePanel panel, boolean replace) {
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
      configuration.setOrder(0); // reset
      configuration.setName(SSRBundle.message("new.template.defaultname"));
      configuration.setDescription("");
      configuration.setProblemDescriptor("");
      configuration.setSuppressId("");
      if (!createNewInspection(configuration, project, profile)) {
        return;
      }
      myPanel.selectInspectionTool(configuration.getUuid().toString());
    }
  }

  public static void createNewInspection(@NotNull Configuration configuration, @NotNull Project project) {
    createNewInspection(configuration, project, InspectionProfileManager.getInstance(project).getCurrentProfile());
  }

  static boolean createNewInspection(@NotNull Configuration configuration,
                                     @NotNull Project project,
                                     @NotNull InspectionProfileImpl profile) {
    final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      final InspectionDataDialog dialog = new InspectionDataDialog(project, inspection, configuration, true);
      if (!dialog.showAndGet()) return false;
    }
    configuration.setUuid(null);
    inspection.addConfiguration(configuration);
    addInspectionToProfile(project, profile, configuration);
    InspectionProfileUtil.fireProfileChanged(profile);
    profile.getProfileManager().fireProfileChanged(profile);
    return true;
  }

  private static void addInspectionToProfile(@NotNull Project project, InspectionProfileImpl profile, Configuration configuration) {
    final String shortName = configuration.getUuid().toString();
    final InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(shortName, project);
    if (toolWrapper != null) {
      // already added
      return;
    }
    final StructuralSearchInspectionToolWrapper wrapped =
      new StructuralSearchInspectionToolWrapper(Collections.singletonList(configuration));
    profile.addTool(project, wrapped, null);

    // enable inspection even when profile is locked, because either:
    // - user just added this inspection explicitly
    // - or inspection was just imported from enabled old SSR inspection
    profile.setToolEnabled(shortName, true);
  }

  static class InspectionDataDialog extends DialogWrapper {
    private final Pattern mySuppressIdPattern = Pattern.compile(LocalInspectionTool.VALID_ID_PATTERN);

    private final SSBasedInspection myInspection;
    @NotNull private final Configuration myConfiguration;
    private final boolean myNewInspection;
    private final JTextField myNameTextField;
    private final JTextField myProblemDescriptorTextField;
    private final EditorTextField myDescriptionTextArea;
    private final JTextField mySuppressIdTextField;

    InspectionDataDialog(Project project, SSBasedInspection inspection, Configuration configuration, boolean newInspection) {
      super(null);
      myInspection = inspection;

      myConfiguration = configuration;
      myNewInspection = newInspection;
      assert myConfiguration.getOrder() == 0;
      myNameTextField = new JTextField(configuration.getName());
      myProblemDescriptorTextField = new JTextField(configuration.getProblemDescriptor());
      myDescriptionTextArea = new EditorTextField(ObjectUtils.notNull(configuration.getDescription(), ""), project, StdFileTypes.HTML);
      myDescriptionTextArea.setOneLineMode(false);
      final EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      myDescriptionTextArea.setFont(scheme.getFont(EditorFontType.PLAIN));
      myDescriptionTextArea.setPreferredSize(new Dimension(375, 125));
      myDescriptionTextArea.setMinimumSize(new Dimension(200, 50));
      mySuppressIdTextField = new JTextField(configuration.getSuppressId());
      setTitle(SSRBundle.message("meta.data.dialog.title"));
      init();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myNameTextField;
    }

    @Override
    protected @NotNull List<ValidationInfo> doValidateAll() {
      final List<ValidationInfo> warnings = new SmartList<>();
      final List<Configuration> configurations = myInspection.getConfigurations();
      final String name = getName();
      if (StringUtil.isEmpty(name)) {
        warnings.add(new ValidationInfo(SSRBundle.message("name.must.not.be.empty.warning"), myNameTextField));
      }
      else {
        for (Configuration configuration : configurations) {
          if (configuration.getOrder() == 0) {
            if (myNewInspection) {
              if (configuration.getName().equals(name)) {
                warnings.add(new ValidationInfo(SSRBundle.message("inspection.with.name.exists.warning", name), myNameTextField));
                break;
              }
            } else if (!configuration.getUuid().equals(myConfiguration.getUuid()) && configuration.getName().equals(name)) {
              warnings.add(new ValidationInfo(SSRBundle.message("inspection.with.name.exists.warning", name), myNameTextField));
              break;
            }
          }
        }
      }
      final String suppressId = getSuppressId();
      if (!StringUtil.isEmpty(suppressId)) {
        if (!mySuppressIdPattern.matcher(suppressId).matches()) {
          warnings.add(new ValidationInfo(SSRBundle.message("suppress.id.must.match.regex.warning"), mySuppressIdTextField));
        }
        else {
          final HighlightDisplayKey key = HighlightDisplayKey.findById(suppressId);
          if (key != null && key != HighlightDisplayKey.find(myConfiguration.getUuid().toString())) {
            warnings.add(new ValidationInfo(SSRBundle.message("suppress.id.in.use.warning", suppressId), mySuppressIdTextField));
          }
          else {
            for (Configuration configuration : configurations) {
              if (suppressId.equals(configuration.getSuppressId()) && !myConfiguration.getUuid().equals(configuration.getUuid())) {
                warnings.add(new ValidationInfo(SSRBundle.message("suppress.id.in.use.warning", suppressId), mySuppressIdTextField));
                break;
              }
            }
          }
        }
      }
      return warnings;
    }

    @Override
    protected void doOKAction() {
      super.doOKAction();
      if (getOKAction().isEnabled()) {
        myConfiguration.setName(getName());
        myConfiguration.setDescription(getDescription());
        myConfiguration.setSuppressId(getSuppressId());
        myConfiguration.setProblemDescriptor(getProblemDescriptor());
      }
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
      return myNameTextField.getText().trim();
    }

    @Nullable
    public String getDescription() {
      return convertEmptyToNull(myDescriptionTextArea.getText());
    }

    @Nullable
    public String getSuppressId() {
      return convertEmptyToNull(mySuppressIdTextField.getText());
    }

    @Nullable
    public String getProblemDescriptor() {
      return convertEmptyToNull(myProblemDescriptorTextField.getText());
    }

    private static String convertEmptyToNull(String s) {
      return StringUtil.isEmpty(s.trim()) ? null : s;
    }
  }
}
