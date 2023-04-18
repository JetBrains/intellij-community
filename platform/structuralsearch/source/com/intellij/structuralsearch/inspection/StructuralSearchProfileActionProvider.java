// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ui.InspectionMetaDataDialog;
import com.intellij.profile.codeInspection.ui.InspectionProfileActionProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.intellij.lang.regexp.inspection.custom.CustomRegExpInspection;
import org.intellij.lang.regexp.inspection.custom.CustomRegExpInspectionToolWrapper;
import org.intellij.lang.regexp.inspection.custom.RegExpDialog;
import org.intellij.lang.regexp.inspection.custom.RegExpInspectionConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
      final Project project = e.getData(CommonDataKeys.PROJECT);
      if (project == null) return;
      final InspectionMetaDataDialog metaDataDialog = inspection.createMetaDataDialog(project, null);
      if (!metaDataDialog.showAndGet()) return;

      final RegExpInspectionConfiguration configuration = new RegExpInspectionConfiguration(metaDataDialog.getName());
      configuration.addPattern(pattern);
      configuration.setDescription(metaDataDialog.getDescription());
      configuration.setSuppressId(metaDataDialog.getSuppressId());
      configuration.setProblemDescriptor(metaDataDialog.getProblemDescriptor());

      configuration.setUuid(null);
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
      InspectionMetaDataDialog dialog = inspection.createMetaDataDialog(project, null);
      if (!dialog.showAndGet()) return false;
      configuration.setOrder(0); // reset
      configuration.setName(dialog.getName());
      configuration.setDescription(dialog.getDescription());
      configuration.setProblemDescriptor(dialog.getProblemDescriptor());
      configuration.setSuppressId(dialog.getSuppressId());
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
}
