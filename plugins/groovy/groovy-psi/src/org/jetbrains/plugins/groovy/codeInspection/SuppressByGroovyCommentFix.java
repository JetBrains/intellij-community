// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInsight.daemon.impl.actions.SuppressByCommentFix;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public class SuppressByGroovyCommentFix extends SuppressByCommentFix {

  public SuppressByGroovyCommentFix(@NotNull String toolId) {
    super(toolId, GrStatement.class);
  }

  @Override
  @Nullable
  public PsiElement getContainer(PsiElement context) {
    return PsiUtil.findEnclosingStatement(context);
  }
}
