package com.intellij.cvsSupport2.ui;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnVirtualFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDateImpl;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.FilePath;

import javax.swing.*;
import java.util.Collections;

/**
 * author: lesya
 */
public class SelectFileVersionDialog extends DialogWrapper {
  private final DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;
  private final Project myProject;

  public SelectFileVersionDialog(FilePath file, Project project) {
    super(true);
    myProject = project;
    myDateOrRevisionOrTagSettings =
    new DateOrRevisionOrTagSettings(new TagsProviderOnVirtualFiles(Collections.singleton(file)),
                                    myProject, false);

    myDateOrRevisionOrTagSettings.updateFrom(CvsConfiguration.getInstance(project).SHOW_CHANGES_REVISION_SETTINGS);
    setTitle("Select Tag or Date");
    init();
  }

  protected void doOKAction() {
    try {
      myDateOrRevisionOrTagSettings.saveTo(CvsConfiguration.getInstance(myProject).SHOW_CHANGES_REVISION_SETTINGS);
    }
    finally {
      super.doOKAction();
    }
  }

  protected JComponent createCenterPanel() {
    return myDateOrRevisionOrTagSettings.getPanel();
  }

  public RevisionOrDate getRevisionOrDate() {
    return RevisionOrDateImpl.createOn(CvsConfiguration.getInstance(myProject).SHOW_CHANGES_REVISION_SETTINGS);
  }
}
