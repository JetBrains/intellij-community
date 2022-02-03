// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit.message;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;

public final class CommitMessageSpellCheckingInspection extends BaseCommitMessageInspection {
  private static final Logger LOG = Logger.getInstance(CommitMessageSpellCheckingInspection.class);

  private static final NullableLazyValue<LocalInspectionTool> ourSpellCheckingInspection = atomicLazyNullable(() -> {
    List<InspectionToolWrapper<?, ?>> tools = InspectionToolRegistrar.getInstance().createTools();
    InspectionToolWrapper<?, ?> spellCheckingWrapper = ContainerUtil.find(tools, wrapper -> wrapper.getShortName().equals("SpellCheckingInspection"));
    if (spellCheckingWrapper == null) {
      LOG.info("Could not find default spell checking inspection");
    }
    else if (!(spellCheckingWrapper instanceof LocalInspectionToolWrapper)) {
      LOG.info("Found spell checking wrapper is not local " + spellCheckingWrapper);
    }
    else {
      return ((LocalInspectionToolWrapper)spellCheckingWrapper).getTool();
    }
    return null;
  });

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    LocalInspectionTool tool = ourSpellCheckingInspection.getValue();

    return tool != null ? tool.buildVisitor(holder, isOnTheFly) : super.buildVisitor(holder, isOnTheFly);
  }

  @Override
  public @Nls @NotNull String getDisplayName() {
    return VcsBundle.message("inspection.CommitMessageSpellCheckingInspection.display.name");
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return ObjectUtils.notNull(HighlightDisplayLevel.find("TYPO"), HighlightDisplayLevel.WARNING);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
