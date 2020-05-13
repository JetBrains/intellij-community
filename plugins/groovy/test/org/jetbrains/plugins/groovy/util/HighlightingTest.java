// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public interface HighlightingTest extends BaseTest {

  @Nullable
  default String getTestName() {
    return null;
  }

  @NotNull
  default Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return Collections.emptyList();
  }

  default void enableInspectionAsWarning(@NotNull LocalInspectionTool inspection) {
    getFixture().enableInspections(inspection);
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspection.getShortName());
    final Project project = getProject();
    final InspectionProfileImpl profile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    profile.setErrorLevel(key, HighlightDisplayLevel.WARNING, project);
  }

  default void fileHighlightingTest(Class<? extends LocalInspectionTool>... inspections) {
    fileHighlightingTest(getTestName() + ".groovy", inspections);
  }

  default void fileHighlightingTest(@NotNull String filePath, Class<? extends LocalInspectionTool>... inspections) {
    getFixture().enableInspections(inspections);
    getFixture().enableInspections(getInspections());
    getFixture().testHighlighting(filePath);
  }

  default void highlightingTest(String text) {
    getFixture().enableInspections(getInspections());
    configureByText(text);
    getFixture().checkHighlighting();
  }

  default void highlightingTest(String text, LocalInspectionTool... inspections) {
    getFixture().enableInspections(inspections);
    highlightingTest(text);
  }

  default void highlightingTest(String text, Class<? extends LocalInspectionTool>... inspections) {
    getFixture().enableInspections(inspections);
    highlightingTest(text);
  }
}
