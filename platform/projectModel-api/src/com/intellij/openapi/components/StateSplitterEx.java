// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.SerializationConstants;

import java.util.List;

public abstract class StateSplitterEx implements StateSplitter {
  @Override
  public abstract @NotNull List<Pair<Element, String>> splitState(@NotNull Element state);

  public void mergeStateInto(@NotNull Element target, @NotNull Element subState) {
    target.addContent(subState);
  }

  @Override
  public final void mergeStatesInto(@NotNull Element target, Element @NotNull [] elements) {
    throw new IllegalStateException();
  }

  protected static @NotNull List<Pair<Element, String>> splitState(@NotNull Element state, @NotNull String attributeName) {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    List<Pair<Element, String>> result = new SmartList<>();
    for (Element subState : state.getChildren()) {
      if (subState.getAttribute(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE) == null) {
        result.add(createItem(subState.getAttributeValue(attributeName), generator, subState));
      }
    }
    return result;
  }

  protected static @NotNull Pair<Element, String> createItem(@NotNull String fileName, @NotNull UniqueNameGenerator generator, @NotNull Element element) {
    return Pair.create(element, generator.generateUniqueName(FileUtil.sanitizeFileName(fileName)) + ".xml");
  }

  protected static void mergeStateInto(@NotNull Element target, @NotNull Element subState, @NotNull String subStateName) {
    if (subState.getName().equals(subStateName)) {
      target.addContent(subState);
    }
    else {
      JDOMUtil.merge(target, subState);
    }
  }
}