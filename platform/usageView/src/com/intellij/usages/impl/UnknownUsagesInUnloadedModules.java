// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.stream.Collectors;


@ApiStatus.Internal
public class UnknownUsagesInUnloadedModules extends UsageAdapter implements Usage {
  private final String myExplanationText;

  private static final int LISTED_MODULES_LIMIT = 10;

  public UnknownUsagesInUnloadedModules(Collection<UnloadedModuleDescription> unloadedModules) {
    int n = unloadedModules.size();
    String modulesText;
    if (n == 1) {
      String theName = unloadedModules.iterator().next().getName();
      modulesText = UsageViewBundle.message("message.part.unloaded.module.0", theName);
    }
    else if (n <= LISTED_MODULES_LIMIT) {
      String listStr = StringUtil.join(unloadedModules, m -> m.getName(), ", ");
      modulesText = UsageViewBundle.message("message.part.small.number.of.unloaded.modules", n, listStr);
    }
    else {
      String listStr = unloadedModules.stream()
        .limit(LISTED_MODULES_LIMIT)
        .map(m -> m.getName())
        .collect(Collectors.joining(", "));
      modulesText = UsageViewBundle.message("message.part.large.number.of.unloaded.modules", n, listStr, n - LISTED_MODULES_LIMIT);
    }

    myExplanationText = UsageViewBundle.message(
      "message.there.may.be.usages.in.0.load.all.modules.and.repeat.refactoring.to.ensure.that.all.the.usages.will.be.updated",
      modulesText);
  }

  @Override
  public @NotNull UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @Override
      public TextChunk @NotNull [] getText() {
        return new TextChunk[] {new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), myExplanationText)};
      }

      @Override
      public @NotNull String getPlainText() {
        return myExplanationText;
      }

      @Override
      public Icon getIcon() {
        return AllIcons.General.Warning;
      }

      @Override
      public String getTooltipText() {
        return null;
      }
    };
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
