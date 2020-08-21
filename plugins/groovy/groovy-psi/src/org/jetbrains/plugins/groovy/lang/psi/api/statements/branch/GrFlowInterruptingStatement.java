// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.branch;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

/**
 * @author ilyas
 */
public interface GrFlowInterruptingStatement extends GrStatement {
  @Nullable
  PsiElement getLabelIdentifier();

  @Nullable
  String getLabelName();

  @Nullable
  GrStatement findTargetStatement();

  @Nullable
  GrLabeledStatement resolveLabel();

  @NlsSafe
  String getStatementText();
}
