/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi;

import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListSelection<T> {
  @NotNull private final List<T> myList;
  private final int mySelectedIndex;

  private ListSelection(@NotNull List<T> list, int selectedIndex) {
    myList = list;
    if (myList.isEmpty()) {
      mySelectedIndex = -1;
    }
    else if (selectedIndex >= 0 && selectedIndex < list.size()) {
      mySelectedIndex = selectedIndex;
    }
    else {
      mySelectedIndex = 0;
    }
  }

  @NotNull
  public static <V> ListSelection<V> createAt(@NotNull List<V> list, int selectedIndex) {
    return new ListSelection<>(list, selectedIndex);
  }

  @NotNull
  public static <V> ListSelection<V> create(@NotNull List<V> list, V selected) {
    return createAt(list, list.indexOf(selected));
  }

  @NotNull
  public static <V> ListSelection<V> create(@NotNull V[] array, V selected) {
    return create(Arrays.asList(array), selected);
  }

  @NotNull
  public static <V> ListSelection<V> createSingleton(@NotNull V element) {
    return createAt(Collections.singletonList(element), 0);
  }


  @NotNull
  public List<T> getList() {
    return myList;
  }

  public int getSelectedIndex() {
    return mySelectedIndex;
  }

  public boolean isEmpty() {
    return myList.isEmpty();
  }

  @NotNull
  public <V> ListSelection<V> map(@NotNull NullableFunction<T, V> convertor) {
    int newSelectionIndex = -1;
    List<V> result = new ArrayList<>();
    for (int i = 0; i < myList.size(); i++) {
      if (i == mySelectedIndex) newSelectionIndex = result.size();
      V out = convertor.fun(myList.get(i));
      if (out != null) result.add(out);
    }
    return new ListSelection<>(result, newSelectionIndex);
  }
}
