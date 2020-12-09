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
package com.android.tools.idea.gradle.dsl.parser.java;

import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.SOURCE_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaDslElement extends BaseCompileOptionsDslElement {
  public static final PropertiesElementDescription<JavaDslElement> JAVA =
    new PropertiesElementDescription<>("java", JavaDslElement.class, JavaDslElement::new);

  // The Java Dsl element has a different mapping of external names to functionality than the BaseCompileOptionsDslElement, even though
  // the corresponding models are identical.  This suggests that JavaDslElement should probably not in fact be a
  // BaseCompileOptionsDslElement.
  //
  // It is also a bit odd in that in Groovy the java block need not be explicitly present -- sourceCompatibility and targetCompatibility
  // properties set at top-level are treated as altering the java block properties.  (I think).
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR}
  }).collect(toModelMap());

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR}
  }).collect(toModelMap());

  @Override
  @NotNull
  public ImmutableMap<Pair<String, Integer>, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter.isKotlin()) {
      return ktsToModelNameMap;
    }
    else if (converter.isGroovy()) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public JavaDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  @Nullable
  public PsiElement create() {
    GradleDslNameConverter converter = getDslFile().getWriter();
    if (converter.isKotlin()) {
      return super.create();
    }
    else if (converter.isGroovy()) {
      if (myParent == null) {
        return null;
      }
      else {
        return myParent.create();
      }
    }
    else {
      return super.create();
    }
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    GradleDslNameConverter converter = getDslFile().getWriter();
    if (converter.isKotlin()) {
      super.setPsiElement(psiElement);
    }
    else if (converter.isGroovy()) {
      // do nothing
    }
    else {
      super.setPsiElement(psiElement);
    }
  }
}
