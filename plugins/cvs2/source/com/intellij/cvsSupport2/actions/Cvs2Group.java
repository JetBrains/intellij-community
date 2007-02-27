package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;

public class Cvs2Group extends StandardVcsGroup {
  public AbstractVcs getVcs(Project project) {
    return CvsVcs2.getInstance(project);
  }

  @Override
  protected boolean isVcsActive(final Project project) {
    return ProjectLevelVcsManager.getInstance(project).checkVcsIsActive("CVS");
  }
}
