// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentModCommandFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class SuppressByGroovyCommentFix extends SuppressByCommentModCommandFix {

  public SuppressByGroovyCommentFix(@NotNull String toolId) {
    super(toolId, GrStatement.class);
  }

  @Override
  public @Nullable PsiElement getContainer(PsiElement context) {
    return PsiUtil.findEnclosingStatement(context);
  }
}
