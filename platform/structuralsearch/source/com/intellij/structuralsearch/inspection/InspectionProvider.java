// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.inspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.inspection.highlightTemplate.StructuralSearchFakeInspection;
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
    addStructuralSearchInspectionsToProfiles(ApplicationInspectionProfileManager.getInstanceImpl(), project);
    addStructuralSearchInspectionsToProfiles(ProjectInspectionProfileManager.getInstance(project), project);
  }

  private static void addStructuralSearchInspectionsToProfiles(InspectionProfileManager profileManager, @NotNull Project project) {
    final InspectionProfileImpl baseProfile = InspectionProfileKt.getBASE_PROFILE();
    for (InspectionProfileImpl profile : profileManager.getProfiles()) {
      final InspectionToolWrapper<?, ?> wrapper = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, project);
      assert wrapper != null;
      final SSBasedInspection ssBasedInspection = (SSBasedInspection)wrapper.getTool();
      final HighlightDisplayKey key = HighlightDisplayKey.find(SSBasedInspection.SHORT_NAME);
      final boolean enabled = profile.isToolEnabled(key);
      for (Configuration configuration : ssBasedInspection.getConfigurations()) {
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
          profile.setToolEnabled(configuration.getUuid().toString(), enabled);
          baseProfile.addTool(project, wrapped, null);
        }
      }
    }
  }

  private static class StructuralSearchInspectionToolWrapper extends LocalInspectionToolWrapper {
    StructuralSearchInspectionToolWrapper(Configuration configuration) {
      super(new StructuralSearchFakeInspection(configuration.getName(), configuration.getUuid()));
    }

    private StructuralSearchInspectionToolWrapper(@NotNull LocalInspectionTool tool) {
      super(tool);
    }

    @NotNull
    @Override
    public LocalInspectionToolWrapper createCopy() {
      return new StructuralSearchInspectionToolWrapper(new StructuralSearchFakeInspection((StructuralSearchFakeInspection)getTool()));
    }

    @Override
    public void initialize(@NotNull GlobalInspectionContext context) {
      super.initialize(context);
      final InspectionProfileImpl profile = ((GlobalInspectionContextBase)context).getCurrentProfile();
      final InspectionToolWrapper<?, ?> tool = profile.getInspectionTool(SSBasedInspection.SHORT_NAME, context.getProject());
      assert tool != null;
      final SSBasedInspection inspection = (SSBasedInspection)tool.getTool();
      inspection.setSessionProfile(profile);
    }
  }
}
