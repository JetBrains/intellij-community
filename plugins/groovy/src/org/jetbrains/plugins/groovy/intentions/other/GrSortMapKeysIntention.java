// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.other;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;

import java.util.Arrays;

public class GrSortMapKeysIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    if (parent instanceof GrArgumentLabel) {
      PsiElement pparent = parent.getParent().getParent();
      if (pparent instanceof GrListOrMap && !ErrorUtil.containsError(pparent)) {
        GrListOrMap map = (GrListOrMap)pparent;
        if (map.getInitializers().length == 0) {
          GrNamedArgument[] namedArgs = map.getNamedArguments();
          if (isLiteralKeys(namedArgs)) {
            GrListOrMap newMap = constructNewMap(namedArgs, project);
            map.replace(newMap);
          }
        }
      }
    }
  }

  @NotNull
  private static GrListOrMap constructNewMap(@NotNull GrNamedArgument[] args, Project project) {
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

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(@NotNull PsiElement element) {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrArgumentLabel)) return false;
        if (((GrArgumentLabel)parent).getNameElement() != element) return false;

        final PsiElement grandParent = parent.getParent();
        if (grandParent == null) return false;

        final PsiElement grandGrandParent = grandParent.getParent();
        if (!(grandGrandParent instanceof GrListOrMap)) return false;

        final GrListOrMap map = (GrListOrMap)grandGrandParent;
        return !ErrorUtil.containsError(map) && map.getInitializers().length == 0 && isLiteralKeys(map.getNamedArguments());
      }
    };
  }

  private static boolean isLiteralKeys(GrNamedArgument[] args) {
    return ContainerUtil.and(args, it -> it.getLabel() != null);
  }
}
