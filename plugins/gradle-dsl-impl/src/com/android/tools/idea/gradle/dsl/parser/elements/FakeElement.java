/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.FAKE;

/**
 * A {@link FakeElement} that is used to represent models that are derived from part of a {@link GradleDslElement}.
 * This is needed since a {@link GradlePropertyModel} requires a backing element, these elements are not
 * fully part of the tree, they have parents but are not visible from or attached to their parent.
 * <p>
 * Subclasses of {@link FakeElement} can decide whether or not they should be able to be renamed or deleted. By default
 * {@link FakeElement}s can't be renamed but can be deleted if constructed with canDelete being {@code true}.
 * <p>
 * {@link #produceValue()} and {@link #consumeValue(Object)} should be overridden by each subclass to provide getting
 * and setting the derived value from and to its real element.
 */
public abstract class FakeElement extends GradleDslSettableExpression {
  @NotNull
  protected final GradleDslSimpleExpression myRealExpression;
  protected final boolean myCanDelete;
  @NotNull
  protected final GradleNameElement myFakeName;

  public FakeElement(@Nullable GradleDslElement parent,
                     @NotNull GradleNameElement name,
                     @NotNull GradleDslSimpleExpression originExpression,
                     boolean canDelete) {
    super(parent, null, name, null);
    myRealExpression = originExpression;
    myCanDelete = canDelete;
    myFakeName = name;
    setElementType(FAKE);
  }

  @Nullable
  private PsiElement createPsiElement() {
    Object s = extractValue();
    PsiElement element = s == null
                         ? null
                         : ApplicationManager.getApplication()
                                             .runReadAction((Computable<PsiElement>)() -> getDslFile().getParser().convertToPsiElement(this, s));
    // Note: Even though we use static dependencies for everything else, we are required to update them here.
    setupDependencies(element);
    return element;
  }

  @Override
  public void rename(@NotNull String newName) {
    throw new UnsupportedOperationException("Renaming of this fake element is not possible.");
  }

  @Override
  public void rename(@NotNull List<String> hierarchicalName) {
    throw new UnsupportedOperationException("Renaming of this fake element is not possible.");
  }

  @Override
  public final void delete() {
    if (myCanDelete) {
      consumeValue(null);
    }
    else {
      throw new UnsupportedOperationException("Deleting this element is not supported.");
    }
  }

  @NotNull
  @Override
  public final Collection<GradleDslElement> getChildren() {
    PsiElement element = createPsiElement();
    if (element == null) {
      return ImmutableList.of();
    }
    return getDslFile().getParser().getResolvedInjections(this, element).stream().map(e -> e.getToBeInjected()).collect(
      Collectors.toList());
  }

  @Override
  protected final void apply() {
    // Do nothing, this is a fake element
  }

  // FakeElements should use there real elements modification counts
  @Override
  public long getModificationCount() {
    return myRealExpression.getModificationCount();
  }

  // FakeElements should use there real elements modification counts
  @Override
  public long getLastCommittedModificationCount() {
    return myRealExpression.getLastCommittedModificationCount();
  }

  @Nullable
  @Override
  public Object produceValue() {
    PsiElement element = createPsiElement();
    if (element == null) {
      return null;
    }

    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, true));
  }

  @Nullable
  @Override
  public final Object produceUnresolvedValue() {
    PsiElement element = createPsiElement();
    if (element == null) {
      return null;
    }

    return ApplicationManager.getApplication()
                             .runReadAction((Computable<Object>)() -> getDslFile().getParser().extractValue(this, element, false));
  }

  @Override
  @NotNull
  public final String getName() {
    return myFakeName.name();
  }

  @Override
  @NotNull
  public final String getFullName() {
    return myFakeName.fullName();
  }

  @Override
  @NotNull
  public final GradleNameElement getNameElement() {
    return myFakeName;
  }

  @Override
  public final void setValue(@NotNull Object value) {
    consumeValue(value);
  }

  /**
   * @return returns the value that has been derived from the real element.
   */
  @Nullable
  protected abstract Object extractValue();

  /**
   * Set the derived element to a given value.
   *
   * @param value the value to set.
   */
  protected abstract void consumeValue(@Nullable Object value);

  @NotNull
  public GradleDslElement getRealExpression() {
    return myRealExpression;
  }
}
