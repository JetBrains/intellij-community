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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.model.java.LanguageLevelPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.kotlin.JvmTargetPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslGlobalValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

/**
 * Builder class for subclasses of GradlePropertyModelImpl.
 */
public final class GradlePropertyModelBuilder {
  /**
   * Creates a builder.
   *
   * @param holder the GradlePropertiesDslElement that the property belongs to
   * @param name   of the property that the model should be built for
   * @return new builder for model.
   */
  @NotNull
  public static GradlePropertyModelBuilder create(@NotNull GradlePropertiesDslElement holder, @NotNull String name) {
    return new GradlePropertyModelBuilder(holder, name);
  }

  /**
   * Creates a builder.
   *
   * @param holder   the GradlePropertiesDslElement that the property belongs to
   * @param property a description of the model property
   * @return new builder for model.
   */
  @NotNull
  public static GradlePropertyModelBuilder create(@NotNull GradlePropertiesDslElement holder, @NotNull ModelPropertyDescription property) {
    return new GradlePropertyModelBuilder(holder, property);
  }
  /**
   * Creates a builder from an element.
   * This is used for things such as {@link FakeElement}s as these can't be created in the normal way since they are not visible from
   * their parents. See {@link FakeElement} for more information.
   * In most cases {@link #create(GradlePropertiesDslElement, String)} should be used instead.
   *
   * @param element the fake element
   * @return new builder for the model
   */
  @NotNull
  public static GradlePropertyModelBuilder create(@NotNull GradleDslElement element) {
    return new GradlePropertyModelBuilder(element);
  }

  @Nullable
  private final GradlePropertiesDslElement myHolder;
  @NotNull
  private final String myName;
  @Nullable
  private final ModelPropertyDescription myProperty;
  @Nullable
  private final GradleDslElement myElement;
  @Nullable
  private GradleDslElement myDefault;
  @NotNull
  private List<PropertyTransform> myTransforms = new ArrayList<>();

  private GradlePropertyModelBuilder(@NotNull GradlePropertiesDslElement holder, @NotNull String name) {
    myHolder = holder;
    myName = name;
    myElement = null;
    myProperty = null;
  }

  private GradlePropertyModelBuilder(@NotNull GradlePropertiesDslElement holder, @NotNull ModelPropertyDescription property) {
    myHolder = holder;
    myName = property.name;
    myElement = null;
    myProperty = property;
  }

  private GradlePropertyModelBuilder(@NotNull GradleDslElement element) {
    myHolder = null;
    myName = element.getName();
    myElement = element;
    ModelEffectDescription effect = element.getModelEffect();
    if (effect == null) {
      myProperty = null;
    }
    else {
      myProperty = effect.property;
    }
  }

  /**
   * Arranges that the model will provide a default value if there is no Dsl element, for cases where there is a known default (and
   * it is useful for the model to be able to provide it).
   *
   * @param value
   * @return this model builder
   */
  public GradlePropertyModelBuilder withDefault(Object value) {
    myDefault = new GradleDslGlobalValue(getParentElement(), value, myName);
    return this;
  }

  /**
   * Adds a transform to this property model. See {@link PropertyTransform} for details.
   *
   * @param transform to add
   * @return this model builder
   */
  public GradlePropertyModelBuilder addTransform(@NotNull PropertyTransform transform) {
    myTransforms.add(0, transform);
    return this;
  }

  @Nullable
  private GradleDslElement getElement() {
    if (myElement != null) {
      return myElement;
    }

    assert myHolder != null;
    return myHolder.getPropertyElement(myName);
  }

  @NotNull
  private GradleDslElement getParentElement() {
    if (myHolder != null) {
      return myHolder;
    }

    assert myElement != null && myElement.getParent() != null;
    return myElement.getParent();
  }

  /**
   * Builds a {@link GradlePropertyModel} with the properties defined by this builder
   *
   * @return the built model
   */
  public GradlePropertyModelImpl build() {
    GradleDslElement currentElement = getElement();
    GradlePropertyModelImpl model;
    if (currentElement != null) {
      model = new GradlePropertyModelImpl(currentElement);
    }
    else if(myProperty != null) {
      model = new GradlePropertyModelImpl(getParentElement(), REGULAR, myProperty);
    }
    else {
      model = new GradlePropertyModelImpl(getParentElement(), REGULAR, myName);
    }
    return setUpModel(model);
  }

  public SigningConfigPropertyModelImpl buildSigningConfig() {
    return new SigningConfigPropertyModelImpl(build());
  }

  /**
   * Builds a {@link PasswordPropertyModel} with the properties defined by this builder.
   *
   * @return the built model
   */
  public PasswordPropertyModelImpl buildPassword() {
    GradleDslElement currentElement = getElement();
    PasswordPropertyModelImpl model = currentElement == null
                                      ? new PasswordPropertyModelImpl(getParentElement(), REGULAR, myName)
                                      : new PasswordPropertyModelImpl(currentElement);
    return setUpModel(model);
  }

  /**
   * Builds a {@link ResolvedPropertyModel} with the properties defined by this builder.
   *
   * @return the built model
   */
  public ResolvedPropertyModelImpl buildResolved() {
    return build().resolve();
  }

  /**
   * Builds a {@link LanguageLevelPropertyModel} with the properties defined by this builder.
   *
   * @return the built model
   */
  public LanguageLevelPropertyModelImpl buildLanguage() {
    return new LanguageLevelPropertyModelImpl(build());
  }

  /**
   * Builds a {@link JvmTargetPropertyModel} with the properties defined by this builder.
   *
   * @return the built model
   */
  public LanguageLevelPropertyModel buildJvmTarget() {
    return new JvmTargetPropertyModelImpl(build());
  }

  @NotNull
  private <T extends GradlePropertyModelImpl> T setUpModel(@NotNull T model) {
    if (myDefault != null) {
      model.setDefaultElement(myDefault);
    }

    for (PropertyTransform t : myTransforms) {
      model.addTransform(t);
    }
    return model;
  }
}
