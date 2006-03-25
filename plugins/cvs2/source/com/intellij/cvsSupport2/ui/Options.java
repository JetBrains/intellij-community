package com.intellij.cvsSupport2.ui;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;

public interface Options {
  boolean isToBeShown(Project project);

  void setToBeShown(boolean value, Project project, boolean onOk);

  Options ADD_ACTION = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getAddOptions().getValue();
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getAddOptions().setValue(value);
    }
  };

  Options ON_FILE_ADDING = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getAddConfirmation().getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getAddConfirmation().setValue(CvsConfiguration.convertToEnumValue(value, onOk));
    }

  };


  Options ON_FILE_REMOVING = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getRemoveConfirmation().getValue() == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION;
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getRemoveConfirmation().setValue(CvsConfiguration.convertToEnumValue(value, onOk));
    }
  };

  Options REMOVE_ACTION = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsVcs2.getInstance(project).getRemoveOptions().getValue();
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsVcs2.getInstance(project).getRemoveOptions().setValue(value);
    }
  };

  Options NULL = new Options() {
    public boolean isToBeShown(Project project) { return true; }
    public void setToBeShown(boolean value, Project project, boolean onOk) {}
  };
}

