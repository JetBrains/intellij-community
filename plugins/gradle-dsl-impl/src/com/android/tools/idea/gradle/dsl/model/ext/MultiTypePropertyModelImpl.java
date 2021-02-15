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

import com.android.tools.idea.gradle.dsl.api.ext.MultiTypePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.LIST;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.MAP;

/**
 * Base implementation of a MultiTypePropertyModel.
 *
 * Note: The {@link PropertyTransform} for the current type in {@link #setUpTransforms()} will take priority over any transforms added by
 * {@link #addTransform(PropertyTransform)}.
 *
 * @param <T> the enum type to represent this properties types
 */
public abstract class MultiTypePropertyModelImpl<T extends Enum<T>> extends GradlePropertyModelImpl implements MultiTypePropertyModel<T> {

  @NotNull private Map<T, PropertyTransform> myTransforms;
  @NotNull private T myType;

  /***
   * @param defaultType the type to default to if no transforms in trasformMap are active.
   * @param element the element that the model should represent.
   * @param transformMap a map of types to the {@link PropertyTransform}s that should be used for them.
   *                     The order of this map is the order in which {@link PropertyTransform#test(GradleDslElement, GradleDslElement)}
   *                     will be called to work out the initial type {@link T}.
   */
  public MultiTypePropertyModelImpl(@NotNull T defaultType,
                                    @NotNull GradleDslElement element,
                                    @NotNull Map<T, PropertyTransform> transformMap) {
    super(element);
    myTransforms = new LinkedHashMap<>(transformMap);
    myType = defaultType;
    // This must be called after myType has been assign a default value.
    setUpTransforms();
  }

  /**
   * @param defaultType  the type to default to if no transforms in transformMap are active.
   * @param holder       the holder of the property. Any {@link GradleDslElement}s created by this model will have this as a parent.
   * @param propertyType the {@link PropertyType} that any new elements should have.
   * @param name         the name of the property represented by this model.
   * @param transformMap a map of types to the {@link PropertyTransform}s that should be used for them.
   *                     The order of this map is the order in which {@link PropertyTransform#test(GradleDslElement, GradleDslElement)}
   *                     will be called to work out the initial type {@link T}.
   */
  public MultiTypePropertyModelImpl(@NotNull T defaultType,
                                    @NotNull GradleDslElement holder,
                                    @NotNull PropertyType propertyType,
                                    @NotNull String name,
                                    @NotNull Map<T, PropertyTransform> transformMap) {
    super(holder, propertyType, name);
    myTransforms = new LinkedHashMap<>(transformMap);
    myType = defaultType;
    // This must be called after myType has been assigned a default value.
    setUpTransforms();
  }

  private void setUpTransforms() {
    // If we don't have an element yet we keep the current type.
    if (myElement == null) {
      return;
    }

    for (Map.Entry<T, PropertyTransform> e : myTransforms.entrySet()) {
      if (e.getValue().test(myElement, myPropertyHolder)) {
        myType = e.getKey();
        break;
      }
    }
  }

  @Override
  @Nullable
  public GradleDslElement getElement() {
    PropertyTransform transform = getTransform();
    if (transform.test(myElement, myPropertyHolder)) {
      return transform.transform(myElement);
    }
    return null;
  }

  @Override
  @NotNull
  protected PropertyTransform getTransform() {
    PropertyTransform pt = myTransforms.get(myType);
    if (pt != null) {
      return pt;
    }
    return super.getTransform();
  }

  @Override
  @NotNull
  public T getType() {
    return myType;
  }

  @Override
  public void setValue(@NotNull T type, @NotNull Object value) {
    setTypeAndValue(type, value);
  }

  @Override
  public void setType(@NotNull T type) {
    setTypeAndValue(type, null);
  }

  /**
   * Set the type and value of this MultiTypePropertyModel.
   *
   * @param type  the new type to set to
   * @param value the new value, if null the current value will be used
   */
  protected void setTypeAndValue(@NotNull T type, @Nullable Object value) {
    Object newValue = value;

    if (newValue == null) {
      // Get the value from the current element.
      ValueType oldValueType = getValueType();

      if (oldValueType == MAP || oldValueType == LIST) {
        throw new UnsupportedOperationException("Can't convert " + oldValueType + " property to new type " + type);
      }
      else {
        GradleDslElement element = getElement();
        assert element instanceof GradleDslSimpleExpression;
        // TODO: This always saves as a literal string, this means any interpolated strings won't copy properly.
        newValue = ((GradleDslSimpleExpression)element).getRawValue();
      }
    }

    // Set the type so a different transformation will kick in
    myType = type;

    if (newValue != null) {
      super.setValue(newValue);
    }
  }
}
