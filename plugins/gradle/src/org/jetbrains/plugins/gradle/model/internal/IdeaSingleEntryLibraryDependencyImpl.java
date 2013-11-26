/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.internal;

import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;

import java.io.File;
import java.io.Serializable;

/**
 * @author Vladislav.Soroka
 * @since 11/25/13
 */
public class IdeaSingleEntryLibraryDependencyImpl extends AbstractGradleDependency
  implements IdeaSingleEntryLibraryDependency, Serializable {

  private File myFile;
  private File mySource;
  private File myJavadoc;

  public IdeaSingleEntryLibraryDependencyImpl(IdeaDependencyScope myScope,
                                              String dependencyName,
                                              String dependencyGroup, String dependencyVersion) {
    super(myScope, dependencyName, dependencyGroup, dependencyVersion);
  }

  @Override
  public File getFile() {
    return myFile;
  }

  @Override
  public File getSource() {
    return mySource;
  }

  @Override
  public File getJavadoc() {
    return myJavadoc;
  }

  public void setFile(File file) {
    myFile = file;
  }

  public void setSource(File source) {
    mySource = source;
  }

  public void setJavadoc(File javadoc) {
    myJavadoc = javadoc;
  }

  @Override
  public GradleModuleVersion getGradleModuleVersion() {
    return null;
  }
}
