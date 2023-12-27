// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public abstract class MainConfigurationStateSplitter extends StateSplitterEx {
  @Override
  public final @NotNull List<Pair<Element, String>> splitState(@NotNull Element state) {
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

  protected @NotNull String getSubStateFileName(@NotNull Element element) {
    for (Element option : element.getChildren("option")) {
      if (option.getAttributeValue("name").equals("myName")) {
        return option.getAttributeValue("value");
      }
    }
    throw new IllegalStateException();
  }

  protected abstract @NotNull
  @NlsSafe String getComponentStateFileName();

  protected abstract @NotNull String getSubStateTagName();
}