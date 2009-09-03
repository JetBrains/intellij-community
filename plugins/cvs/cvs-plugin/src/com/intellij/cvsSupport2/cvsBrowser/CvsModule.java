package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.openapi.project.Project;

import javax.swing.*;

/**
 * author: lesya
 */
public class CvsModule extends CvsElement{

  public CvsModule(Icon icon, Icon expandedIcon, Project project) {
    super(icon, expandedIcon, project);
  }

  public String createPathForChild(String name) {
    return name;
  }
}
