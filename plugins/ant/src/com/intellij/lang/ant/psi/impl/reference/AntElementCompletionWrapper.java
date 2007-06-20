package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.impl.AntElementImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class AntElementCompletionWrapper extends AntElementImpl implements PsiNamedElement {

  private final String myName;
  private final Project myProject;
  private final AntElementRole myRole;

  public AntElementCompletionWrapper(final String name, @NotNull final Project project, final AntElementRole role) {
    super(null, null);
    myName = name;
    myProject = project;
    myRole = role;
  }


  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (that instanceof AntElementCompletionWrapper) {
      return myName.equals(((AntElementCompletionWrapper)that).myName);
    }
    return false;
  }

  public int hashCode() {
    return myName.hashCode();
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

  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Can't rename ant completion element");  
  }
}
