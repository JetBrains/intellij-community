// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;

public class GrReplaceWithImportFix extends PsiUpdateModCommandQuickFix {

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
    GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)startElement.getParent());
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("replace.qualified.name.with.import");
  }
}
