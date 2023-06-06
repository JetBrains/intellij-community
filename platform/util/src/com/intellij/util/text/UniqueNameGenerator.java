// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class UniqueNameGenerator implements Predicate<String> {
  private final Set<String> myExistingNames = new HashSet<>();

  public <T> UniqueNameGenerator(@NotNull Collection<? extends T> elements, @Nullable Function<? super T, String> namer) {
    for (T t : elements) {
      addExistingName(namer != null ? StringUtil.notNullize(namer.fun(t)) : t.toString());
    }
  }

  public UniqueNameGenerator() {
  }

  @Override
  public final boolean test(@NotNull String candidate) {
    return isUnique(candidate);
  }

  public final boolean isUnique(@NotNull String candidate) {
    return !myExistingNames.contains(candidate);
  }

  public final boolean isUnique(@NotNull String name, @NotNull String prefix, @NotNull String suffix) {
    return test(prefix + name + suffix);
  }

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull Collection<String> existingNames) {
    return generateUniqueName(defaultName, "", "", existingNames);
  }

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull Collection<String> existingNames) {
    return generateUniqueName(defaultName, prefix, suffix, s -> !existingNames.contains(s));
  }

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull Predicate<? super String> validator) {
    return generateUniqueName(defaultName, "", "", validator);
  }

  @ApiStatus.Obsolete
  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, (Predicate<? super String>) validator);
  }

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull Predicate<? super String> validator) {
    return generateUniqueName(defaultName, prefix, suffix, "", "", validator);
  }

  /**
   * @deprecated use {@link UniqueNameGenerator#generateUniqueName(String, String, String, Predicate)} instead
   */
  @Deprecated
  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, prefix, suffix, (Predicate<? super String>) validator);
  }

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix,
                                                            @NotNull String beforeNumber, @NotNull String afterNumber,
                                                            @NotNull Predicate<? super String> validator) {
    String defaultFullName = (prefix + defaultName + suffix).trim();
    if (validator.test(defaultFullName)) {
      return defaultFullName;
    }

    for (int i = 2; ; i++) {
      String fullName = (prefix + defaultName + beforeNumber + i + afterNumber + suffix).trim();
      if (validator.test(fullName)) {
        return fullName;
      }
    }
  }

  @ApiStatus.Obsolete
  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix,
                                                            @NotNull String beforeNumber, @NotNull String afterNumber,
                                                            @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, prefix, suffix, beforeNumber, afterNumber, (Predicate<? super String>) validator);
  }

  public @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix) {
    return generateUniqueName(defaultName, prefix, suffix, "", "");
  }

  public @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull String beforeNumber, @NotNull String afterNumber) {
    String result = generateUniqueName(defaultName, prefix, suffix, beforeNumber, afterNumber, this);
    addExistingName(result);
    return result;
  }

  public void addExistingName(@NotNull String result) {
    myExistingNames.add(result);
  }

  public @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName) {
    return generateUniqueName(defaultName, "", "");
  }
}
