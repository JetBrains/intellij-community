// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class UniqueNameGenerator implements Condition<String> {
  private final Set<String> myExistingNames = new HashSet<>();

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

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull Collection<String> existingNames) {
    return generateUniqueName(defaultName, "", "", existingNames);
  }

  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull @Unmodifiable Collection<String> existingNames) {
    return generateUniqueName(defaultName, prefix, suffix, s -> !existingNames.contains(s));
  }

  /**
   * Generates a unique name. Derived names are numbered starting from 2.
   *
   * @param defaultName original symbol name
   * @param validator a predicate that returns false if a supplied name is already used and we cannot use it
   * @return the name based on the defaultName, which is definitely not used (typically by adding a numeric suffix)
   */
  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, "", "", validator);
  }

  /**
   * Generates a unique name. Derived names are numbered starting from 1.
   *
   * @param defaultName original symbol name
   * @param validator a predicate that returns false if a supplied name is already used and we cannot use it
   * @return the name based on the defaultName, which is definitely not used (typically by adding a numeric suffix)
   */
  public static @NlsSafe @NotNull String generateUniqueNameOneBased(@NotNull String defaultName, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, "", "", "", "", validator, 1);
  }

  /**
   * Generates a unique name
   *
   * @param defaultName original symbol name
   * @param prefix      prefix to add before defaultName
   * @param suffix      suffix to add after defaultName
   * @param validator   a predicate that returns false if a supplied name is already used and we cannot use it
   * @return the name based on the defaultName, which is definitely not used (typically by adding a numeric suffix)
   */
  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix, @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, prefix, suffix, "", "", validator);
  }

  /**
   * Generates a unique name
   *
   * @param defaultName original symbol name
   * @param prefix prefix to add before defaultName
   * @param suffix suffix to add after defaultName
   * @param beforeNumber infix to separate defaultName and number
   * @param afterNumber infix to separate number and suffix
   * @param validator a predicate that returns false if a supplied name is already used and we cannot use it
   * @return the name based on the defaultName, which is definitely not used (typically by adding a numeric suffix)
   */
  public static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix,
                                                            @NotNull String beforeNumber, @NotNull String afterNumber,
                                                            @NotNull Condition<? super String> validator) {
    return generateUniqueName(defaultName, prefix, suffix, beforeNumber, afterNumber, validator, 2);
  }

  private static @NlsSafe @NotNull String generateUniqueName(@NotNull String defaultName, @NotNull String prefix, @NotNull String suffix,
                                                             @NotNull String beforeNumber, @NotNull String afterNumber,
                                                             @NotNull Condition<? super String> validator, int startingNumber) {
    String defaultFullName = (prefix + defaultName + suffix).trim();
    if (validator.value(defaultFullName)) {
      return defaultFullName;
    }

    String baseName = defaultName;
    Pattern pattern = Pattern.compile("(.+?)" + Pattern.quote(beforeNumber) + "(\\d{1,9})");
    java.util.regex.Matcher matcher = pattern.matcher(baseName);
    int index = startingNumber;
    if (matcher.matches()) {
      baseName = matcher.group(1);
      index = Integer.parseInt(matcher.group(2)) + 1;
    }
    while (true) {
      String name = (prefix + baseName + beforeNumber + (index++) + afterNumber + suffix).trim();
      if (validator.value(name)) {
        return name;
      }
    }
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
