/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 2/18/2017
 */
@Tag("build")
public class BuildParticipant implements Serializable {
  private String myRootProjectName;
  private String myRootPath;
  @NotNull private Set<String> myProjects = new HashSet<>();

  @Attribute("name")
  public String getRootProjectName() {
    return myRootProjectName;
  }

  public void setRootProjectName(String rootProjectName) {
    myRootProjectName = rootProjectName;
  }

  @Attribute("path")
  public String getRootPath() {
    return myRootPath;
  }

  public void setRootPath(String rootPath) {
    myRootPath = rootPath;
  }

  @AbstractCollection(surroundWithTag = false, elementTag = "project", elementValueAttribute = "path")
  @OptionTag(tag = "projects", nameAttribute = "")
  @NotNull
  public Set<String> getProjects() {
    return myProjects;
  }

  public void setProjects(@NotNull Set<String> projects) {
    myProjects = projects;
  }

  public BuildParticipant copy() {
    BuildParticipant result = new BuildParticipant();
    result.myRootPath = myRootPath;
    result.myProjects = new HashSet<>(myProjects);
    return result;
  }
}
