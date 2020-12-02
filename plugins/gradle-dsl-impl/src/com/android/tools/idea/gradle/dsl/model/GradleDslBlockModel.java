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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class for the models representing block elements.
 */
public abstract class GradleDslBlockModel implements GradleDslModel {
  protected GradlePropertiesDslElement myDslElement;

  protected GradleDslBlockModel(@NotNull GradlePropertiesDslElement dslElement) {
    myDslElement = dslElement;
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return myDslElement.getPsiElement();
  }

  public boolean hasValidPsiElement() {
    PsiElement psiElement = getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  @Override
  @NotNull
  public Map<String, GradlePropertyModel> getInScopeProperties() {
    return myDslElement.getInScopeElements().entrySet().stream()
                       .collect(Collectors.toMap(e -> e.getKey(), e -> new GradlePropertyModelImpl(e.getValue())));
  }

  @Override
  @NotNull
  public List<GradlePropertyModel> getDeclaredProperties() {
    return myDslElement.getContainedElements(true).stream()
                       .filter(e -> e instanceof GradleDslExpression)
                       .map(e -> new GradlePropertyModelImpl(e)).collect(Collectors.toList());
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildResolved();
  }

  @NotNull
  protected ResolvedPropertyModel getModelForProperty(@NotNull ModelPropertyDescription property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildResolved();
  }

  @NotNull
  protected LanguageLevelPropertyModel getLanguageModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildLanguage();
  }

  @NotNull
  protected LanguageLevelPropertyModel getJvmTargetModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildJvmTarget();
  }

  @NotNull
  protected ResolvedPropertyModel getFileModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).addTransform(PropertyUtil.FILE_TRANSFORM).buildResolved();
  }

  @NotNull
  protected PasswordPropertyModel getPasswordModelForProperty(@NotNull String property) {
    return GradlePropertyModelBuilder.create(myDslElement, property).buildPassword();
  }
}
