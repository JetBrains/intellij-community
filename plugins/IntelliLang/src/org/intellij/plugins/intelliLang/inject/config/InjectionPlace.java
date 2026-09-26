// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayFactory;

/**
* @author Gregory.Shrago
*/
// todo inline class
public final class InjectionPlace {

  public static final InjectionPlace[] EMPTY_ARRAY = new InjectionPlace[0];

  public static final ArrayFactory<InjectionPlace> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new InjectionPlace[count];

  private final boolean myEnabled;
  private final ElementPattern<? extends PsiElement> myElementPattern;


  public InjectionPlace(ElementPattern<? extends PsiElement> myElementPattern, boolean enabled) {
    this.myElementPattern = myElementPattern;
    myEnabled = enabled;
  }

  public InjectionPlace enabled(final boolean enabled) {
    return new InjectionPlace(myElementPattern, enabled);
  }

  public String getText() {
    return myElementPattern.toString();
  }

  public ElementPattern<? extends PsiElement> getElementPattern() {
    return myElementPattern;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final InjectionPlace place = (InjectionPlace)o;

    if (!myElementPattern.equals(place.myElementPattern)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElementPattern.hashCode();
  }

  @Override
  public String toString() {
    return "InjectionPlace{" +
           (myEnabled ? "+ " : "- ") +
           myElementPattern +
           '}';
  }
}
