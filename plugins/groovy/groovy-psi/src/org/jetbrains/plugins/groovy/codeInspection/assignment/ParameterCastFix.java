// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class ParameterCastFix extends GroovyFix {
  private final GrExpression myArgument;
  private final PsiType myType;
  private final String myName;

  public ParameterCastFix(int param, @NotNull PsiType type, @NotNull GrExpression argument) {
    myArgument = argument;
    myType = PsiImplUtil.normalizeWildcardTypeByPosition(type, argument);

    StringBuilder builder = new StringBuilder();
    builder.append("Cast ");

    builder.append(param + 1);
    switch (param + 1) {
      case 1:
        builder.append("st");
        break;
      case 2:
        builder.append("nd");
        break;
      case 3:
        builder.append("rd");
        break;
      default:
        builder.append("th");
        break;
    }
    builder.append(" parameter to ").append(myType.getPresentableText());


    myName = builder.toString();
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    final GrArgumentList list = element instanceof GrArgumentList ? (GrArgumentList)element :PsiUtil.getArgumentsList(element);
    if (list == null) return;

    GrCastFix.doSafeCast(project, myType, myArgument);
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Add cast";
  }
}
