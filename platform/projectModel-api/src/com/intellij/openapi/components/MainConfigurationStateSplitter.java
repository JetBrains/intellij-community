/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public abstract class MainConfigurationStateSplitter extends StateSplitterEx {
  @Override
  public final List<Pair<Element, String>> splitState(@NotNull Element state) {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    List<Pair<Element, String>> result = new SmartList<>();
    for (Iterator<Element> iterator = state.getChildren(getSubStateTagName()).iterator(); iterator.hasNext(); ) {
      Element element = iterator.next();
      iterator.remove();
      result.add(createItem(getSubStateFileName(element), generator, element));
    }
    if (!JDOMUtil.isEmpty(state)) {
      result.add(createItem(getComponentStateFileName(), generator, state));
    }
    return result;
  }

  @Override
  public final void mergeStateInto(@NotNull Element target, @NotNull Element subState) {
    mergeStateInto(target, subState, getSubStateTagName());
  }

  @NotNull
  protected String getSubStateFileName(@NotNull Element element) {
    for (Element option : element.getChildren("option")) {
      if (option.getAttributeValue("name").equals("myName")) {
        return option.getAttributeValue("value");
      }
    }
    throw new IllegalStateException();
  }

  @NotNull
  protected abstract String getComponentStateFileName();

  @NotNull
  protected abstract String getSubStateTagName();
}