/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;

public abstract class CvsElementFactory {

  public abstract CvsElement createElement(String name, CvsEnvironment env, Project project);

  public static final CvsElementFactory FOLDER_ELEMENT_FACTORY = new CvsElementFactory(){
    public CvsElement createElement(String name, CvsEnvironment env, Project project) {
      return new CvsElement(name, AllIcons.Nodes.TreeClosed);
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
