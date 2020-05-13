// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.psi.PsiClass;
import com.intellij.refactoring.move.moveInner.MoveInnerHandler;
import com.intellij.refactoring.move.moveInner.MoveInnerOptions;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static org.jetbrains.plugins.groovy.refactoring.move.MoveGroovyClassHandler.removeAllAliasImportedUsages;

public class MoveGroovyInnerHandler implements MoveInnerHandler {

  @Override
  public @NotNull PsiClass copyClass(@NotNull MoveInnerOptions options) {
    throw new UnsupportedOperationException("Move inner class is unsupported in Groovy");
  }

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
    removeAllAliasImportedUsages(results);
  }
}
