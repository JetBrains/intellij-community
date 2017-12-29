/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitorHelper;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class ParameterCastFix extends GroovyFix {
  @NotNull
  private final PsiType myType;
  @NotNull
  private final String myName;
  private final int myPosition;

  public ParameterCastFix(int position, @NotNull PsiType type) {
    myType = type;
    myPosition = position;

    StringBuilder builder = new StringBuilder();
    builder.append("Cast ");

    builder.append(position + 1);
    switch (position + 1) {
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
    final GrArgumentList list = element instanceof GrArgumentList ? (GrArgumentList)element : PsiUtil.getArgumentsList(element);
    if (list == null) return;

    List<GrExpression> callArguments = GroovyTypeCheckVisitorHelper.getExpressionArgumentsOfCall(list);
    if (callArguments == null || myPosition >= callArguments.size()) return;
    GrExpression expression = callArguments.get(myPosition);

    GrCastFix.doSafeCast(project, myType, expression);
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
    return "Add parameter cast";
  }
}
