/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.commit;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.EventListener;

import static com.intellij.ui.GuiUtils.enableChildren;
import static java.util.Collections.singletonList;

public class CommitMessageInspectionDetails {
  @NotNull private final Project myProject;
  @NotNull private final InspectionProfileImpl myProfile;
  @NotNull private final ToolDescriptors myToolDescriptors;
  @NotNull private final ScopeToolState myToolState;
  private JPanel mySeverityChooserPanel;
  private LevelChooserAction mySeverityChooser;
  private JComponent myMainPanel;

  @NotNull private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public CommitMessageInspectionDetails(@NotNull Project project,
                                        @NotNull InspectionProfileImpl profile,
                                        @NotNull ToolDescriptors toolDescriptors) {
    myProject = project;
    myProfile = profile;
    myToolDescriptors = toolDescriptors;
    myToolState = myToolDescriptors.getDefaultDescriptor().getState();

    init();
  }

  @NotNull
  public JComponent getMainPanel() {
    return myMainPanel;
  }

  @NotNull
  public HighlightDisplayKey getKey() {
    return myToolDescriptors.getDefaultDescriptor().getKey();
  }

  public void update() {
    mySeverityChooser.setChosen(ScopesAndSeveritiesTable.getSeverity(singletonList(myToolState)));
    enableChildren(myToolState.isEnabled(), myMainPanel);
  }

  public void addListener(@NotNull ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  private void init() {
    mySeverityChooser = new MySeverityChooser(myProfile.getProfileManager().getOwnSeverityRegistrar());
    mySeverityChooserPanel.add(mySeverityChooser.createCustomComponent(mySeverityChooser.getTemplatePresentation()), BorderLayout.CENTER);

    JComponent options = myToolState.getAdditionalConfigPanel();
    if (options != null) {
      myMainPanel.add(options, createOptionsPanelConstraints());
    }
  }

  @NotNull
  private static GridConstraints createOptionsPanelConstraints() {
    GridConstraints result = new GridConstraints();

    result.setRow(1);
    result.setColumn(0);
    result.setRowSpan(1);
    result.setColSpan(2);
    result.setAnchor(GridConstraints.ANCHOR_NORTHWEST);
    result.setUseParentLayout(true);

    return result;
  }

  private class MySeverityChooser extends LevelChooserAction {
    public MySeverityChooser(@NotNull SeverityRegistrar registrar) {
      super(registrar);
    }

    @Override
    protected void onChosen(@NotNull HighlightSeverity severity) {
      HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);

      myProfile.setErrorLevel(myToolDescriptors.getDefaultDescriptor().getKey(), level, null, myProject);
      myEventDispatcher.getMulticaster().onSeverityChanged(severity);
    }

    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      return createComboBoxButton(presentation);
    }
  }

  public interface ChangeListener extends EventListener {
    void onSeverityChanged(@NotNull HighlightSeverity severity);
  }
}
