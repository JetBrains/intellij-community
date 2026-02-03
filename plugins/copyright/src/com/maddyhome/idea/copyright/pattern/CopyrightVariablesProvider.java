// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.pattern;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Allows providing language dependant velocity variables
 */
public abstract class CopyrightVariablesProvider {
  /**
   * Fill {@code context} map with variable name/bean pairs, based on current {@code project}, {@code module}, {@code file}
   */
  public abstract void collectVariables(@NotNull Map<String, Object> context, Project project, Module module, @NotNull PsiFile file);
}
