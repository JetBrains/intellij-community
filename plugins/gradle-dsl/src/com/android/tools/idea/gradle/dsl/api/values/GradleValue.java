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
package com.android.tools.idea.gradle.dsl.api.values;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Deprecated
public interface GradleValue<T> {
  @Deprecated
  @Nullable
  T value();

  @Deprecated
  @Nullable
  PsiElement getPsiElement();

  @Deprecated
  @Nullable
  VirtualFile getFile();

  @Deprecated
  @Nullable
  String getPropertyName();

  @Deprecated
  @Nullable
  String getDslText();

  @Deprecated
  @NotNull
  Map<String, GradleNotNullValue<Object>> getResolvedVariables();

  @Deprecated
  @NotNull
  static <E> List<E> getValues(@Nullable List<? extends GradleValue<E>> gradleValues) {
    if (gradleValues == null) {
      return ImmutableList.of();
    }

    List<E> values = new ArrayList<>(gradleValues.size());
    for (GradleValue<E> gradleValue : gradleValues) {
      E value = gradleValue.value();
      if (value != null) {
        values.add(value);
      }
    }

    return values;
  }

  @Deprecated
  @NotNull
  static <V> Map<String, V> getValues(@Nullable Map<String, ? extends GradleValue<V>> gradleValues) {
    if (gradleValues == null) {
      return ImmutableMap.of();
    }

    Map<String, V> values = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends GradleValue<V>> gradleValueEntry : gradleValues.entrySet()) {
      V value = gradleValueEntry.getValue().value();
      if (value != null) {
        values.put(gradleValueEntry.getKey(), value);
      }
    }

    return values;
  }
}