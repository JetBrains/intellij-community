/*
 * @author max
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.project.Project;

import java.util.List;

public class TooManyProjectLeakedException extends RuntimeException {
  private final List<Project> leakedProjects;

  public TooManyProjectLeakedException(List<Project> leakedProjects) {
    this.leakedProjects = leakedProjects;
  }

  public List<Project> getLeakedProjects() {
    return leakedProjects;
  }
}
