/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

/**
* @author peter
*/
public class GroovyClassDescriptor {
  static {
    try {
      GroovyCategorySupport.getCategoryNameUsage("aaa");
    }
    catch (NoSuchMethodError e) {
      throw new RuntimeException("Incompatible Groovy JAR in classpath: " + GroovyCategorySupport.class.getResource("/") + ", please remove it");
    }
  }

  private final PsiType myPsiType;
  private final PsiElement myPlace;
  private final PsiFile myFile;

  @SuppressWarnings({"SetReplaceableByEnumSet"}) //order is important
  final Set<Factor> affectingFactors = new LinkedHashSet<>();

  public GroovyClassDescriptor(@NotNull PsiType psiType, PsiElement place, final PsiFile placeFile) {
    myPsiType = psiType;
    myPlace = place;
    myFile = placeFile;
  }

  public Project getProject() {
    return myFile.getProject();
  }

  public GlobalSearchScope getResolveScope() {
    //affectingFactors.add(Factor.placeFile);
    return myPlace.getResolveScope();
  }

  public PsiElement getPlace() {
    affectingFactors.add(Factor.placeElement);
    return myPlace;
  }

  @NotNull
  public PsiType getPsiType() {
    affectingFactors.add(Factor.qualifierType);
    return myPsiType;
  }

  public PsiFile getPlaceFile() {
    affectingFactors.add(Factor.placeFile);
    return myFile;
  }

  public PsiFile justGetPlaceFile() {
    return myFile;
  }

}
