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
package com.android.tools.idea.gradle.dsl.model.notifications;

import com.android.tools.idea.gradle.dsl.api.BuildModelNotification;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class IncompleteParsingNotification implements BuildModelNotification {
  private final List<String> myUnknownElementNames = new ArrayList<>();

  public IncompleteParsingNotification() {
  }

  public void addUnknownElement(@NotNull PsiElement element) {
    myUnknownElementNames.add(element.getClass().getSimpleName());
  }

  @Override
  public boolean isCorrectionAvailable() {
    return false;
  }

  @Override
  public void correct() { }

  @Override
  @NotNull
  public NotificationType getType() {
    return NotificationType.INCOMPLETE_PARSE;
  }

  @NotNull
  public String toString() {
    return "Found the following unknown element types while parsing: " + StringUtil.join(myUnknownElementNames, ", ");
  }
}
