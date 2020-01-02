// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * @author Bas Leijdekkers
 */
public class InspectionProvider implements StartupActivity.DumbAware {

  @Override
  public void runActivity(@NotNull Project project) {
    if (!Registry.is("ssr.separate.inspections")) return;
    addStructuralSearchInspectionsToProfiles(InspectionProfileManager.getInstance(), project);
    addStructuralSearchInspectionsToProfiles(InspectionProfileManager.getInstance(project), project);
  }

  private static void addStructuralSearchInspectionsToProfiles(InspectionProfileManager profileManager, @NotNull Project project) {
    for (InspectionProfileImpl profile : profileManager.getProfiles()) {
      final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, project);
      assert wrapper != null;
      final SSBasedInspection ssBasedInspection = (SSBasedInspection)wrapper.getTool();
      final HighlightDisplayKey key = HighlightDisplayKey.find(SSBasedInspection.SHORT_NAME);
      final boolean enabled = profile.isToolEnabled(key);
      for (Configuration configuration : ssBasedInspection.getConfigurations()) {
        addConfigurationToProfile(project, profile, configuration, enabled);
      }
    }
  }


  public static void addConfigurationToProfile(@NotNull Project project,
                                               InspectionProfileImpl profile,
                                               Configuration configuration) {
    addConfigurationToProfile(project, profile, configuration, true);
  }

  private static void addConfigurationToProfile(@NotNull Project project,
                                                InspectionProfileImpl profile,
                                                Configuration configuration, boolean enabled) {
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
      profile.setToolEnabled(configuration.getUuid().toString(), enabled);
    }
  }
}
