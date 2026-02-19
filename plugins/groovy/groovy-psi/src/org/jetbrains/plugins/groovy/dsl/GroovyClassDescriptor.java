// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

public class GroovyClassDescriptor {
  static {
    try {
      GroovyCategorySupport.getCategoryNameUsage("aaa");
    }
    catch (NoSuchMethodError e) {
      throw new RuntimeException("Incompatible Groovy JAR in classpath: " + GroovyCategorySupport.class.getResource("/") + ", please remove it");
    }
  }

  private final Project myProject;
  private final PsiType myPsiType;
  private final PsiClass myPsiClass;
  private final PsiElement myPlace;
  private final PsiFile myFile;

  final Set<Factor> affectingFactors = EnumSet.noneOf(Factor.class);

  public GroovyClassDescriptor(
    @NotNull PsiType psiType,
    @NotNull PsiClass aClass,
    @NotNull PsiElement place,
    @NotNull PsiFile placeFile
  ) {
    myProject = placeFile.getProject();
    myPsiType = psiType;
    myPsiClass = aClass;
    myPlace = place;
    myFile = placeFile;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull GlobalSearchScope getResolveScope() {
    //affectingFactors.add(Factor.placeFile);
    return myPlace.getResolveScope();
  }

  public @NotNull PsiElement getPlace() {
    affectingFactors.add(Factor.placeElement);
    return myPlace;
  }

  public @NotNull PsiType getPsiType() {
    affectingFactors.add(Factor.qualifierType);
    return myPsiType;
  }

  public @NotNull PsiClass getPsiClass() {
    affectingFactors.add(Factor.qualifierType);
    return myPsiClass;
  }

  public @NotNull PsiFile getPlaceFile() {
    affectingFactors.add(Factor.placeFile);
    return myFile;
  }

  public @NotNull PsiFile justGetPlaceFile() {
    return myFile;
  }

  public @NotNull PsiElement justGetPlace() {
    return myPlace;
  }

  public @NotNull PsiClass justGetPsiClass() {
    return myPsiClass;
  }
}
