package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;

public class PsiClassLocator implements PsiLocator {
  private final String myName;
  private final String myPackage;

  private PsiClassLocator(final String name, final String aPackage) {
    myName = name;
    myPackage = aPackage;
  }

  public static PsiClassLocator fromQualifiedName(final String name) {
    final int lastDot = name.lastIndexOf('.');
    if (lastDot == -1 || lastDot == name.length())
      return new PsiClassLocator(name, "");
    else
      return new PsiClassLocator(name.substring(lastDot + 1), name.substring(0, lastDot));
  }

  public Location<PsiClass> getLocation(final Project project) {
    return PsiLocation.fromClassQualifiedName(project, getQualifiedName());
  }

  public String getPackage() {
    return myPackage;
  }

  public String getName() {
    return myName;
  }

  public String getQualifiedName() {
    return (myPackage.length() > 0 ? myPackage + "." : "") + myName;
  }
}
