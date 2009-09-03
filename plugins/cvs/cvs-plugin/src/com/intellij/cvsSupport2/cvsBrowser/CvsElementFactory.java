package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.util.Icons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

import javax.swing.*;

public abstract class CvsElementFactory {
  private final static Icon TREE_OPEN = Icons.DIRECTORY_OPEN_ICON;
  private final static Icon TREE_CLOSE = Icons.DIRECTORY_CLOSED_ICON;
  private final static Icon MODULE_OPEN = IconLoader.getIcon("/nodes/ModuleOpen.png");
  private final static Icon MODULE_CLOSE = IconLoader.getIcon("/nodes/ModuleClosed.png");

  public abstract CvsElement createElement(String name, CvsEnvironment env, Project project);

  public static final CvsElementFactory FOLDER_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsElement(TREE_CLOSE,  TREE_OPEN, project);
    }
  };

  public static final CvsElementFactory MODULE_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsModule(MODULE_CLOSE,  MODULE_OPEN, project);
    }
  };

  public static final CvsElementFactory FILE_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsFile(name, env, project);
    }
  };

}
