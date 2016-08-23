/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.usages.rules;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

public interface UsageFilteringRuleProvider {
  ExtensionPointName<UsageFilteringRuleProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageFilteringRuleProvider");

  @NotNull
  UsageFilteringRule[] getActiveRules(@NotNull Project project);

  @NotNull
  AnAction[] createFilteringActions(@NotNull UsageView view);

  Topic<Runnable> RULES_CHANGED = new Topic<>("usave view rules changed", Runnable.class);
}
