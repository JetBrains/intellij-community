// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/*
 * Base class for Dynamic property and method
 */

public abstract class DItemElement implements DNamedElement, DTypedElement, Comparable {
  public String myType;
  public Boolean myStatic;
  public String myName;

  public DItemElement(@Nullable Boolean isStatic, @Nullable String name, @Nullable String type) {
    myStatic = isStatic;
    myName = name;
    myType = type;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DItemElement that = (DItemElement)o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;
    if (myStatic != that.myStatic) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myType != null ? myType.hashCode() : 0);
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    return result;
  }

  @Override
  public String getType() {
    return myType;
  }

  @Override
  public void setType(String type) {
    this.myType = type;
    clearCache();
  }

  public abstract void clearCache();

  @Override
  public @NlsSafe String getName() {
    return myName;
  }

  @Override
  public void setName(@NlsSafe String name) {
    this.myName = name;
    clearCache();
  }

  public Boolean isStatic() {
    return myStatic;
  }

  public void setStatic(Boolean aStatic) {
    myStatic = aStatic;
    clearCache();
  }

  @Override
  public int compareTo(Object o) {
    if (!(o instanceof DItemElement)) return 0;
    final DItemElement otherProperty = (DItemElement)o;

    return getName().compareTo(otherProperty.getName()) + getType().compareTo(otherProperty.getType());
  }


  @NotNull
  public abstract PsiNamedElement getPsi(PsiManager manager, String containingClassName);
}
