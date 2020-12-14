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

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isPropertiesElementOrMap;

/**
 * This transform deals with repository elements which support dynamically adding closures when
 * some of their properties are set.
 * For example, when setting the url property on a jcenter repository we go from:
 * <p>
 * {@code jcenter()}
 * <p>
 * to
 * <p>
 * {@code jcenter() {
 * url 'some.url'
 * }}
 */
public class RepositoryClosureTransform extends DefaultTransform {
  @NotNull
  private String myElementName;

  public RepositoryClosureTransform(@NotNull String elementName) {
    myElementName = elementName;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e) {
    return e != null;
  }

  @Nullable
  @Override
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    assert e != null;
    GradleDslElement element = null;
    if (e.getClosureElement() != null) {
      element = e.getClosureElement().getElement(myElementName);
    }
    else if (isPropertiesElementOrMap(e)) {
      element = ((GradlePropertiesDslElement)e).getElement(myElementName);
    }
    return element;
  }

  @NotNull
  @Override
  public GradleDslExpression bind(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull Object value,
                                  @NotNull String name) {
    GradleDslElement transformedElement = transform(oldElement);

    if (transformedElement == null) {
      return super.bind(holder, null, value, myElementName);
    }
    return super.bind(holder, transformedElement, value, myElementName);
  }

  @NotNull
  @Override
  public GradleDslElement replace(@NotNull GradleDslElement holder,
                                  @Nullable GradleDslElement oldElement,
                                  @NotNull GradleDslExpression newElement,
                                  @NotNull String name) {
    assert oldElement != null;


    GradlePropertiesDslElement parent;
    GradleDslElement existingElement;
    if (isPropertiesElementOrMap(oldElement)) {
      parent = (GradlePropertiesDslElement)oldElement;
      existingElement = parent.getElement(myElementName);
    }
    else if (oldElement.getClosureElement() == null) {
      parent = new GradleDslClosure(holder, null, GradleNameElement.empty());
      oldElement.setNewClosureElement((GradleDslClosure)parent);
      existingElement = null;
    }
    else {
      parent = oldElement.getClosureElement();
      existingElement = parent.getElement(myElementName);
    }
    super.replace(parent, existingElement, newElement, myElementName);
    return oldElement;
  }
}
