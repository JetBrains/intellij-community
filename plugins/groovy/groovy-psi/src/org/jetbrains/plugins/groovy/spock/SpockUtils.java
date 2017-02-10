/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.spock;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class SpockUtils {

  public static final String SPEC_CLASS_NAME = "spock.lang.Specification";

  private static final LightCacheKey<Map<String, SpockVariableDescriptor>> KEY = LightCacheKey.create();

  private SpockUtils() {

  }

  public static Map<String, SpockVariableDescriptor> getVariableMap(@NotNull GrMethod method) {
    GrMethod originalMethod;

    PsiFile containingFile = method.getContainingFile();
    if (containingFile != containingFile.getOriginalFile()) {
      int methodOffset = method.getTextOffset();
      PsiElement originalPlace = containingFile.getOriginalFile().findElementAt(methodOffset);
      originalMethod = PsiTreeUtil.getParentOfType(originalPlace, GrMethod.class);
      assert originalMethod != null : containingFile.getOriginalFile().getText().substring(Math.max(0, methodOffset - 50), Math.min(methodOffset + 50, containingFile.getOriginalFile().getText().length()));
    }
    else {
      originalMethod = method;
    }

    Map<String, SpockVariableDescriptor> cachedValue = KEY.getCachedValue(originalMethod);
    if (cachedValue == null) {
      cachedValue = createVariableMap(originalMethod);

      cachedValue = KEY.putCachedValue(originalMethod, cachedValue);
    }

    return cachedValue;
  }

  // See org.spockframework.compiler.WhereBlockRewriter
  public static Map<String, SpockVariableDescriptor> createVariableMap(GrMethod method) {
    GrOpenBlock block = method.getBlock();
    if (block == null) return Collections.emptyMap();

    PsiElement elementUnderLabel = null;
    PsiElement elementAfterLabel = null;

    main:
    for (PsiElement e = block.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrLabeledStatement) {
        GrLabeledStatement l = (GrLabeledStatement)e;

        elementAfterLabel = l.getNextSibling();

        while (true) {
          GrStatement statement = l.getStatement();

          if ("where".equals(l.getName())) {
            elementUnderLabel = statement;
            break main;
          }

          if (statement instanceof GrLabeledStatement) {
            l = (GrLabeledStatement)statement;
            continue;
          }

          break;
        }
      }
    }

    if (elementUnderLabel == null) return Collections.emptyMap();

    Map<String, SpockVariableDescriptor> res = new HashMap<>();

    PsiElement e = elementUnderLabel;

    while (e != null) {
      if (e instanceof GrBinaryExpression && ((GrBinaryExpression)e).getOperationTokenType() == GroovyElementTypes.COMPOSITE_LSHIFT_SIGN) {
        GrBinaryExpression shift = (GrBinaryExpression)e;
        GrExpression leftOperand = shift.getLeftOperand();
        GrExpression rightOperand = shift.getRightOperand();

        if (leftOperand instanceof GrReferenceExpression) {
          String name = getNameByReference(leftOperand);
          if (name != null) {
            SpockVariableDescriptor descriptor = new SpockVariableDescriptor(leftOperand, name);
            descriptor.addExpressionOfCollection(rightOperand);
            res.put(name, descriptor);
          }
        }
        else if (leftOperand instanceof GrListOrMap) {
          GrExpression[] variableDefinitions = ((GrListOrMap)leftOperand).getInitializers();

          SpockVariableDescriptor[] variables = createVariables(res, Arrays.asList(variableDefinitions));

          if (rightOperand instanceof GrListOrMap) {
            for (GrExpression expression : ((GrListOrMap)rightOperand).getInitializers()) {
              if (expression instanceof GrListOrMap) {
                add(variables, Arrays.asList(((GrListOrMap)expression).getInitializers()));
              }
              else {
                for (SpockVariableDescriptor variable : variables) {
                  if (variable != null) {
                    variable.addExpressionOfCollection(expression);
                  }
                }
              }
            }
          }
        }
      }
      else if (e instanceof GrAssignmentExpression) {
        GrAssignmentExpression assExpr = (GrAssignmentExpression)e;
        GrExpression lValue = assExpr.getLValue();
        String name = getNameByReference(lValue);
        if (name != null) {
          res.put(name, new SpockVariableDescriptor(lValue, name).addExpression(assExpr.getRValue()));
        }
      }
      else if (isOrStatement(e)) {
        // See org.spockframework.compiler.WhereBlockRewriter#rewriteTableLikeParameterization()
        List<GrExpression> variableDefinitions = new ArrayList<>();
        splitOr(variableDefinitions, (GrExpression)e);

        SpockVariableDescriptor[] variables = createVariables(res, variableDefinitions);

        List<GrExpression> row = new ArrayList<>();

        PsiElement rowElement = getNext(e, elementUnderLabel, elementAfterLabel);
        while (isOrStatement(rowElement)) {
          row.clear();
          splitOr(row, (GrExpression)rowElement);

          add(variables, row);

          rowElement = getNext(rowElement, elementUnderLabel, elementAfterLabel);
        }

        e = rowElement;
        continue;
      }

      e = getNext(e, elementUnderLabel, elementAfterLabel);
    }

    return res;
  }

  private static SpockVariableDescriptor[] createVariables(Map<String, SpockVariableDescriptor> map, List<GrExpression> variableDefinitions) {
    SpockVariableDescriptor[] variables = new SpockVariableDescriptor[variableDefinitions.size()];

    for (int i = 0; i < variableDefinitions.size(); i++) {
      GrExpression expression = variableDefinitions.get(i);
      String name = getNameByReference(expression);
      if (name == null) continue;

      SpockVariableDescriptor variableDescriptor = new SpockVariableDescriptor(expression, name);
      map.put(name, variableDescriptor);
      variables[i] = variableDescriptor;
    }

    return variables;
  }

  private static void add(SpockVariableDescriptor[] variables, List<GrExpression> expressions) {
    for (int i = 0, end = Math.min(variables.length, expressions.size()); i < end; i++) {
      if (variables[i] != null) { // variables[i] can be null.
        variables[i].addExpression(expressions.get(i));
      }
    }
  }

  private static boolean isOrStatement(PsiElement element) {
    if (element instanceof GrBinaryExpression) {
      IElementType type = ((GrBinaryExpression)element).getOperationTokenType();

      return type == GroovyTokenTypes.mBOR || type == GroovyTokenTypes.mLOR;
    }

    return false;
  }

  @Nullable
  private static PsiElement getNext(@NotNull PsiElement current, PsiElement elementUnderLabel, PsiElement elementAfterLabel) {
    PsiElement e = current;

    do {
      if (e == elementUnderLabel) {
        e = elementAfterLabel;
      }
      else {
        e = e.getNextSibling();
      }
    } while (PsiImplUtil.isLeafElementOfType(e, TokenSets.WHITE_SPACES_OR_COMMENTS));

    if (e instanceof GrLabeledStatement) return null;

    return e;
  }

  @Nullable
  public static String getNameByReference(@Nullable PsiElement expression) {
    if (!(expression instanceof GrReferenceExpression)) return null;

    PsiElement firstChild = expression.getFirstChild();
    if (firstChild != expression.getLastChild() || !PsiImplUtil.isLeafElementOfType(firstChild, GroovyTokenTypes.mIDENT)) return null;

    GrReferenceExpression ref = (GrReferenceExpression)expression;
    if (ref.isQualified()) return null;

    return ref.getReferenceName();
  }

  // See org.spockframework.compiler.WhereBlockRewriter#splitRow()
  private static void splitOr(List<GrExpression> res, GrExpression element) {
    if (isOrStatement(element)) {
      GrBinaryExpression be = (GrBinaryExpression)element;
      splitOr(res, be.getLeftOperand());
      splitOr(res, be.getRightOperand());
    }
    else {
      res.add(element);
    }
  }
}
