// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.dsl;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchRunLineMarkerProvider extends RunLineMarkerContributor {

  @Nullable
  @Override
  public Info getInfo(@NotNull PsiElement element) {
    if (element.getFirstChild() == null /* todo check is ssr dsl */) {
      final AnAction[] actions = ExecutorAction.getActions();
      return new Info(StructuralSearchRunConfigurationType.ICON, actions, e ->
        StringUtil.join(ContainerUtil.mapNotNull(actions, action -> getText(action, e)), "\n"));
    }
    return null;
  }
}
