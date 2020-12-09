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

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.notifications.NotificationTypeReference;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Provide Gradle specific abstraction over a {@link PsiElement}s.
 */
public interface GradleDslElement extends AnchorProvider {
  void setParsedClosureElement(@NotNull GradleDslClosure closure);

  void setNewClosureElement(@Nullable GradleDslClosure closureElement);

  /**
   * @return the closure that should be added to the element on the next call to {@link #applyChanges()}, null is no closure has been set.
   */
  @Nullable
  GradleDslClosure getUnsavedClosure();

  /**
   * @return the current closure element, either {@link #getUnsavedClosure()} if non-null or the closure that was parsed from the build
   * file. Null is both of these are absent.
   */
  @Nullable
  GradleDslClosure getClosureElement();

  /**
   * Returns the name of this element at the lowest scope. I.e the text after the last dot ('.').
   */
  @NotNull
  String getName();

  /**
   * Returns the full and qualified name of this {@link GradleDslElement}, this will be the name of this element appended to the
   * qualified name of this elements parent.
   */
  @NotNull
  String getQualifiedName();

  /**
   * Returns the full name of the element. For elements where it makes sense, this will be the text of the
   * PsiElement in the build file.
   */
  @NotNull
  String getFullName();

  @NotNull
  GradleNameElement getNameElement();

  void rename(@NotNull String newName);

  void rename(@NotNull List<String> hierarchicalName);

  @Nullable
  GradleDslElement getParent();

  @NotNull
  List<GradlePropertiesDslElement> getHolders();

  void addHolder(@NotNull GradlePropertiesDslElement holder);

  void setParent(@NotNull GradleDslElement parent);

  @Nullable
  PsiElement getPsiElement();

  void setPsiElement(@Nullable PsiElement psiElement);

  boolean shouldUseAssignment();

  void setUseAssignment(boolean useAssignment);

  @NotNull
  PropertyType getElementType();

  void setElementType(@NotNull PropertyType propertyType);

  @NotNull
  GradleDslFile getDslFile();

  @NotNull
  List<GradleReferenceInjection> getResolvedVariables();

  @Nullable
  GradleDslElement getAnchor();

  /**
   * Creates the {@link PsiElement} by adding this element to the .gradle file.
   *
   * <p>It creates a new {@link PsiElement} only when {@link #getPsiElement()} return {@code null}.
   *
   * <p>Returns the final {@link PsiElement} corresponds to this element or {@code null} when failed to create the
   * {@link PsiElement}.
   */
  @Nullable
  PsiElement create();

  /**
   * Triggers the moving of the element if required. Repositions the element within the build file based on
   * its parent. After a call to this method, if the element should be moved, the element will be placed
   * after the {@link PsiElement} obtained from calling {@link #requestAnchor(GradleDslElement)} on its parent.
   *
   * @return the new PsiElement of the moved element, null if the element is not attached to a tree or if
   * no PsiElement currently exists for it.
   */
  @Nullable
  PsiElement move();

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  void delete();

  /**
   * Marks this element as having been modified.
   */
  void setModified();

  boolean isModified();

  /**
   * Returns {@code true} if this element represents a Block element (Ex. android, productFlavors, dependencies etc.),
   * {@code false} otherwise.
   */
  boolean isBlockElement();

  /**
   * Returns {@code true} if this element represents an element which is insignificant if empty.
   */
  boolean isInsignificantIfEmpty();

  @NotNull
  Collection<GradleDslElement> getChildren();

  void resetState();

  void applyChanges();

  /**
   * Computes a list of properties and variables that are declared or assigned to in this scope.
   * Override in subclasses to return meaningful values.
   */
  @NotNull
  List<GradleDslElement> getContainedElements(boolean includeProperties);

  /**
   * Computes a list of properties and variables that are visible from this GradleDslElement.
   */
  @NotNull
  Map<String, GradleDslElement> getInScopeElements();

  /**
   * Helpers to quick obtain a notification instance for this elements build context.
   *
   * @param type type reference of the given notification, see {@link NotificationTypeReference} for possible values.
   * @param <T>  type of the notification
   * @return the instance of the notification in the build model.
   */
  @NotNull
  <T extends BuildModelNotification> T notification(@NotNull NotificationTypeReference<T> type);

  void registerDependent(@NotNull GradleReferenceInjection injection);

  void unregisterDependent(@NotNull GradleReferenceInjection injection);

  void unregisterAllDependants();

  /**
   * @return all things that depend on this element.
   */
  @NotNull
  List<GradleReferenceInjection> getDependents();

  /**
   * @return all resolved and unresolved dependencies.
   */
  @NotNull
  List<GradleReferenceInjection> getDependencies();

  void updateDependenciesOnAddElement(@NotNull GradleDslElement newElement);

  void updateDependenciesOnReplaceElement(@NotNull GradleDslElement oldElement, @NotNull GradleDslElement newElement);

  void updateDependenciesOnRemoveElement(@NotNull GradleDslElement oldElement);

  void resolve();

  boolean isNewEmptyBlockElement();

  /**
   * The map returned by this method is responsible for encoding the semantics of expressed user Dsl code in terms of the Dsl Model.
   *
   * The keys of the map represent user Dsl code as the (name, null) Pair for properties, and (name, arity) for method calls, with an
   * encoding to handle varargs documented in {@link ArityHelper}.
   *
   * The values of the map represent the model property, and the effect of this code on the property, encoded as an instance of
   * {@link ModelEffectDescription}.
   *
   * @param converter
   * @return a map from expressed code to model semantics
   */
  @NotNull
  ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter);


  /**
   * The effect of this element on the Dsl model.
   *
   * @return the effect on the model, or null if the element has no or unknown effect.
   */
  @Nullable
  ModelEffectDescription getModelEffect();

  void setModelEffect(@Nullable ModelEffectDescription effect);

  /**
   *  A convenience function to extract the property if the effect is not null.
   */
  @Nullable
  ModelPropertyDescription getModelProperty();
}
