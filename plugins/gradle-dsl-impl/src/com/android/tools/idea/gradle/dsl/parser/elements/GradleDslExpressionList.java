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

import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an element which consists a list of {@link GradleDslSimpleExpression}s.
 */
public final class GradleDslExpressionList extends GradlePropertiesDslElement implements GradleDslExpression {
  private final boolean myAppendToArgumentListWithOneElement;
  // This boolean controls whether of not the empty list element should be deleted on a call to delete in one of
  // its children. For non-literal lists (e.g merges "merge1", "merge2") #shouldBeDeleted() always returns true since we
  // never want to preserve these lists. However literal lists (e.g merges = ['merge1', 'merge2']) should only be deleted
  // if the #delete() method on the list element is called, not when there are no more elements left. This is due to
  // merges = [] possibly having important semantic meaning.
  private boolean myShouldBeDeleted;

  // Is this GradleDslExpressionList being used as an actual list. This is used when creating the element to
  // work out whether we need to wrap this list in brackets. For example expression lists are used for literals lists
  // like "prop = ['value1', 'value2']" but can also be used for thing such as lint options "check 'check-id-1', 'check-id-2'"
  private boolean myIsLiteralList;

  public GradleDslExpressionList(@Nullable GradleDslElement parent, @NotNull GradleNameElement name, boolean isLiteralList) {
    super(parent, null, name);
    myAppendToArgumentListWithOneElement = false;
    myIsLiteralList = isLiteralList;
  }

  public GradleDslExpressionList(@NotNull GradleDslElement parent,
                                 @NotNull PsiElement psiElement,
                                 boolean isLiteralList,
                                 @NotNull GradleNameElement name) {
    super(parent, psiElement, name);
    myAppendToArgumentListWithOneElement = false;
    myIsLiteralList = isLiteralList;
  }

  public GradleDslExpressionList(@NotNull GradleDslElement parent,
                                 @NotNull PsiElement psiElement,
                                 @NotNull GradleNameElement name,
                                 boolean appendToArgumentListWithOneElement) {
    super(parent, psiElement, name);
    myAppendToArgumentListWithOneElement = appendToArgumentListWithOneElement;
    myIsLiteralList = false;
  }

  public void addParsedExpression(@NotNull GradleDslExpression expression) {
    super.addParsedElement(expression);
  }

  public void addNewExpression(@NotNull GradleDslExpression expression) {
    setNewElement(expression);
  }

  public void addNewExpression(@NotNull GradleDslExpression expression, int index) {
    addNewElementAt(index, expression);
  }

  @SuppressWarnings("SuspiciousMethodCalls") // We pass in a superclass instance to remove.
  public void removeElement(@NotNull GradleDslElement element) {
    super.removeProperty(element);
  }

  public GradleDslExpression getElementAt(int index) {
    List<GradleDslExpression> expressions = getPropertyElements(GradleDslExpression.class);
    if (index < 0 || index > expressions.size()) {
      return null;
    }
    return expressions.get(index);
  }

  @SuppressWarnings("SuspiciousMethodCalls") // We pass in a superclass instance to remove.
  public int findIndexOf(@NotNull GradleDslElement element) {
    List<GradleDslExpression> expressions = getPropertyElements(GradleDslExpression.class);
    for (int i = 0; i < expressions.size(); i++) {
      if (expressions.get(i).equals(element)) {
        return i;
      }
    }
    return -1;
  }

  public void replaceExpression(@NotNull GradleDslExpression oldExpression, @NotNull GradleDslExpression newExpression) {
    super.replaceElement(oldExpression, newExpression);
  }

  @NotNull
  public List<GradleDslExpression> getExpressions() {
    return getPropertyElements(GradleDslExpression.class);
  }

  @NotNull
  public List<GradleDslSimpleExpression> getSimpleExpressions() {
    return getExpressions().stream().filter(e -> e instanceof GradleDslSimpleExpression).map(e -> (GradleDslSimpleExpression)e).collect(
      Collectors.toList());
  }

  @NotNull
  public <T> List<T> getLiterals(@NotNull Class<T> clazz) {
    return getSimpleExpressions().stream().map(e -> e.getValue(clazz)).filter(e -> e != null).collect(Collectors.toList());
  }

  public boolean isLiteralList() {
    return myIsLiteralList;
  }

  public boolean isAppendToArgumentListWithOneElement() {
    return myAppendToArgumentListWithOneElement;
  }

  @Override
  @Nullable
  public PsiElement create() {
    return getDslFile().getWriter().createDslExpressionList(this);
  }

  @Override
  public void delete() {
    myShouldBeDeleted = true;
    super.delete();
  }

  @Override
  protected void apply() {
    getDslFile().getWriter().applyDslExpressionList(this);

    super.apply();
  }

  @Override
  @Nullable
  public PsiElement getExpression() {
    return getPsiElement();
  }

  public boolean shouldBeDeleted() {
    return !isLiteralList() || myShouldBeDeleted;
  }

  @Override
  @NotNull
  public List<GradleReferenceInjection> getResolvedVariables() {
    return ContainerUtil.filter(getDependencies(), e -> e.isResolved());
  }

  // The following methods ensure that only GradleDslExpressions can be added to this GradlePropertiesDslElement.

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    assert element instanceof GradleDslExpression;
    super.setParsedElement(element);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    assert element instanceof GradleDslExpression;
    super.addParsedElement(element);
  }

  @Override
  protected void addParsedResettingElement(@NotNull GradleDslElement element, @NotNull String propertyToReset) {
    assert element instanceof GradleDslExpression;
    super.addParsedResettingElement(element, propertyToReset);
  }

  @Override
  public void addToParsedExpressionList(@NotNull String property, @NotNull GradleDslElement element) {
    assert element instanceof GradleDslExpression;
    super.addToParsedExpressionList(property, element);
  }

  @NotNull
  @Override
  public GradleDslElement setNewElement(@NotNull GradleDslElement newElement) {
    assert newElement instanceof GradleDslExpression;
    return super.setNewElement(newElement);
  }

  @Override
  public void addNewElementAt(int index, @NotNull GradleDslElement newElement) {
    assert newElement instanceof GradleDslExpression;
    List<GradleDslExpression> expressions = getPropertyElements(GradleDslExpression.class);
    if (index > expressions.size()) {
      throw new IndexOutOfBoundsException(index + " is out of bounds for size " + expressions.size());
    }
    super.addNewElementAt(index, newElement);
  }

  @NotNull
  @Override
  public GradleDslElement replaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement) {
    assert newElement instanceof GradleDslExpression && oldElement instanceof GradleDslExpression;
    return super.replaceElement(oldElement, newElement);
  }

  @Override
  @NotNull
  public GradleDslExpressionList copy() {
    GradleDslExpressionList listClone =
      new GradleDslExpressionList(myParent, GradleNameElement.copy(myName), /*isLiteralMap()*/false);
    for (GradleDslElement element : getCurrentElements()) {
      // NOTE: This line may throw if we try to change the configuration name of an unsupported element.
      GradleDslExpression sourceExpression = (GradleDslExpression)element;
      GradleDslExpression copiedExpression = sourceExpression.copy();
      // NOTE: setNewElement is a confusing name which does not reflect what the method does.
      listClone.setNewElement(copiedExpression);
    }
    return listClone;
  }
}
