/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GutterActionRenderer extends GutterIconRenderer implements DumbAware {
  private final AnAction myAction;

  public GutterActionRenderer(@NotNull AnAction action) {
    myAction = action;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return myAction.getTemplatePresentation().getIcon();
  }

  @Override
  public AnAction getClickAction() {
    return myAction;
  }

  @Override
  public String getTooltipText() {
    return myAction.getTemplatePresentation().getText();
  }

  @Override
  public boolean isNavigateAction() {
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GutterActionRenderer that = (GutterActionRenderer)o;

    if (!myAction.equals(that.myAction)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myAction.hashCode();
  }
}
