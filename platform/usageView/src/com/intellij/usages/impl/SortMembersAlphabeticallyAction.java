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
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageViewSettings;

/**
* @author cdr
*/
class SortMembersAlphabeticallyAction extends RuleAction {

  SortMembersAlphabeticallyAction(UsageViewImpl usageView) {
    super(usageView, UsageViewBundle.message("sort.alphabetically.action.text"), AllIcons.ObjectBrowser.Sorted);
  }

  @Override
  protected boolean getOptionValue() {
    return UsageViewSettings.getInstance().IS_SORT_MEMBERS_ALPHABETICALLY;
  }

  @Override
  protected void setOptionValue(final boolean value) {
    UsageViewSettings.getInstance().IS_SORT_MEMBERS_ALPHABETICALLY = value;
  }
}
