/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.util.AbstractSDK;

import javax.swing.*;

/**
 * @author ilyas
 */
public class GroovySDK implements AbstractSDK {

  protected Library myLibrary;
  private final boolean myProjectLib;
  protected String mySdkVersion;
  private Module myModule;

  public GroovySDK(final Library library, final Module module, final boolean isProjectLib) {
    myModule = module;
    myLibrary = library;
    myProjectLib = isProjectLib;
    mySdkVersion = GroovyConfigUtils.getGroovyLibVersion(library);
  }

  public Module getModule() {
    return myModule;
  }

  public Project getProject() {
    return myModule.getProject();
  }

  public Library getLibrary() {
    return myLibrary;
  }

  public String getSdkVersion() {
    return mySdkVersion;
  }

  public String getLibraryName() {
    return myLibrary.getName();
  }

  public boolean isProjectLib() {
    return myProjectLib;
  }

  public String getPresentation() {
    return " (Groovy version \"" + getSdkVersion() + "\")" + (isProjectLib() ? " [" + getModule().getProject().getName() + "]" : "");
  }

  public Icon getIcon() {
    return GroovyIcons.GROOVY_SDK;
  }
}
