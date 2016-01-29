/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.intellij.util.ui.accessibility;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AccessibleContextUtil {
  public static void setName(@NotNull JComponent component, @NotNull JComponent source) {
    String name = source.getAccessibleContext().getAccessibleName();
    if (name != null) {
      component.getAccessibleContext().setAccessibleName(name);
    }
  }

  public static void setCombinedName(@NotNull JComponent component,
                                     @NotNull JComponent j1, @NotNull String separator, @NotNull JComponent j2) {
    component.getAccessibleContext().setAccessibleName(
      combineAccessibleStrings(
        j1.getAccessibleContext().getAccessibleName(),
        separator,
        j2.getAccessibleContext().getAccessibleName()));
  }

  public static void setCombinedName(@NotNull JComponent component,
                                     @NotNull JComponent j1, @NotNull String separator1,
                                     @NotNull JComponent j2, @NotNull String separator2, @NotNull JComponent j3) {
    component.getAccessibleContext().setAccessibleName(
      combineAccessibleStrings(
        j1.getAccessibleContext().getAccessibleName(),
        separator1,
        j2.getAccessibleContext().getAccessibleName(),
        separator2,
        j3.getAccessibleContext().getAccessibleName()));
  }

  public static void setDescription(@NotNull JComponent component, @NotNull JComponent source) {
    String description = source.getAccessibleContext().getAccessibleDescription();
    if (description != null) {
      component.getAccessibleContext().setAccessibleDescription(description);
    }
  }

  public static void setCombinedDescription(@NotNull JComponent component, @NotNull JComponent j1,
                                            @NotNull String separator, @NotNull JComponent j2) {
    component.getAccessibleContext().setAccessibleDescription(
      combineAccessibleStrings(
        j1.getAccessibleContext().getAccessibleDescription(),
        separator,
        j2.getAccessibleContext().getAccessibleDescription()));
  }

  public static void setCombinedDescription(@NotNull JComponent component, @NotNull JComponent j1, @NotNull String separator1,
                                            @NotNull JComponent j2, @NotNull String separator2, @NotNull JComponent j3) {
    component.getAccessibleContext().setAccessibleDescription(
      combineAccessibleStrings(
        j1.getAccessibleContext().getAccessibleDescription(),
        separator1,
        j2.getAccessibleContext().getAccessibleDescription(),
        separator2,
        j3.getAccessibleContext().getAccessibleDescription()));
  }

  public static @Nullable String combineAccessibleStrings(@Nullable String s1, @NotNull String separator, @Nullable String s2) {
    if (StringUtil.isEmpty(s1))
      return s2;
    if (StringUtil.isEmpty(s2))
      return s1;
    return String.format("%s%s%s", s1, separator, s2);
  }

  public static @Nullable String combineAccessibleStrings(@Nullable String s1, @NotNull String separator1, @Nullable  String s2,
                                                          @NotNull String separator2, @Nullable  String s3) {
    return combineAccessibleStrings(combineAccessibleStrings(s1, separator1, s2), separator2, s3);
  }
}
