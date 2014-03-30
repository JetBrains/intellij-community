/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.actions;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.ex.DiffPanelEx;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import org.jetbrains.annotations.NotNull;

public class IgnoreWhiteSpacesAction extends DiffPanelComboBoxAction<ComparisonPolicy> {
  private static final ComparisonPolicy[] ourActionOrder = new ComparisonPolicy[]{
    ComparisonPolicy.DEFAULT,
    ComparisonPolicy.TRIM_SPACE,
    ComparisonPolicy.IGNORE_SPACE
  };

  public IgnoreWhiteSpacesAction() {
    super(ourActionOrder);
    addAction(ComparisonPolicy.DEFAULT, new IgnoringPolicyAction(DiffBundle.message("diff.acton.ignore.whitespace.policy.do.not.ignore"), ComparisonPolicy.DEFAULT));
    addAction(ComparisonPolicy.TRIM_SPACE, new IgnoringPolicyAction(DiffBundle.message("diff.acton.ignore.whitespace.policy.leading.and.trailing"), ComparisonPolicy.TRIM_SPACE));
    addAction(ComparisonPolicy.IGNORE_SPACE, new IgnoringPolicyAction(DiffBundle.message("diff.acton.ignore.whitespace.policy.all"), ComparisonPolicy.IGNORE_SPACE));
  }

  @NotNull
  @Override
  protected String getActionName() {
    return DiffBundle.message("ignore.whitespace.acton.name");
  }

  @NotNull
  @Override
  protected ComparisonPolicy getCurrentOption(@NotNull DiffPanelEx diffPanel) {
    return diffPanel.getComparisonPolicy();
  }

  private static class IgnoringPolicyAction extends DiffPanelAction {
    private final ComparisonPolicy myPolicy;

    public IgnoringPolicyAction(String text, ComparisonPolicy policy) {
      super(text);
      myPolicy = policy;
    }

    @Override
    protected void perform(@NotNull DiffPanelEx diffPanel) {
      diffPanel.setComparisonPolicy(myPolicy);
    }
  }
}
