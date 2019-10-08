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
package com.intellij.util.text;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author peter
 */
public class UniqueNameGenerator implements Condition<String> {
  private final Set<String> myExistingNames = new THashSet<>();

  public <T> UniqueNameGenerator(@NotNull Collection<? extends T> elements, @Nullable Function<? super T, String> namer) {
    for (T t : elements) {
      addExistingName(namer != null ? StringUtil.notNullize(namer.fun(t)) : t.toString());
    }
  }

  public UniqueNameGenerator() {
  }

  @Override
  public final boolean value(@NotNull String candidate) {
    return isUnique(candidate);
  }

  public final boolean isUnique(@NotNull String candidate) {
    return !myExistingNames.contains(candidate);
  }

  public final boolean isUnique(@NotNull String name, @NotNull String prefix, @NotNull String suffix) {
    return value(prefix + name + suffix);
  }

  @NotNull
  public static String generateUniqueName(@NotNull String defaultName, @NotNull Collection<String> existingNames) {
    return generateUniqueName(defaultName, "", "", existingNames);
  }

  @NotNull
  public static String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull Collection<String> existingNames) {
    return generateUniqueName(defaultName, prefix, suffix, s -> !existingNames.contains(s));
  }

  @NotNull
  public static String generateUniqueName(@NotNull String defaultName, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, "", "", validator);
  }

  @NotNull
  public static String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, prefix, suffix, "", "", validator);
  }

  @NotNull
  public static String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix,
                                          @NotNull String beforeNumber, @NotNull String afterNumber,
                                          @NotNull Condition<? super String> validator) {
    String defaultFullName = (prefix + defaultName + suffix).trim();
    if (validator.value(defaultFullName)) {
      return defaultFullName;
    }

    for (int i = 2; ; i++) {
      String fullName = (prefix + defaultName + beforeNumber + i + afterNumber + suffix).trim();
      if (validator.value(fullName)) {
        return fullName;
      }
    }
  }

  @NotNull
  public String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix) {
    return generateUniqueName(defaultName, prefix, suffix, "", "");
  }

  @NotNull
  public String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull String beforeNumber, @NotNull String afterNumber) {
    String result = generateUniqueName(defaultName, prefix, suffix, beforeNumber, afterNumber, this);
    addExistingName(result);
    return result;
  }

  public void addExistingName(@NotNull String result) {
    myExistingNames.add(result);
  }

  @NotNull
  public String generateUniqueName(@NotNull String defaultName) {
    return generateUniqueName(defaultName, "", "");
  }
}
