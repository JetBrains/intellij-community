package com.intellij.cvsSupport2.ui;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.project.Project;

public interface Options {
  boolean isToBeShown(Project project);

  void setToBeShown(boolean value, Project project, boolean onOk);

  Options ADD_ACTION = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsConfiguration.getInstance(project).SHOW_ADD_OPTIONS;
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsConfiguration.getInstance(project).SHOW_ADD_OPTIONS = value;
    }
  };

  Options ON_FILE_ADDING = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsConfiguration.getInstance(project).ON_FILE_ADDING
             == com.intellij.util.Options.SHOW_DIALOG;
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsConfiguration.getInstance(project).ON_FILE_ADDING = CvsConfiguration.convertToEnumValue(value, onOk);
    }

  };


  Options ON_FILE_REMOVING = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsConfiguration.getInstance(project).ON_FILE_REMOVING
             == com.intellij.util.Options.SHOW_DIALOG;
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsConfiguration.getInstance(project).ON_FILE_REMOVING = CvsConfiguration.convertToEnumValue(value, onOk);
    }
  };

  Options REMOVE_ACTION = new Options() {
    public boolean isToBeShown(Project project) {
      return CvsConfiguration.getInstance(project).SHOW_REMOVE_OPTIONS;
    }

    public void setToBeShown(boolean value, Project project, boolean onOk) {
      CvsConfiguration.getInstance(project).SHOW_REMOVE_OPTIONS = value;
    }
  };

}

