// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      LOG.info("Could not find default spell checking inspection");
    }
    else if (!(spellCheckingWrapper instanceof LocalInspectionToolWrapper)) {
      LOG.info("Found spell checking wrapper is not local " + spellCheckingWrapper);
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