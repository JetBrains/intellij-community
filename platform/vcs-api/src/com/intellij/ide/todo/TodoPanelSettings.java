/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.todo;

import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;

public class TodoPanelSettings {
  @OptionTag(tag = "are-packages-shown", nameAttribute = "")
  public boolean arePackagesShown;
  @OptionTag(tag = "are-modules-shown", nameAttribute = "")
  public boolean areModulesShown;
  @OptionTag(tag = "flatten-packages", nameAttribute = "")
  public boolean areFlattenPackages;
  @OptionTag(tag = "is-autoscroll-to-source", nameAttribute = "")
  public boolean isAutoScrollToSource;
  @OptionTag(tag = "todo-filter", nameAttribute = "", valueAttribute = "name")
  public String todoFilterName;
  @OptionTag(tag = "is-preview-enabled", nameAttribute = "")
  public boolean showPreview;

  public TodoPanelSettings() {
  }

  public TodoPanelSettings(@NotNull TodoPanelSettings s) {
    arePackagesShown = s.arePackagesShown;
    areModulesShown = s.areModulesShown;
    areFlattenPackages = s.areFlattenPackages;
    isAutoScrollToSource = s.isAutoScrollToSource;
    todoFilterName = s.todoFilterName;
  }
}
