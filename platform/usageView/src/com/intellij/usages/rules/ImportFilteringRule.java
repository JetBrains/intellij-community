/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.usages.rules.UsageFilteringRule;

/**
 * User: Maxim.Mossienko
 * Date: 11.03.2010
 * Time: 19:08:33
 */
public abstract class ImportFilteringRule implements UsageFilteringRule {
  public static final ExtensionPointName<ImportFilteringRule> EP_NAME = ExtensionPointName.create("com.intellij.importFilteringRule");
}
