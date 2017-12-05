/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.dialogs.vcs.changelist.chooser";
  }

  protected String getDimensionServiceKey() {
    return "VCS.ChangelistChooser";
  }

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

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
