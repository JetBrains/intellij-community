// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ui.CustomInspectionActions;
import com.intellij.profile.codeInspection.ui.InspectionMetaDataDialog;
import com.intellij.profile.codeInspection.ui.InspectionProfileActionProvider;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchProfileActionProvider extends InspectionProfileActionProvider {

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

  @Override
  public @Nullable AddInspectionActionGroup getAddActions(@NotNull SingleInspectionProfilePanel panel) {
    enableSSIfDisabled(panel.getProfile(), panel.getProject());
    final var group = new DefaultActionGroup(
      new AddInspectionAction(panel, SSRBundle.message("SSRInspection.add.search.template.button"), false),
      new AddInspectionAction(panel, SSRBundle.message("SSRInspection.add.replace.template.button"), true)
    );
    return new AddInspectionActionGroup(group, "ssr.profile.action.provider.add.group");
  }

  @Override
  public boolean canDeleteInspection(InspectionProfileEntry entry) {
    return entry instanceof SSBasedInspection;
  }

  @Override
  public void deleteInspection(InspectionProfileEntry entry, String shortName) {
    if (entry instanceof SSBasedInspection ssBasedInspection) {
      ssBasedInspection.removeConfigurationsWithUuid(shortName);
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
    final SSBasedInspection inspection = SSBasedInspection.getStructuralSearchInspection(profile);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      InspectionMetaDataDialog dialog = inspection.createMetaDataDialog(project, profile.getDisplayName(), null);
      if (configuration instanceof ReplaceConfiguration) {
        dialog.showCleanupOption(false);
      }
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
    CustomInspectionActions.fireProfileChanged(profile);
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
