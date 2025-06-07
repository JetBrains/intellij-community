// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrDisjunctionTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrRemoveExceptionFix extends PsiUpdateModCommandAction<PsiElement> {
  private final @IntentionName String myText;
  private final boolean myDisjunction;

  public GrRemoveExceptionFix(boolean isDisjunction) {
    super(PsiElement.class);
    myDisjunction = isDisjunction;
    if (isDisjunction) {
      myText = GroovyBundle.message("remove.exception");
    }
    else {
      myText = GroovyBundle.message("remove.catch.block");
    }
  }
  
  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("try.catch.fix");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiElement target = myDisjunction ? findTypeElementInDisjunction(element, context.offset()) : findCatch(element);
    return target == null ? null : Presentation.of(myText);
  }

  private static @Nullable GrTypeElement findTypeElementInDisjunction(@NotNull PsiElement at, int offset) {
    final GrDisjunctionTypeElement disjunction = PsiTreeUtil.getParentOfType(at, GrDisjunctionTypeElement.class);
    if (disjunction == null) return null;
    for (GrTypeElement element : disjunction.getTypeElements()) {
      if (element.getTextRange().contains(offset)) {
        return element;
      }
    }
    return null;
  }

  private static @Nullable GrCatchClause findCatch(@NotNull PsiElement at) {
    return PsiTreeUtil.getParentOfType(at, GrCatchClause.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement at, @NotNull ModPsiUpdater updater) {
    if (myDisjunction) {
      final GrTypeElement element = findTypeElementInDisjunction(at, context.offset());
      if (element != null) {
        element.delete();
      }
    }
    else {
      final GrCatchClause aCatch = findCatch(at);
      if (aCatch != null) {
        aCatch.delete();
      }
    }
  }
}
