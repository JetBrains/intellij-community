// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
@Tag("build")
public class BuildParticipant implements Serializable {
  private String myRootProjectName;
  private String myRootPath;
  private String myParentRootPath;
  private @NotNull Set<String> myProjects = new HashSet<>();

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

  @Attribute("parentPath")
  public String getParentRootPath() {
    return myParentRootPath;
  }

  public void setParentRootPath(String parentRootPath) {
    myParentRootPath = parentRootPath;
  }

  @XCollection(propertyElementName = "projects", elementName = "project", valueAttributeName = "path")
  public @NotNull Set<String> getProjects() {
    return myProjects;
  }

  public void setProjects(@NotNull Set<String> projects) {
    myProjects = projects;
  }

  public BuildParticipant copy() {
    BuildParticipant result = new BuildParticipant();
    result.myRootProjectName = myRootProjectName;
    result.myRootPath = myRootPath;
    result.myProjects = new HashSet<>(myProjects);
    return result;
  }
}
