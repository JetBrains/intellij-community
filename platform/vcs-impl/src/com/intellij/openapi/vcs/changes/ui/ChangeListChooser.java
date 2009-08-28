package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import com.intellij.openapi.vcs.changes.ChangeListEditHandler;
import com.intellij.util.Consumer;
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
                           @Nullable final String defaultName) {
    super(project, false);
    myProject = project;

    ChangeListEditHandler handler = null;
    for (ChangeList changelist : changelists) {
      handler = ((LocalChangeListImpl)changelist).getEditHandler();
      if (handler != null) {
        break;
      }
    }

    myPanel = new ChangeListChooserPanel(null, new Consumer<Boolean>() {
      public void consume(final Boolean aBoolean) {
        setOKActionEnabled(aBoolean);
      }
    });

    myPanel.init(project);
    myPanel.setChangeLists(changelists);
    myPanel.setDefaultSelection(defaultSelection);

    setTitle(title);
    if (defaultName != null) {
      myPanel.setDefaultName(defaultName);
    }

    init();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
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

  public LocalChangeList getSelectedList() {
    return mySelectedList;
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
