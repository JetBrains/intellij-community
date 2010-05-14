/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

import java.util.HashSet;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.06.2009
 */
public class GrNamedArgumentSearchVisitor extends GroovyRecursiveElementVisitor {
  private final String myParamName;
  private final HashSet<String> mySet;

  public GrNamedArgumentSearchVisitor(final String paramName, final HashSet<String> set) {
    myParamName = paramName;
    mySet = set;
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    PsiElement nextSibling = referenceExpression.getNextSibling();
    if (nextSibling instanceof PsiWhiteSpace) {
      nextSibling = nextSibling.getNextSibling();
    }

    final GrExpression expression = referenceExpression.getQualifierExpression();

    if (expression instanceof GrReferenceExpression) {
      if (myParamName.equals(((GrReferenceExpression)expression).getName())) {
        if (!(nextSibling instanceof GrArgumentList)) {
          mySet.add(referenceExpression.getName());
        }
      }
    }
    else if (expression == null && myParamName.equals(referenceExpression.getName())) {
      if (nextSibling instanceof GrArgumentList) {
        GrArgumentList args = (GrArgumentList)nextSibling;

        if (args.isIndexPropertiesList()) {
          GrExpression[] expr = args.getExpressionArguments();

          if (expr.length == 1 && expr[0] instanceof GrLiteral) {
            Object value = ((GrLiteral)expr[0]).getValue();
            if (value instanceof String) {
              mySet.add((String)value);
            }
          }
        }
      }
    }

    super.visitReferenceExpression(referenceExpression);
  }
}
