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

package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ui.PlaceHolder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author Maxim.Mossienko
 */
public class TreeStructureUtil {
  public static final String PLACE = "StructureViewPopup";

  private TreeStructureUtil() {}

  public static Object[] getChildElementsFromTreeStructure(AbstractTreeStructure treeStructure, Object element) {
    final Object[] items = treeStructure.getChildElements(element);
    HashSet<Object> viewedItems = new HashSet<>();

    for (Object item : items) {
      if (viewedItems.contains(item)) continue;
      viewedItems.add(item);
    }

    return items;
  }

  public static boolean isInStructureViewPopup(@NotNull PlaceHolder<String> model) {
    return PLACE.equals(model.getPlace());
  }

  @NonNls
  public static String getPropertyName(String propertyName) {
    return propertyName + ".file.structure.state";
  }
}
