// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Max Medvedev
 */
public class GrChangeVariableType extends GroovyFix {
  private static final Logger LOG = Logger.getInstance(GrChangeVariableType.class);
  private final String myType;
  private final String myName;

  public GrChangeVariableType(PsiType type, String name) {
    myType = type.getCanonicalText();
    myName = name;
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();

    try {
      final PsiType type = JavaPsiFacade.getElementFactory(project).createTypeFromText(myType, element);

      if (parent instanceof GrVariable) {
        ((GrVariable)parent).setType(type);
      }
      else if (element instanceof GrReferenceExpression &&
               parent instanceof GrAssignmentExpression &&
               ((GrAssignmentExpression)parent).getLValue() == element) {
        final PsiElement resolved = ((GrReferenceExpression)element).resolve();
        if (resolved instanceof GrVariable && !(resolved instanceof GrParameter)) {
          ((GrVariable)resolved).setType(type);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return GroovyBundle.message("change.lvalue.type", myName, myType);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("intention.family.name.change.variable.type");
  }
}
