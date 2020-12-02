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
package com.android.tools.idea.gradle.dsl.parser.ext;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public final class ElementSort {
  private enum State {
    UNMARKED, TEMP_MARK, PERM_MARK
  }

  @NotNull private final GradleDslElement myElement; // Element we are sorting for, used for unresolved dependencies.
  @NotNull private final GradlePropertiesDslElement myParent;

  private ElementSort(@NotNull GradlePropertiesDslElement parentElement, @NotNull GradleDslElement element) {
    myElement = element;
    myParent = parentElement;
  }

  public static ElementSort create(@NotNull GradlePropertiesDslElement parent, @NotNull GradleDslElement element) {
    return new ElementSort(parent, element);
  }

  public boolean sort(@NotNull List<GradleDslElement> elementList, @NotNull List<GradleDslElement> resultList) {
    Map<GradleDslElement, State> states = new HashMap<>();
    elementList.forEach(e -> states.put(e, State.UNMARKED));
    List<GradleDslElement> postSort = new ArrayList<>();

    for (GradleDslElement e : elementList) {
      if (!visit(states, e, postSort)) {
        return false;
      }
    }

    resultList.clear();
    resultList.addAll(postSort);
    return true;
  }

  private boolean visit(@NotNull Map<GradleDslElement, State> states,
                        @NotNull GradleDslElement element,
                        @NotNull List<GradleDslElement> postSort) {
    if (states.get(element) == State.PERM_MARK) {
      return true;
    }
    if (states.get(element) == State.TEMP_MARK) {
      return false;
    }

    states.put(element, State.TEMP_MARK);
    for (GradleDslElement item : gatherDependencies(element)) {
      if (!visit(states, item, postSort)) {
        return false;
      }
    }
    states.put(element, State.PERM_MARK);
    postSort.add(element);
    return true;
  }

  private List<GradleDslElement> gatherDependencies(@NotNull GradleDslElement element) {
    return element.getDependencies().stream().map(e -> {
      GradleDslElement newElement = e.getToBeInjected();
      GradleDslSimpleExpression originElement = e.getOriginElement();
      String internalSyntaxReference = originElement.getDslFile().getParser().convertReferenceText(originElement, e.getName());
      if (newElement == null && myElement.getNameElement().isReferencedIn(internalSyntaxReference)) {
        return myElement;
      }

      if (newElement == null) {
        newElement = originElement.resolveInternalSyntaxReference(internalSyntaxReference, false);
      }

      while (newElement != null && newElement.getParent() != myParent) {
        newElement = newElement.getParent();
      }

      return newElement;
    }).filter(Objects::nonNull).collect(Collectors.toList());
  }
}
