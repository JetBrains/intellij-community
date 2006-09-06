package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.impl.AntElementImpl;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AntElementCompletionWrapper extends AntElementImpl {

  private final String myName;
  private final Project myProject;
  private final AntElementRole myRole;

  public AntElementCompletionWrapper(final String name, @NotNull final Project project, final AntElementRole role) {
    super(null, null);
    myName = name;
    myProject = project;
    myRole = role;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public AntElementRole getRole() {
    return myRole;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isPhysical() {
    return false;
  }
}
