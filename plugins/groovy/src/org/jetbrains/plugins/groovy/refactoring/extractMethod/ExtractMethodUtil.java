/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMethodOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class ExtractMethodUtil {

  public enum MethodAccessQualifier {
    PUBLIC, PRIVATE, PROTECTED
  }

  @NotNull
  static GrStatement createResultStatement(ExtractMethodInfoHelper helper, @NotNull String methodName) {
    String name = helper.getOutputName();
    PsiType type = helper.getOutputType();
    GrStatement[] statements = helper.getStatements();
    GrMethodCallExpression callExpression = createMethodCallByHelper(methodName, helper);
    if (name == null || type == PsiType.VOID) return callExpression;
    GroovyElementFactory factory = GroovyElementFactory.getInstance(helper.getProject());
    if (mustAddVariableDeclaration(statements, name)) {
      return factory.createVariableDeclaration(new String[0], name, callExpression, type.equalsToText("java.lang.Object") ? null : type);
    } else {
      return factory.createExpressionFromText(name + "= " + callExpression.getText());
    }
  }


  static void removeOldStatements(GrStatementOwner owner, ExtractMethodInfoHelper helper) throws IncorrectOperationException {
    ASTNode parentNode = owner.getNode();
    for (PsiElement element : helper.getInnerElements()) {
      ASTNode node = element.getNode();
      if (node == null || node.getTreeParent() != parentNode) {
        throw new IncorrectOperationException();
      }
      parentNode.removeChild(node);
    }
  }


  /*
  Collect variable types in code block to be extracted
   */
  @Nullable
  static Map<String, PsiType> getVariableTypes(GrStatement[] statements) {
    if (statements.length == 0) return null;
    Map<String, PsiType> map = new HashMap<String, PsiType>();
    for (GrStatement statement : statements) {
      addContainingVariableTypes(statement, map);
    }
    return map;
  }

  /*
  To declare or not a variable to which method call result will be assigned.
   */
  private static boolean mustAddVariableDeclaration(@NotNull GrStatement[] statements, @NotNull String varName) {
    GrVariable ourDeclaration = getVariableDeclaration(statements, varName);
    if (ourDeclaration == null) return true;
    for (GrStatement statement : statements) {
      if (statement instanceof GrVariableDeclaration) {
        GrVariable[] variables = ((GrVariableDeclaration) statement).getVariables();
        for (GrVariable variable : variables) {
          if (ourDeclaration == variable) return true;
        }
      }
    }
    return false;
  }

  static GrMethod createMethodByHelper(@NotNull String name, ExtractMethodInfoHelper helper) {
    //todo change names in statements
    StringBuffer buffer = new StringBuffer();

    //Add signature
    PsiType type = helper.getOutputType();
    final PsiPrimitiveType outUnboxed = PsiPrimitiveType.getUnboxedType(type);
    if (outUnboxed != null) type = outUnboxed;
    String typeText = type.getPresentableText();
    String returnType = typeText.equals("void") || typeText.equals("Object") ? "" : typeText;
    if (returnType.length() == 0) buffer.append("def ");
    buffer.append(returnType);
    buffer.append(" ");
    buffer.append(name);
    buffer.append("(");
    int i = 0;
    ParameterInfo[] infos = helper.getParameterInfos();
    for (ParameterInfo info : infos) {
      PsiType paramType = info.getType();
      final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(paramType);
      if (unboxed != null) paramType = unboxed;
      String paramTypeText = paramType == null || paramType.equalsToText("java.lang.Object") ? "" : paramType.getPresentableText();
      buffer.append(paramTypeText);
      buffer.append(" ");
      buffer.append(info.getName());
      if (i < infos.length - 1) {
        buffer.append(",");
      }
      i++;
    }
    buffer.append(") { \n");

    GroovyElementFactory factory = GroovyElementFactory.getInstance(helper.getProject());
    String outputName = helper.getOutputName();
    if (type != PsiType.VOID && outputName != null && !mustAddVariableDeclaration(helper.getStatements(), outputName)) {
      GrVariableDeclaration decl = factory.createVariableDeclaration(new String[0], outputName, null, type);
      buffer.append(decl.getText()).append("\n");
    }

    if (!ExtractMethodUtil.isSingleExpression(helper.getStatements())) {
      for (PsiElement element : helper.getInnerElements()) {
        buffer.append(element.getText());
      }
      //append return statement
      if (type != PsiType.VOID && outputName != null) {
        buffer.append("\n return ");
        buffer.append(outputName);
      }
    } else {
      GrExpression expr = (GrExpression) helper.getStatements()[0];
      while (expr instanceof GrParenthesizedExpression) {
        expr = ((GrParenthesizedExpression) expr).getOperand();
      }
      buffer.append(PsiType.VOID == type ? "" : "return ").append(expr != null ? expr.getText() : "");
    }

    buffer.append("\n}");

    String methodText = buffer.toString();
    GrMethod method = factory.createMethodFromText(methodText);
    assert method != null;
    return method;
  }

  static GrStatement[] getStatementsByElements(PsiElement[] elements) {
    ArrayList<GrStatement> statementList = new ArrayList<GrStatement>();
    for (PsiElement element : elements) {
      if (element instanceof GrStatement) {
        statementList.add(((GrStatement) element));
      }
    }
    return statementList.toArray(new GrStatement[statementList.size()]);
  }

  static PsiElement[] getElementsInOffset(PsiFile file, int startOffset, int endOffset) {
    PsiElement[] elements;
    GrExpression expr = GroovyRefactoringUtil.findElementInRange(((GroovyFileBase) file), startOffset, endOffset, GrExpression.class);

    if (expr != null) {
      PsiElement parent = expr.getParent();
      if (expr.getParent() instanceof GrMethodCallExpression || parent instanceof GrIndexProperty) {
        expr = ((GrExpression) expr.getParent());
      }
      elements = new PsiElement[]{expr};
    } else {
      elements = GroovyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset);
    }
    return elements;
  }

  @Nullable
  static GrMethodOwner getMethodOwner(GrStatement statement) {
    PsiElement parent = statement.getParent();
    while (parent != null && !(parent instanceof GrMethodOwner) && !(parent instanceof PsiFile)) {
      parent = parent.getParent();
    }
    return parent instanceof GrMethodOwner ? ((GrMethodOwner) parent) : null;
  }

  @Nullable
  static GrVariableDeclarationOwner getDecalarationOwner(GrStatement statement) {
    PsiElement parent = statement.getParent();
    return parent instanceof GrVariableDeclarationOwner ? ((GrVariableDeclarationOwner) parent) : null;
  }

  static boolean isSingleExpression(GrStatement[] statements) {
    return statements.length == 1 && statements[0] instanceof GrExpression &&
        !(statements[0].getParent() instanceof GrVariableDeclarationOwner && statements[0] instanceof GrAssignmentExpression);
  }

  // Get declaration of variable to which method call expression will be assigned
  private static GrVariable getVariableDeclaration(@NotNull GrStatement[] statements, @NotNull String varName) {
    GrVariable variable = null;
    for (GrStatement statement : statements) {
      variable = getInnerVariableDeclaration(statement, varName);
      if (variable != null) break;
    }
    return variable;
  }

  private static GrVariable getInnerVariableDeclaration(@NotNull PsiElement element, @NotNull String name) {
    if (element instanceof GrReferenceExpression) {
      GrReferenceExpression expr = (GrReferenceExpression) element;
      if (name.equals(expr.getName())) {
        PsiReference ref = expr.getReference();
        if (ref != null) {
          PsiElement resolved = ref.resolve();
          if (resolved instanceof GrVariable && !(resolved instanceof GrField)) {
            return ((GrVariable) resolved);
          }
        }
        return null;
      } else {
        return null;
      }
    }
    for (PsiElement child : element.getChildren()) {
      GrVariable res = getInnerVariableDeclaration(child, name);
      if (res != null) return res;
    }
    return null;
  }

  private static void addContainingVariableTypes(PsiElement statement, Map<String, PsiType> map) {
    if (statement instanceof GrVariable) {
      GrVariable variable = (GrVariable) statement;
      String name = variable.getName();
      if (name != null) {
        map.put(name, variable.getTypeGroovy());
      }
    }
    if (statement instanceof GrReferenceExpression) {
      GrReferenceExpression expr = (GrReferenceExpression) statement;
      String name = expr.getName();
      PsiReference ref = expr.getReference();
      if (name != null && (ref == null || !(ref.resolve() instanceof GrField))) {
        map.put(name, expr.getType());
      }
    }
    for (PsiElement element : statement.getChildren()) {
      addContainingVariableTypes(element, map);
    }

  }

  static GrMethodCallExpression createMethodCallByHelper(@NotNull String name, ExtractMethodInfoHelper helper) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(name).append("(");
    int i = 0;
    String[] argumentNames = helper.getArgumentNames();
    for (String argName : argumentNames) {
      buffer.append(argName);
      if (i < argumentNames.length - 1) {
        buffer.append(",");
      }
      i++;
    }

    buffer.append(")");
    String callText = buffer.toString();
    GroovyElementFactory factory = GroovyElementFactory.getInstance(helper.getProject());
    GrExpression expr = factory.createExpressionFromText(callText);
    assert expr instanceof GrMethodCallExpression;
    return ((GrMethodCallExpression) expr);
  }

  /*
  Some cosmetics
   */
  static void removeNewLineAfter(@NotNull GrStatement statement) {
    ASTNode parentNode = statement.getParent().getNode();
    ASTNode next = statement.getNode().getTreeNext();
    if (parentNode != null && next != null && GroovyTokenTypes.mNLS == next.getElementType()) {
      parentNode.removeChild(next);
    }
  }

  static int getCaretOffset(@NotNull GrStatement statement) {
    if (statement instanceof GrVariableDeclaration) {
      GrVariable[] variables = ((GrVariableDeclaration) statement).getVariables();
      if (variables.length > 0) {
        GrExpression initializer = variables[0].getInitializerGroovy();
        if (initializer != null) {
          return initializer.getTextOffset();
        }
      }
    } else if (statement instanceof GrAssignmentExpression) {
      GrExpression value = ((GrAssignmentExpression) statement).getRValue();
      if (value != null) {
        return value.getTextOffset();
      }
    } else if (statement instanceof GrMethodCallExpression) {
      return statement.getTextOffset();
    }
    return statement.getTextOffset();
  }


}
