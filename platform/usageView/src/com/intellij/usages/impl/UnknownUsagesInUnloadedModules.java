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
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public class UnknownUsagesInUnloadedModules extends UsageAdapter implements Usage {
  private final String myExplanationText;

  public UnknownUsagesInUnloadedModules(Collection<UnloadedModuleDescription> unloadedModules) {
    String modulesText = unloadedModules.size() > 1 ? unloadedModules.size() + " unloaded modules"
                                                    : "unloaded module '" + ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(unloadedModules)).getName() + "'";
    myExplanationText = "There may be usages in " + modulesText + ". Load all modules and repeat refactoring to ensure that all the usages will be updated.";
  }

  @NotNull
  @Override
  public UsagePresentation getPresentation() {
    return new UsagePresentation() {
      @NotNull
      @Override
      public TextChunk[] getText() {
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
