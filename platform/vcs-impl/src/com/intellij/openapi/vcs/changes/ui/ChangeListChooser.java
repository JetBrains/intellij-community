// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.function.Consumer;

public class ChangeListChooser extends DialogWrapper {
  private final Project myProject;
  private LocalChangeList mySelectedList;
  private final ChangeListChooserPanel myPanel;

  public ChangeListChooser(@NotNull Project project,
                           @NlsContexts.DialogTitle String title) {
    super(project, false);
    myProject = project;

    myPanel = new ChangeListChooserPanel(myProject, new Consumer<>() {
      @Override
      public void accept(final @Nullable @NlsContexts.DialogMessage String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage, myPanel);
      }
    });

    myPanel.init();
    setTitle(title);
    setSize(JBUI.scale(500), JBUI.scale(230));
    init();
  }

  public void setChangeLists(@Nullable Collection<? extends ChangeList> changelists) {
    myPanel.setChangeLists(changelists);
  }

  public void setDefaultSelection(@Nullable ChangeList defaultSelection) {
    myPanel.setDefaultSelection(defaultSelection);
  }

  public void setSuggestedName(@Nls @Nullable String suggestedName) {
    setSuggestedName(suggestedName, false);
  }

  public void setSuggestedName(@Nls @Nullable String suggestedName, boolean forceCreate) {
    if (suggestedName == null) return;
    myPanel.setSuggestedName(suggestedName, forceCreate);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Override
  protected @Nullable String getHelpId() {
    return "reference.dialogs.vcs.changelist.chooser";
  }

  @Override
  protected String getDimensionServiceKey() {
    return "VCS.ChangelistChooser";
  }

  @Override
  protected void doOKAction() {
    mySelectedList = myPanel.getSelectedList(myProject);
    if (mySelectedList != null) {
      super.doOKAction();
    }
  }

  public @Nullable LocalChangeList getSelectedList() {
    return mySelectedList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
