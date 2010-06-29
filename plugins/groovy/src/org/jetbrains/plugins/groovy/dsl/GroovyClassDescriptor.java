/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
* @author peter
*/
public class GroovyClassDescriptor {
  static {
    try {
      final AtomicInteger integer = GroovyCategorySupport.getCategoryNameUsage("aaa");
    }
    catch (NoSuchMethodError e) {
      throw new RuntimeException("Incompatible Groovy jar in classpath: " + GroovyCategorySupport.class.getResource("/") + ", please remove it");
    }
  }

  private final PsiClass myPsiClass;
  private final PsiElement myPlace;
  private final PsiFile myFile;
  private final boolean myPlaceDependent;
  private boolean myPlaceElementAccessed;

  public GroovyClassDescriptor(@NotNull PsiClass psiClass, PsiElement place, boolean placeDependent, final PsiFile placeFile) {
    myPsiClass = psiClass;
    myPlace = place;
    myPlaceDependent = placeDependent;
    myFile = placeFile;
  }

  public Project getProject() {
    return myPlace.getProject();
  }

  public GlobalSearchScope getResolveScope() {
    return myPlace.getResolveScope();
  }

  @Nullable
  public String getQualifiedName() {
    return myPsiClass.getQualifiedName();
  }

  public boolean isInheritor(String qname) {
    return InheritanceUtil.isInheritor(myPsiClass, qname);
  }

  public PsiElement getPlace() {
    myPlaceElementAccessed = true;
    return myPlace;
  }

  public PsiClass getPsiClass() {
    return myPsiClass;
  }

  public PsiFile getPlaceFile() {
    return myFile;
  }

  public boolean placeAccessed() {
    return myPlaceElementAccessed;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroovyClassDescriptor that = (GroovyClassDescriptor)o;

    if (!myPsiClass.equals(that.myPsiClass)) return false;

    if (myPlaceDependent) {
      return myPlace.equals(that.myPlace);
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myPsiClass.hashCode();
    if (myPlaceDependent) {
      return result * 31 + myPlace.hashCode();
    }
    return result;
  }
}
