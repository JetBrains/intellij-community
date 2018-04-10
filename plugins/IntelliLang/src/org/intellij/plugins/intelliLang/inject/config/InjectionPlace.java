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

package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayFactory;

/**
* @author Gregory.Shrago
*/
// todo inline class
public class InjectionPlace {

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
