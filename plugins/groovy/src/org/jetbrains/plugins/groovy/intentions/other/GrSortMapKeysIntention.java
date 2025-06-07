// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.GrPsiUpdateIntention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;

import java.util.Arrays;

public final class GrSortMapKeysIntention extends GrPsiUpdateIntention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    PsiElement parent = element.getParent();

    if (parent instanceof GrArgumentLabel) {
      PsiElement pparent = parent.getParent().getParent();
      if (pparent instanceof GrListOrMap map && !ErrorUtil.containsError(pparent)) {
        if (map.getInitializers().length == 0) {
          GrNamedArgument[] namedArgs = map.getNamedArguments();
          if (isLiteralKeys(namedArgs)) {
            GrListOrMap newMap = constructNewMap(namedArgs, context.project());
            map.replace(newMap);
          }
        }
      }
    }
  }

  private static @NotNull GrListOrMap constructNewMap(GrNamedArgument @NotNull [] args, Project project) {
    StringBuilder builder = new StringBuilder();

    builder.append("[");

    Arrays.sort(args, (o1, o2) -> {
      final String l1 = o1.getLabelName();
      final String l2 = o2.getLabelName();
      assert l1 != null && l2 != null;

      return l1.compareTo(l2);
    });

    for (GrNamedArgument arg : args) {
      builder.append(arg.getText()).append(",\n");
    }


    builder.replace(builder.length() - 2, builder.length(), "]");

    return (GrListOrMap)GroovyPsiElementFactory.getInstance(project).createExpressionFromText(builder);
  }

  @Override
  protected @NotNull PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrArgumentLabel)) return false;
        if (((GrArgumentLabel)parent).getNameElement() != element) return false;

        final PsiElement grandParent = parent.getParent();
        if (grandParent == null) return false;

        final PsiElement grandGrandParent = grandParent.getParent();
        if (!(grandGrandParent instanceof GrListOrMap map)) return false;

        return !ErrorUtil.containsError(map) && map.getInitializers().length == 0 && isLiteralKeys(map.getNamedArguments());
      }
    };
  }

  private static boolean isLiteralKeys(GrNamedArgument[] args) {
    return ContainerUtil.and(args, it -> it.getLabel() != null);
  }
}
