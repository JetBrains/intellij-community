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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @deprecated use @see com.intellij.util.ListSelection instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
public class ListSelection<T> {
  @NotNull private final com.intellij.util.ListSelection<T> myInstance;

  private ListSelection(@NotNull List<T> list, int selectedIndex) {
    myInstance = com.intellij.util.ListSelection.createAt(list, selectedIndex);
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
  public static <V> ListSelection<V> create(V @NotNull [] array, V selected) {
    return create(Arrays.asList(array), selected);
  }

  @NotNull
  public static <V> ListSelection<V> createSingleton(@NotNull V element) {
    return createAt(Collections.singletonList(element), 0);
  }


  @NotNull
  public List<T> getList() {
    return myInstance.getList();
  }

  public int getSelectedIndex() {
    return myInstance.getSelectedIndex();
  }

  public boolean isEmpty() {
    return myInstance.getList().isEmpty();
  }

  @NotNull
  public <V> ListSelection<V> map(@NotNull NullableFunction<? super T, ? extends V> convertor) {
    int newSelectionIndex = -1;
    List<V> result = new ArrayList<>();
    for (int i = 0; i < getList().size(); i++) {
      if (i == getSelectedIndex()) newSelectionIndex = result.size();
      V out = convertor.fun(getList().get(i));
      if (out != null) result.add(out);
    }
    return new ListSelection<>(result, newSelectionIndex);
  }
}
