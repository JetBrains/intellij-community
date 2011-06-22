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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

public abstract class CvsElementFactory {
  private final static Icon TREE_OPEN = PlatformIcons.DIRECTORY_OPEN_ICON;
  private final static Icon TREE_CLOSE = PlatformIcons.DIRECTORY_CLOSED_ICON;
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
