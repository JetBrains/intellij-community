/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

@SuppressWarnings("deprecation")
public abstract class StateSplitterEx implements StateSplitter {
  @Override
  public abstract List<Pair<Element, String>> splitState(@NotNull Element state);

  public void mergeStateInto(@NotNull Element target, @NotNull Element subState) {
    target.addContent(subState);
  }

  @Override
  public final void mergeStatesInto(Element target, Element[] elements) {
    throw new IllegalStateException();
  }

  @NotNull
  protected static List<Pair<Element, String>> splitState(@NotNull Element state, @NotNull String attributeName) {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    List<Pair<Element, String>> result = new SmartList<>();
    for (Element subState : state.getChildren()) {
      result.add(createItem(generator, subState, attributeName));
    }
    return result;
  }

  @NotNull
  protected static Pair<Element, String> createItem(@NotNull UniqueNameGenerator generator, @NotNull Element element, @NotNull String attributeName) {
    return createItem(element.getAttributeValue(attributeName), generator, element);
  }

  @NotNull
  protected static Pair<Element, String> createItem(@NotNull String fileName, @NotNull UniqueNameGenerator generator, @NotNull Element element) {
    return Pair.create(element, generator.generateUniqueName(FileUtil.sanitizeFileName(fileName)) + ".xml");
  }

  protected static void mergeStateInto(@NotNull Element target, @NotNull Element subState, @NotNull String subStateName) {
    if (subState.getName().equals(subStateName)) {
      target.addContent(subState);
    }
    else {
      for (Iterator<Element> iterator = subState.getChildren().iterator(); iterator.hasNext(); ) {
        Element configuration = iterator.next();
        iterator.remove();
        target.addContent(configuration);
      }
      for (Iterator<Attribute> iterator = subState.getAttributes().iterator(); iterator.hasNext(); ) {
        Attribute attribute = iterator.next();
        iterator.remove();
        target.setAttribute(attribute);
      }
    }
  }
}