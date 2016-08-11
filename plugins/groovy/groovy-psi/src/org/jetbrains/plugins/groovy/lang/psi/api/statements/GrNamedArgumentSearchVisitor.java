/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.06.2009
 */
public class GrNamedArgumentSearchVisitor extends GroovyRecursiveElementVisitor {

  public static final NamedArgumentDescriptor CODE_NAMED_ARGUMENTS_DESCR = NamedArgumentDescriptor.SIMPLE_AS_LOCAL_VAR;

  private static final List<String> METHOD_NAMES = Arrays.asList("containsKey", "remove", "get");

  private final Map<String, NamedArgumentDescriptor> myResult = new HashMap<>();

  private final String myFirstArgumentName;

  public GrNamedArgumentSearchVisitor(String firstArgumentName) {
    myFirstArgumentName = firstArgumentName;
  }

  public Map<String, NamedArgumentDescriptor> getResult() {
    return myResult;
  }

  private void extractArguments(@NotNull GrArgumentList argumentList) {
    GrExpression[] expr = argumentList.getExpressionArguments();

    if (expr.length == 1 && expr[0] instanceof GrLiteral) {
      Object value = ((GrLiteral)expr[0]).getValue();
      if (value instanceof String) {
        String s = (String)value;
        if (StringUtil.isJavaIdentifier(s)) {
          add((String)value);
        }
      }
    }
  }

  private void add(String refName) {
    myResult.put(refName, CODE_NAMED_ARGUMENTS_DESCR);
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    if (myFirstArgumentName.equals(referenceExpression.getReferenceName()) && !referenceExpression.isQualified()) {
      PsiElement parent = referenceExpression.getParent();

      if (parent instanceof GrReferenceExpression) {
        GrReferenceExpression parentRef = (GrReferenceExpression)parent;

        PsiElement parentParent = parentRef.getParent();

        if (parentParent instanceof GrMethodCallExpression) {
          if (METHOD_NAMES.contains(parentRef.getReferenceName())) {
            extractArguments(((GrMethodCallExpression)parentParent).getArgumentList());
          }
        }
        else {
          add(parentRef.getReferenceName());
        }
      }
      else if (parent instanceof GrIndexProperty) {
        GrIndexProperty indexProperty = (GrIndexProperty)parent;
        extractArguments(indexProperty.getArgumentList());
      }
    }

    super.visitReferenceExpression(referenceExpression);
  }

  public static Map<String, NamedArgumentDescriptor> find(GrVariable variable) {
    final GrExpression initializerGroovy = variable.getInitializerGroovy();

    if (!(initializerGroovy instanceof GrClosableBlock)) {
      return Collections.emptyMap();
    }

    final GrClosableBlock closure = (GrClosableBlock)initializerGroovy;
    final GrParameter[] parameters = closure.getAllParameters();
    if (parameters.length == 0) return Collections.emptyMap();

    GrParameter parameter = parameters[0];

    GrNamedArgumentSearchVisitor visitor = new GrNamedArgumentSearchVisitor(parameter.getName());
    closure.accept(visitor);
    return visitor.getResult();
  }
}
