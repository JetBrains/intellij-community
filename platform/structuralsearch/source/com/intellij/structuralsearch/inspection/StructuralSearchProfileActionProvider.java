// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInspection.ex.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.profile.codeInspection.ui.InspectionProfileActionProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchProfileActionProvider extends InspectionProfileActionProvider {

  @NotNull
  @Override
  public List<AnAction> getActions(SingleInspectionProfilePanel panel) {
    if (!Registry.is("ssr.separate.inspections")) return Collections.emptyList();

    final InspectionProfileModifiableModel profile = panel.getProfile();
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
    presentation.setText("Add Structural Search && Replace Inspection");
    return Arrays.asList(actionGroup, new RemoveTemplateAction(panel));
  }

  private static class RemoveTemplateAction extends DumbAwareAction {

    private final SingleInspectionProfilePanel myPanel;

    private RemoveTemplateAction(SingleInspectionProfilePanel panel) {
      super("Remove Structural Search && Replace Inspection", null, AllIcons.General.Remove);
      myPanel = panel;
      registerCustomShortcutSet(CommonShortcuts.getDelete(), myPanel);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myPanel.getSelectedTool() instanceof StructuralSearchInspectionToolWrapper);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final String shortName = myPanel.getSelectedTool().getShortName();
      myPanel.removeSelectedRow();
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
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
      if (!dialog.showAndGet()) return;
      final InspectionProfileModifiableModel profile = myPanel.getProfile();
      final Project project = e.getData(CommonDataKeys.PROJECT);
      assert project != null;
      final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, project);
      assert wrapper != null;
      final SSBasedInspection inspection = (SSBasedInspection)wrapper.getTool();
      final Configuration configuration = dialog.getConfiguration();

      if (!ConfigurationManager.showSaveTemplateAsDialog(inspection.getConfigurations(), configuration, project)) {
        return;
      }
      addConfigurationToProfile(project, profile, configuration);
      profile.getProfileManager().fireProfileChanged(profile);
      myPanel.selectInspectionTool(configuration.getUuid().toString());
    }

    private static void addConfigurationToProfile(@NotNull Project project,
                                                  InspectionProfileImpl profile,
                                                  Configuration configuration) {
      final UUID uuid = configuration.getUuid();
      final InspectionToolWrapper<?, ?> toolWrapper;
      if (uuid == null) {
        configuration.setUuid(UUID.randomUUID());
        toolWrapper = null;
      }
      else {
        toolWrapper = profile.getInspectionTool(configuration.getUuid().toString(), project);
      }
      if (toolWrapper == null) {
        final LocalInspectionToolWrapper wrapped = new StructuralSearchInspectionToolWrapper(configuration);
        profile.addTool(project, wrapped, null);

        // enable inspection even when profile is locked, because either:
        // - user just added this inspection explicitly
        // - or inspection was just imported from enabled old SSR inspection
        profile.setToolEnabled(configuration.getUuid().toString(), true);
      }
    }
  }
}
