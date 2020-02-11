// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

public class UnknownUsagesInUnloadedModules extends UsageAdapter implements Usage {
  private final String myExplanationText;

  public UnknownUsagesInUnloadedModules(Collection<UnloadedModuleDescription> unloadedModules) {
    String modulesText = unloadedModules.size() > 1 ? unloadedModules.size() + " unloaded modules"
                                                    : "unloaded module '" + Objects
                                                      .requireNonNull(ContainerUtil.getFirstItem(unloadedModules)).getName() + "'";
    myExplanationText = "There may be usages in " + modulesText + ". Load all modules and repeat refactoring to ensure that all the usages will be updated.";
  }

  @NotNull
  @Override
  public UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @Override
      public TextChunk @NotNull [] getText() {
        return new TextChunk[] {new TextChunk(SimpleTextAttributes.REGULAR_ATTRIBUTES.toTextAttributes(), myExplanationText)};
      }

      @NotNull
      @Override
      public String getPlainText() {
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
