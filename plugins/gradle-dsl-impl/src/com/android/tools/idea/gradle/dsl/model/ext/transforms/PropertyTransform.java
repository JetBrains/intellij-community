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
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.removeElement;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription.CREATE_WITH_VALUE;

/**
 * <p>Defines a transform that can be used to allow a {@link GradlePropertyModel} to represent complex properties.
 * Simple properties will be in the form of:</p>
 * <code>propertyName = propertyValue</code><br>
 * <p>
 * <p>However sometimes properties will need to be represented in other ways such as {@link SigningConfigModel#storeFile()}.
 * The type of this property is a file and as such need to be shown in the gradle file as:</p>
 * <code>propertyName = file(propertyValue)</code><br>
 * <p>See {@link SingleArgumentMethodTransform} as an example transform for this.</p>
 * <p>
 * <p>A {@link PropertyTransform} allows us to define extra formats for properties which give us access to the value that we are
 * interested in. A {@link GradlePropertyModel} can have any number of transforms associated with it.</p>
 * <p>
 * <p>If no transforms are added via {@link GradlePropertyModelImpl#addTransform(PropertyTransform)} then the default transform
 * {@link PropertyUtil#DEFAULT_TRANSFORM} is used.</p>
 */
public abstract class PropertyTransform {
  public PropertyTransform() {
  }

  /**
   * Function for testing the properties {@link GradleDslElement} to see whether this transform should be activated.
   *
   * @param e the element contained by a property, null if the property has no element.
   * @param holder the Dsl element which will hold this property.
   * @return whether or not this transform should be activated
   */
  public abstract boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder);

  /**
   * A function that transforms the properties {@link GradleDslElement} into one that should be used as the value.
   *
   * @param e the element contained by a property. When used in a {@link PropertyTransform} this argument is
   *          guaranteed to have had a previous call to {@link PropertyTransform#test(GradleDslElement, GradleDslElement)}
   *          return {@code true}.
   * @return the element that should be used to represent the property's value if this transform is active,
   * if the transform can't correctly transform the element then {@code null} should be returned.
   */
  @Nullable
  public abstract GradleDslElement transform(@Nullable GradleDslElement e);

  /**
   * A function used to bind a value to a new {@link GradleDslElement}.
   *
   * @param holder     the parent of the property being held by the {@link GradlePropertyModel}
   * @param oldElement the old element being held by the {@link GradlePropertyModel}, null if it doesn't yet exist.
   * @param value      the new value that was passed to {@link GradlePropertyModel#setValue(Object)}.
   * @param name       the name of the property, this may be useful in replacing existing elements or creating new ones.
   * @return the new element to replace. This will be passed into {@link #replace(GradleDslElement, GradleDslElement, GradleDslExpression, String)}
   * if the element returned differs from oldElement.
   */
  @NotNull
  public abstract GradleDslExpression bind(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull Object value,
                                           @NotNull String name);

  @NotNull
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull ModelPropertyDescription propertyDescription) {
    return bind(holder, oldElement, value, propertyDescription.name);
  }

  /**
   * A function used to bind a new list to the {@link GradlePropertyModel}.
   *
   * @param holder       the parent of the property being held by the {@link GradlePropertyModel}
   * @param oldElement   the old element being held by the {@link GradlePropertyModel}, null if it doesn't yet exist.
   * @param name         the new value that was passed to {@link GradlePropertyModel#setValue(Object)}.
   * @param isMethodCall whether or not the {@link GradlePropertyModel} was set to be a method call or not.
   * @return a {@link GradleDslExpression} to be passed into replace if it differs from oldElement.
   */
  @NotNull
  public GradleDslExpression bindList(@NotNull GradleDslElement holder,
                                      @Nullable GradleDslElement oldElement,
                                      @NotNull String name,
                                      boolean isMethodCall) {
    return new GradleDslExpressionList(holder, GradleNameElement.create(name), !isMethodCall);
  }

  @NotNull
  public GradleDslExpression bindList(@NotNull GradleDslElement holder,
                                      @Nullable GradleDslElement oldElement,
                                      @NotNull ModelPropertyDescription propertyDescription,
                                      boolean isMethodCall) {
    GradleDslExpression result = bindList(holder, oldElement, propertyDescription.name, isMethodCall);
    result.setModelEffect(new ModelEffectDescription(propertyDescription, CREATE_WITH_VALUE));
    return result;
  }

  /**
   * A function used to bind a new map to the {@link GradlePropertyModel}.
   *
   * @param holder       the parent of the property being held by the {@link GradlePropertyModel}
   * @param oldElement   the old element being held by the {@link GradlePropertyModel}, null if it doesn't yet exist.
   * @param name         the new value that was passed to {@link GradlePropertyModel#setValue(Object)}.
   * @param isMethodCall whether or not the {@link GradlePropertyModel} was set to be a method call or not.
   * @return a {@link GradleDslExpression} to be passed into replace if it differs from oldElement.
   */
  @NotNull
  public GradleDslExpression bindMap(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull String name,
                                     boolean isMethodCall) {
    return new GradleDslExpressionMap(holder, GradleNameElement.create(name), !isMethodCall);
  }

  @NotNull
  public GradleDslExpression bindMap(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull ModelPropertyDescription propertyDescription,
                                     boolean isMethodCall) {
    GradleDslExpression result = bindMap(holder, oldElement, propertyDescription.name, isMethodCall);
    result.setModelEffect(new ModelEffectDescription(propertyDescription, CREATE_WITH_VALUE));
    return result;
  }

  /**
   * A function to handle the replacement of elements within a {@link GradlePropertyModel}. This allows transforms
   * to do possibly complex restructurings of the element tree. It also gives the transform a chance to flag errors
   * specific to each individual property.
   *
   * @param holder     the parent of the property being held by the {@link GradlePropertyModel}
   * @param oldElement the old element being held by the {@link GradlePropertyModel}, null if it doesn't yet exist.
   * @param newElement the new element that was returned by one of the bind* methods based on what operation was performed on the model.
   * @return the new element that should be assigned to the {@link GradlePropertyModel}.
   */
  @NotNull
  public abstract GradleDslElement replace(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull GradleDslExpression newElement,
                                           @NotNull String name);

  /**
   * A function to handle the removal as elements from a property model. This method is called on the active transform if the model is
   * deleted via {@link GradlePropertyModel#delete()}.
   *
   * @param holder     the parent of the property being held by the {@link GradlePropertyModel}
   * @param oldElement the old element being held by the {@link GradlePropertyModel}, null if it doesn't yet exist.
   * @param
   */
  @Nullable
  public GradleDslElement delete(@NotNull GradleDslElement holder,
                                 @NotNull GradleDslElement oldElement,
                                 @NotNull GradleDslElement transformedElement) {
    removeElement(transformedElement);
    return null;
  }
}
