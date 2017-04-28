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
package com.intellij.vcs.commit;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AtomicNullableLazyValue;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.AtomicNullableLazyValue.createValue;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.find;

public class CommitMessageSpellCheckingInspection extends BaseCommitMessageInspection {

  private static final Logger LOG = Logger.getInstance(CommitMessageSpellCheckingInspection.class);

  private static final AtomicNullableLazyValue<LocalInspectionTool> ourSpellCheckingInspection = createValue(() -> {
    LocalInspectionTool result = null;
    List<InspectionToolWrapper> tools = InspectionToolRegistrar.getInstance().createTools();
    InspectionToolWrapper spellCheckingWrapper = find(tools, wrapper -> wrapper.getShortName().equals("SpellCheckingInspection"));

    if (spellCheckingWrapper == null) {
      LOG.error("Could not find default spell checking inspection");
    }
    else if (!(spellCheckingWrapper instanceof LocalInspectionToolWrapper)) {
      LOG.error("Found spell checking wrapper is not local " + spellCheckingWrapper);
    }
    else {
      result = ((LocalInspectionToolWrapper)spellCheckingWrapper).getTool();
    }

    return result;
  });

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LocalInspectionTool tool = ourSpellCheckingInspection.getValue();

    return tool != null ? tool.buildVisitor(holder, isOnTheFly) : super.buildVisitor(holder, isOnTheFly);
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Spelling";
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return notNull(HighlightDisplayLevel.find("TYPO"), HighlightDisplayLevel.WARNING);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}