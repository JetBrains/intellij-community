/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.GradleDslWriter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.PROPERTIES_FILE;

public final class GradlePropertiesFile extends GradleDslFile {
  @NotNull
  private final Properties myProperties;

  public GradlePropertiesFile(@NotNull Properties properties,
                              @NotNull VirtualFile file,
                              @NotNull Project project,
                              @NotNull String moduleName,
                              @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
    myProperties = properties;
  }

  @Override
  public void parse() {
    // There is nothing to parse in a properties file as it's just a java properties file.
  }

  @Override
  @NotNull
  public List<GradleDslElement> getContainedElements(boolean includeProperties) {
    return new ArrayList<>(getPropertyElements().values());
  }

  @Override
  @Nullable
  public GradleDslSimpleExpression getPropertyElement(@NotNull String property) {
    String value = myProperties.getProperty(property);
    if (value == null) {
      return null;
    }

    GradlePropertyElement propertyElement = new GradlePropertyElement(this, GradleNameElement.fake(property));
    propertyElement.setValue(value);
    return propertyElement;
  }

  @Override
  @NotNull
  public Map<String, GradleDslElement> getPropertyElements() {
    Map<String, GradleDslElement> results = new HashMap<>();
    for (String name : myProperties.stringPropertyNames()) {
      results.put(name, getPropertyElement(name));
    }
    return results;
  }

  @Override
  @NotNull
  public Map<String, GradleDslElement> getElements() {
    // Properties files have no variables.
    return getPropertyElements();
  }

  @Override
  @NotNull
  public GradleDslWriter getWriter() {
    return new GradleDslWriter.Adapter();
  }

  private static final class GradlePropertyElement extends GradleDslSimpleExpression {
    @Nullable private Object myValue;

    private GradlePropertyElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
      super(parent, null, name, null);
      setElementType(PROPERTIES_FILE);
    }

    @Override
    @Nullable
    public Object produceValue() {
      return myValue;
    }

    @Override
    @Nullable
    public Object produceUnresolvedValue() {
      return getValue();
    }

    @Override
    @Nullable
    public <T> T getValue(@NotNull Class<T> clazz) {
      Object value = getValue();
      if (clazz.isInstance(value)) {
        return clazz.cast(value);
      }
      return null;
    }

    @Override
    @Nullable
    public <T> T getUnresolvedValue(@NotNull Class<T> clazz) {
      return getValue(clazz);
    }

    @Override
    public void setValue(@NotNull Object value) {
      myValue = value;
      valueChanged();
    }

    @Nullable
    @Override
    public Object produceRawValue() {
      return getUnresolvedValue();
    }

    @NotNull
    @Override
    public GradleDslSimpleExpression copy() {
      GradlePropertyElement element = new GradlePropertyElement(myParent, GradleNameElement.copy(myName));
      element.myValue = myValue;
      return element;
    }

    @Override
    @NotNull
    public Collection<GradleDslElement> getChildren() {
      return ImmutableList.of();
    }

    @Override
    protected void apply() {
      // There is nothing to apply here as this is just a dummy dsl element to represent a property.
    }
  }
}