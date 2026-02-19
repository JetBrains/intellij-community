// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.Couple;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class GrMapTypeImpl extends GrMapType {

  private final @NotNull LinkedHashMap<String, PsiType> myStringEntries;
  private final @NotNull List<Couple<PsiType>> myOtherEntries;

  GrMapTypeImpl(JavaPsiFacade facade,
                GlobalSearchScope scope,
                @NotNull LinkedHashMap<String, PsiType> stringEntries,
                @NotNull List<Couple<PsiType>> otherEntries,
                @NotNull LanguageLevel languageLevel) {
    super(facade, scope, languageLevel);
    myStringEntries = stringEntries;
    myOtherEntries = otherEntries;
  }

  @Override
  public @Nullable PsiType getTypeByStringKey(String key) {
    return myStringEntries.get(key);
  }

  @Override
  public @NotNull Set<String> getStringKeys() {
    return myStringEntries.keySet();
  }

  @Override
  protected @NotNull List<Couple<PsiType>> getOtherEntries() {
    return myOtherEntries;
  }

  @Override
  protected @NotNull LinkedHashMap<String, PsiType> getStringEntries() {
    return myStringEntries;
  }

  @Override
  public boolean isValid() {
    for (PsiType type : myStringEntries.values()) {
      if (type != null && !type.isValid()) {
        return false;
      }
    }
    for (Couple<PsiType> entry : myOtherEntries) {
      if (entry.first != null && !entry.first.isValid()) {
        return false;
      }
      if (entry.second != null && !entry.second.isValid()) {
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean isEmpty() {
    return myStringEntries.isEmpty() && myOtherEntries.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GrMapTypeImpl type = (GrMapTypeImpl)o;

    if (!myStringEntries.equals(type.myStringEntries)) return false;
    if (!myOtherEntries.equals(type.myOtherEntries)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myStringEntries.hashCode();
    result = 31 * result + myOtherEntries.hashCode();
    return result;
  }
}
