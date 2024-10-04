// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.table.ScopesAndSeveritiesTable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;

import static com.intellij.ui.GuiUtils.enableChildren;
import static java.util.Collections.singletonList;

@ApiStatus.Internal
public class CommitMessageInspectionDetails implements UnnamedConfigurable, Disposable {
  @NotNull private final Project myProject;
  @NotNull private final InspectionProfileImpl myProfile;
  @NotNull private final Descriptor myDefaultDescriptor;
  @NotNull private final ScopeToolState myToolState;
  private final LevelChooserAction mySeverityChooser;
  private final ConfigurableUi<Project> myOptionsConfigurable;
  private final CommitMessageInspectionDetailsPanel myMainPanel;

  @NotNull private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public CommitMessageInspectionDetails(@NotNull Project project,
                                        @NotNull InspectionProfileImpl profile,
                                        @NotNull Descriptor defaultDescriptor) {
    myProject = project;
    myProfile = profile;
    myDefaultDescriptor = defaultDescriptor;
    myToolState = myDefaultDescriptor.getState();

    mySeverityChooser = new MySeverityChooser(myProfile.getProfileManager().getSeverityRegistrar());
    JComponent severityPanel = mySeverityChooser.createCustomComponent(mySeverityChooser.getTemplatePresentation(), ActionPlaces.UNKNOWN);

    BaseCommitMessageInspection tool = ObjectUtils.tryCast(myToolState.getTool().getTool(), BaseCommitMessageInspection.class);
    myOptionsConfigurable = tool != null ? tool.createOptionsConfigurable() : null;
    JComponent options = myOptionsConfigurable != null ? myOptionsConfigurable.getComponent() : myToolState.getAdditionalConfigPanel(this, project);

    myMainPanel = new CommitMessageInspectionDetailsPanel(severityPanel, options);

    reset();
  }

  @NotNull
  public HighlightDisplayKey getKey() {
    return myDefaultDescriptor.getKey();
  }

  public void update() {
    mySeverityChooser.setChosen(ScopesAndSeveritiesTable.getSeverity(singletonList(myToolState)));
    enableChildren(myToolState.isEnabled(), myMainPanel.getComponent());
  }

  public void addListener(@NotNull ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    return myMainPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myOptionsConfigurable != null && myOptionsConfigurable.isModified(myProject);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myOptionsConfigurable != null) {
      myOptionsConfigurable.apply(myProject);
    }
  }

  @Override
  public void reset() {
    if (myOptionsConfigurable != null) {
      myOptionsConfigurable.reset(myProject);
    }
  }

  @Override
  public void dispose() {
    if (myOptionsConfigurable instanceof Disposable) {
      Disposer.dispose((Disposable)myOptionsConfigurable);
    }
  }

  private class MySeverityChooser extends LevelChooserAction {
    MySeverityChooser(@NotNull SeverityRegistrar registrar) {
      super(registrar);
    }

    @Override
    protected void onChosen(@NotNull HighlightSeverity severity) {
      HighlightDisplayLevel level = HighlightDisplayLevel.find(severity);

      myProfile.setErrorLevel(myDefaultDescriptor.getKey(), level, null, myProject);
      myEventDispatcher.getMulticaster().onSeverityChanged(severity);
    }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      return createComboBoxButton(presentation);
    }
  }

  public interface ChangeListener extends EventListener {
    void onSeverityChanged(@NotNull HighlightSeverity severity);
  }
}
