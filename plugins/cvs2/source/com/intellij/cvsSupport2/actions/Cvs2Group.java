package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.actions.StandardVcsGroup;

public class Cvs2Group extends StandardVcsGroup {
  public AbstractVcs getVcs(Project project) {
    return CvsVcs2.getInstance(project);
  }

  @Override
  public String getVcsName(final Project project) {
    return "CVS";
  }
}
