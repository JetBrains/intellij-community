/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import java.util.Map;
import java.util.Objects;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO;

/**
 * Base class for all the {@link GradleDslElement}s that represent blocks like android, productFlavors, buildTypes etc.
 */
public class GradleDslBlockElement extends GradlePropertiesDslElement {
  protected GradleDslBlockElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  private Pair<String,SemanticsDescription> getSemantics(@NotNull GradleDslElement element) {
    String name = element.getName();
    Map<Pair<String,Integer>,Pair<String,SemanticsDescription>> nameMapper = getExternalToModelMap(element.getDslFile().getParser());
    if (element.shouldUseAssignment()) {
      Pair<String, SemanticsDescription> value = nameMapper.get(new Pair<>(name, (Integer) null));
      if (value != null) {
        return value;
      }
    }
    else {
      for (Map.Entry<Pair<String, Integer>,Pair<String, SemanticsDescription>> entry : nameMapper.entrySet()) {
        String entryName = entry.getKey().getFirst();
        Integer arity = entry.getKey().getSecond();
        // TODO(xof): distinguish between semantics based on expressed arities (at the moment we return the first method entry we find,
        //  whether or not the arity is compatible.
        if (entryName.equals(name) && !Objects.equals(arity, property)) {
          return entry.getValue();
        }
      }
    }
    return null;
  }

  /**
   * The contract of this method is to arrange that an element parsed from Dsl source corresponding to setting a model property is
   * recognized as that model property.  This is needed because in general there are multiple ways of setting model properties, even
   * within one Dsl language (Groovy setter methods visible and setVisible) let alone between multiple Dsl languages (Groovy visible vs
   * Kotlin isVisible).
   *
   * The operation, if we do find that we are dealing with a model property, boils down to calling canonize (NOTYPO) on the name element,
   * which changes the name of the element to be the model property name, while not marking the element itself as having been modified (so
   * that absent anything else happening the element does not appear to need writing out on apply().  The {@link GradleDslNameConverter}
   * implementations are responsible for converting the model name back to an external name if the element is modified by something else
   * (e.g. a user's {@link GradlePropertyModel#setValue(Object)}.)
   *
   * @param element a Dsl element potentially representing a model property
   */
  private void maybeCanonizeElement(@NotNull GradleDslElement element) { // NOTYPO
    Pair<String,SemanticsDescription> semantics = getSemantics(element);
    if (semantics == null) return;
    SemanticsDescription description = semantics.getSecond();
    if (element.shouldUseAssignment()) {
      if (description != VAR && description != VWO) {
        // we are maybe-renaming a property involved in an assignment, which only makes sense if the property has a writer (i.e.
        // it is a property and not a read-only VAL)
        return;
      }
      // TODO(xof): for methods, we should eventually only canonize (NOTYPO) if we have a SET.  Until the semantics are fully encoded,
      //  though, there are other (description == OTHER) methods which end up here.
    }
    // we rename the GradleNameElement, and not the element directly, because this renaming is not about renaming the property
    // but about providing a canonical model name for a thing.
    element.getNameElement().canonize(semantics.getFirst()); // NOTYPO
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(element.getFullName())) {
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this);
        super.addParsedElement(applyDslElement);
      }
      applyDslElement.addParsedElement(element);
      return;
    }
    Pair<String,SemanticsDescription> semantics = getSemantics(element);
    if (semantics != null) {
      SemanticsDescription description = semantics.getSecond();
      if (description == ADD_AS_LIST && element instanceof GradleDslSimpleExpression) {
        addAsParsedDslExpressionList(semantics.getFirst(), (GradleDslSimpleExpression) element);
        return;
      }
      maybeCanonizeElement(element); // NOTYPO
    }
    super.addParsedElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    maybeCanonizeElement(element); // NOTYPO
    super.setParsedElement(element);
  }

  @Override
  @NotNull
  public GradleDslElement setNewElement(@NotNull GradleDslElement element) {
    maybeCanonizeElement(element); // NOTYPO
    return super.setNewElement(element);
  }
}
