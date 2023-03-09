// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
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
import org.intellij.lang.regexp.inspection.custom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchProfileActionProvider extends InspectionProfileActionProvider {

  @NotNull
  @Override
  public List<AnAction> getActions(@NotNull SingleInspectionProfilePanel panel) {
    enableSSIfDisabled(panel.getProfile(), panel.getProject());
    final DefaultActionGroup actionGroup = new DefaultActionGroup(
      new AddInspectionAction(panel, SSRBundle.message("SSRInspection.add.search.template.button"), false),
      new AddInspectionAction(panel, SSRBundle.message("SSRInspection.add.replace.template.button"), true),
      new AddCustomRegExpInspectionAction(panel, SSRBundle.message("action.add.regexp.search.inspection.text"), false),
      new AddCustomRegExpInspectionAction(panel, SSRBundle.message("action.add.regexp.replace.inspection.text"), true)
    );
    actionGroup.setPopup(true);
    actionGroup.registerCustomShortcutSet(CommonShortcuts.getNew(), panel);
    final Presentation presentation = actionGroup.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.Add);
    presentation.setText(SSRBundle.messagePointer("add.inspection.button"));
    return Arrays.asList(actionGroup, new RemoveInspectionAction(panel));
  }

  private static void enableSSIfDisabled(@NotNull InspectionProfileModifiableModel profile, @NotNull Project project) {
    if (profile.getToolsOrNull(SSBasedInspection.SHORT_NAME, null) != null &&
        !profile.isToolEnabled(HighlightDisplayKey.find(SSBasedInspection.SHORT_NAME))) {
      profile.setToolEnabled(SSBasedInspection.SHORT_NAME, true, project, false);

      for (ScopeToolState tool : profile.getAllTools()) {
        final InspectionToolWrapper<?, ?> wrapper = tool.getTool();
        if (wrapper instanceof StructuralSearchInspectionToolWrapper) {
          tool.setEnabled(false);
        }
      }
    }
  }

  private static final class RemoveInspectionAction extends DumbAwareAction {
    private final SingleInspectionProfilePanel myPanel;

    private RemoveInspectionAction(@NotNull SingleInspectionProfilePanel panel) {
      super(SSRBundle.message("remove.inspection.button"), null, AllIcons.General.Remove);
      myPanel = panel;
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myPanel);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      final InspectionToolWrapper<?, ?> selectedTool = myPanel.getSelectedTool();
      e.getPresentation().setEnabled(selectedTool instanceof CustomRegExpInspectionToolWrapper ||
                                     selectedTool instanceof StructuralSearchInspectionToolWrapper);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final InspectionToolWrapper<?, ?> selectedTool = myPanel.getSelectedTool();
      final String shortName = selectedTool.getShortName();
      final String mainToolId = selectedTool.getMainToolId();
      myPanel.removeSelectedRow();
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final InspectionProfileEntry inspection = InspectionProfileUtil.getInspection(profile, mainToolId);
      if (inspection instanceof SSBasedInspection ssBasedInspection) {
        ssBasedInspection.removeConfigurationsWithUuid(shortName);
      }
      else if (inspection instanceof CustomRegExpInspection customRegExpInspection) {
        customRegExpInspection.removeConfigurationWithUuid(shortName);
      }
      profile.removeTool(selectedTool);
      profile.setModified(true);
      InspectionProfileUtil.fireProfileChanged(profile);
    }
  }

  static final class AddCustomRegExpInspectionAction extends DumbAwareAction {
    private final SingleInspectionProfilePanel myPanel;
    private final boolean myReplace;

    AddCustomRegExpInspectionAction(@NotNull SingleInspectionProfilePanel panel, @NlsActions.ActionText String text, boolean replace) {
      super(text);
      myPanel = panel;
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final RegExpDialog dialog = new RegExpDialog(e.getProject(), true, myReplace ? RegExpInspectionConfiguration.InspectionPattern.EMPTY_REPLACE_PATTERN : null);
      if (myReplace) {
        // do something?
      }
      if (!dialog.showAndGet()) return;

      final RegExpInspectionConfiguration.InspectionPattern pattern = dialog.getPattern();
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final CustomRegExpInspection inspection = InspectionProfileUtil.getCustomRegExpInspection(profile);
      final RegExpInspectionConfiguration configuration = new RegExpInspectionConfiguration("new inspection");
      configuration.patterns.add(pattern);
      final Project project = getEventProject(e);
      final MetaDataDialog metaDataDialog = new MetaDataDialog(project, inspection, configuration, true);
      if (!metaDataDialog.showAndGet()) return;

      inspection.addConfiguration(configuration);
      CustomRegExpInspection.addInspectionToProfile(project, profile, configuration);
      profile.setModified(true);
      InspectionProfileUtil.fireProfileChanged(profile);
      myPanel.selectInspectionTool(configuration.getUuid());
    }
  }

  static final class AddInspectionAction extends DumbAwareAction {
    private final SingleInspectionProfilePanel myPanel;
    private final boolean myReplace;

    AddInspectionAction(@NotNull SingleInspectionProfilePanel panel, @NlsActions.ActionText String text, boolean replace) {
      super(text);
      myPanel = panel;
      myReplace = replace;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final SearchContext context = new SearchContext(e.getDataContext());

      final StructuralSearchDialog dialog = new StructuralSearchDialog(context, myReplace, true);
      if (!dialog.showAndGet()) return;

      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final Configuration configuration = dialog.getConfiguration();
      if (!createNewInspection(configuration, context.getProject(), profile)) return;

      myPanel.selectInspectionTool(configuration.getUuid());
    }
  }

  public static void createNewInspection(@NotNull Configuration configuration, @NotNull Project project) {
    createNewInspection(configuration, project, InspectionProfileManager.getInstance(project).getCurrentProfile());
  }

  public static boolean createNewInspection(@NotNull Configuration configuration,
                                             @NotNull Project project,
                                             @NotNull InspectionProfileImpl profile) {
    final SSBasedInspection inspection = InspectionProfileUtil.getStructuralSearchInspection(profile);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      configuration.setOrder(0); // reset
      configuration.setName(SSRBundle.message("new.template.defaultname"));
      configuration.setDescription("");
      configuration.setProblemDescriptor("");
      configuration.setSuppressId("");
      final InspectionDataDialog dialog = new InspectionDataDialog(project, inspection, configuration, true);
      if (!dialog.showAndGet()) return false;
    }
    configuration.setUuid(null);
    inspection.addConfiguration(configuration);
    addInspectionToProfile(project, profile, configuration);
    if (profile instanceof InspectionProfileModifiableModel) {
      ((InspectionProfileModifiableModel)profile).setModified(true);
    }
    InspectionProfileUtil.fireProfileChanged(profile);
    return true;
  }

  private static void addInspectionToProfile(@NotNull Project project,
                                             @NotNull InspectionProfileImpl profile,
                                             @NotNull Configuration configuration) {
    final String shortName = configuration.getUuid();
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

    InspectionDataDialog(Project project, @NotNull SSBasedInspection inspection, @NotNull Configuration configuration, boolean newInspection) {
      super((Project)null);
      myInspection = inspection;

      myConfiguration = configuration;
      myNewInspection = newInspection;
      assert myConfiguration.getOrder() == 0;
      myNameTextField = new JTextField(configuration.getName());
      myProblemDescriptorTextField = new JTextField(configuration.getProblemDescriptor());
      myDescriptionTextArea = new EditorTextField(ObjectUtils.notNull(configuration.getDescription(), ""), project, HtmlFileType.INSTANCE);
      myDescriptionTextArea.setOneLineMode(false);
      myDescriptionTextArea.setFont(EditorFontType.getGlobalPlainFont());
      myDescriptionTextArea.setPreferredSize(new Dimension(375, 125));
      myDescriptionTextArea.setMinimumSize(new Dimension(200, 50));
      mySuppressIdTextField = new JTextField(configuration.getSuppressId());
      setTitle(SSRBundle.message("meta.data.dialog.title"));
      init();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
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
          if (key != null && key != HighlightDisplayKey.find(myConfiguration.getUuid())) {
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

    public @NlsSafe String getName() {
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
