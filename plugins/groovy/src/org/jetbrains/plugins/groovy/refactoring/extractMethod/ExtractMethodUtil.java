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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMethodOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/**
 * @author ilyas
 */
public class ExtractMethodUtil {

  @Nullable
  public static Map<String, PsiType> getVariableTypes(GrStatement[] statements) {

    if (statements.length == 0) return null;
    Map<String, PsiType> map = new HashMap<String, PsiType>();
    for (GrStatement statement : statements) {
      addContainingVariableTypes(statement, map);
    }

    return map;

  }

  private static void addContainingVariableTypes(PsiElement statement, Map<String, PsiType> map) {
    for (PsiElement element : statement.getChildren()) {
      if (element instanceof GrVariable) {
        GrVariable variable = (GrVariable) element;
        String name = variable.getName();
        if (name != null) {
          map.put(name, variable.getTypeGroovy());
        }
      }
      if (element instanceof GrReferenceExpression) {
        GrReferenceExpression expr = (GrReferenceExpression) element;
        String name = expr.getName();
        if (name != null) {
          map.put(name, expr.getType());
        }
      }
      addContainingVariableTypes(element, map);
    }
  }

  public static GrMethod createMethodByHelper(String name, ExtractMethodInfoHelper helper) {
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

    for (PsiElement element : helper.getInnerElements()) {
      buffer.append(element.getText());
    }

    //append return statement
    String outputName = helper.getOutputName();
    if (type != PsiType.VOID && outputName != null) {
      buffer.append("\n return ");
      buffer.append(outputName);
    }
    buffer.append("\n}");

    String methodText = buffer.toString();
    GroovyElementFactory factory = GroovyElementFactory.getInstance(helper.getProject());
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

  public enum MethodAccessQualifier {
    PUBLIC, PRIVATE, PROTECTED
  }

}
