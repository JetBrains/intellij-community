/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;

/**
 * Represents a literal element.
 */
public final class GradleDslLiteral extends GradleDslSettableExpression {
  public GradleDslLiteral(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name, null);
    // Will be set in the call to #setValue
    setReference(false);
  }

  public GradleDslLiteral(@NotNull GradleDslElement parent,
                          @NotNull PsiElement psiElement,
                          @NotNull GradleNameElement name,
                          @NotNull PsiElement literal,
                          boolean isReference) {
    super(parent, psiElement, name, literal);
    setReference(isReference);
  }

  @Override
  @Nullable
  public Object produceValue() {
    PsiElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, true));
  }

  @Override
  @Nullable
  public Object produceUnresolvedValue() {
    PsiElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, false));
  }

  @Override
  public void setValue(@NotNull Object value) {
    checkForValidValue(value);
    PsiElement element =
      ApplicationManager.getApplication().runReadAction((Computable<PsiElement>)() -> {
        PsiElement psiElement = getDslFile().getParser().convertToPsiElement(this, value);
        getDslFile().getParser().setUpForNewValue(this, psiElement);
        return psiElement;
      });
    setUnsavedValue(element);
    valueChanged();
  }

  @Nullable
  @Override
  public Object produceRawValue() {
    PsiElement currentElement = getCurrentElement();
    if (currentElement == null) {
      return null;
    }

    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> {
                               boolean shouldInterpolate = getDslFile().getParser().shouldInterpolate(this);
                               Object val = getDslFile().getParser().extractValue(this, currentElement, false);
                               if (val instanceof String && shouldInterpolate) {
                                 return iStr((String)val);
                               }
                               return val;
                             });
  }

  @NotNull
  @Override
  public GradleDslLiteral copy() {
    assert myParent != null;
    GradleDslLiteral literal = new GradleDslLiteral(myParent, GradleNameElement.copy(myName));
    Object v = getRawValue();
    if (v != null) {
      literal.setValue(isReference() ? new ReferenceTo((String)v) : v);
    }
    return literal;
  }

  @Override
  public String toString() {
    Object value = getValue();
    String nameString = getName();
    String namePrefix = nameString.equals("") ? "" : (nameString + ": ");
    return namePrefix + (value != null ? value.toString() : super.toString());
  }

  @Override
  @NotNull
  public Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslLiteral(this);
  }

  @Override
  public void delete() {
    getDslFile().getWriter().deleteDslLiteral(this);
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslLiteral(this);
  }

  @Nullable
  public GradleReferenceInjection getReferenceInjection() {
    return myDependencies.isEmpty() ? null : myDependencies.get(0);
  }

  @Override
  @Nullable
  public String getReferenceText() {
    if (!isReference()) {
      return null;
    }

    PsiElement element = getCurrentElement();
    return element != null ? getPsiText(element) : null;
  }

  @Override
  public void reset() {
    super.reset();
    ApplicationManager.getApplication().runReadAction(() -> getDslFile().getParser().setUpForNewValue(this, getCurrentElement()));
  }
}
