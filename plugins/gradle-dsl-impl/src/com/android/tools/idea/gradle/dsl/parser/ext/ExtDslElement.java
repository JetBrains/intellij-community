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
package com.android.tools.idea.gradle.dsl.parser.ext;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents the extra user-defined properties defined in the Gradle file.
 * <p>
 * For more details please read
 * <a href="https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:extra_properties">Extra Properties</a>.
 * </p>
 */
public final class ExtDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<ExtDslElement> EXT =
    new PropertiesElementDescription<>("ext", ExtDslElement.class, ExtDslElement::new);

  public ExtDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  /*
     The following method that sets values on the ExtDslElement are overwritten from the GradlePropertiesDslElement,
     this is two ensure that any properties added to the Ext block use the equals notation, "prop1 = 'value'" as apposed
     to the application notation. "prop1 'value'", the latter is not valid.
   */
  @Override
  @NotNull
  public GradleDslElement setNewLiteral(@NotNull String property, @NotNull Object object) {
    GradleDslElement element = super.setNewLiteral(property, object);
    element.setUseAssignment(true);
    return element;
  }

  @Override
  @NotNull
  public GradleDslElement setNewElement(@NotNull GradleDslElement element) {
    GradleDslElement newElement = super.setNewElement(element);
    newElement.setUseAssignment(true);
    return newElement;
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    // This makes sure the the PsiElement for the ExtDslElement is the first one declared in the file.
    // This allows all new ext elements to be added to the first ext block so to be more likely to be in scope for using fields.
    if (getPsiElement() == null || psiElement == null) {
      super.setPsiElement(psiElement);
    }
  }

  /**
   * For the ExtModel we need to also include properties that are already defined in the block,
   * rather than just variables.
   */
  @Override
  @NotNull
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return super.getContainedElements(true);
  }

  /**
   * For the ExtModel we need to also include properties that are already defined in the block,
   * rather than just variables.
   */
  @Override
  @NotNull
  public Map<String, GradleDslElement> getInScopeElements() {
    if (myParent == null) {
      return ImmutableMap.of();
    }
    Map<String, GradleDslElement> parentResults = myParent.getInScopeElements();
    // Add my properties as well.
    parentResults.putAll(getElements());
    return parentResults;
  }
}
