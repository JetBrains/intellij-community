// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.NullableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author max
 */
public class ChangeListChooser extends DialogWrapper {
  private final Project myProject;
  private LocalChangeList mySelectedList;
  private final ChangeListChooserPanel myPanel;

  public ChangeListChooser(@NotNull Project project,
                           @NotNull Collection<? extends ChangeList> changelists,
                           @Nullable ChangeList defaultSelection,
                           final String title,
                           @Nullable final String suggestedName) {
    super(project, false);
    myProject = project;

    myPanel = new ChangeListChooserPanel(myProject, new NullableConsumer<String>() {
      @Override
      public void consume(final @Nullable String errorMessage) {
        setOKActionEnabled(errorMessage == null);
        setErrorText(errorMessage, myPanel);
      }
    });

    myPanel.init();
    myPanel.setChangeLists(changelists);
    myPanel.setDefaultSelection(defaultSelection);

    setTitle(title);
    if (suggestedName != null) {
      myPanel.setSuggestedName(suggestedName);
    }

    init();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Nullable
  @Override
  protected String getHelpId() {
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

  @Nullable
  public LocalChangeList getSelectedList() {
    return mySelectedList;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
