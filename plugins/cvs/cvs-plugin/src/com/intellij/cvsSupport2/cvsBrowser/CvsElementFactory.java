// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;

public abstract class CvsElementFactory {

  public abstract CvsElement createElement(String name, CvsEnvironment env, Project project);

  public static final CvsElementFactory FOLDER_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsElement(name, AllIcons.Nodes.Folder);
    }
  };

  public static final CvsElementFactory MODULE_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsModule(name, AllIcons.Nodes.Module);
    }
  };

  public static final CvsElementFactory FILE_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsFile(name, env, project);
    }
  };

}
