/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.resources;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IOResourceInspectionBase extends ResourceInspection {
  protected static final String[] IO_TYPES = {
    "java.io.InputStream", "java.io.OutputStream",
    "java.io.Reader", "java.io.Writer",
    "java.io.RandomAccessFile", "java.util.zip.ZipFile"};
  final List<String> ignoredTypes = new ArrayList();

  public IOResourceInspectionBase() {
    parseString(ignoredTypesString, ignoredTypes);
  }

  @NonNls
  @SuppressWarnings({"PublicField"})
  public String ignoredTypesString = "java.io.ByteArrayOutputStream" +
                                     ',' + "java.io.ByteArrayInputStream" +
                                     ',' + "java.io.StringBufferInputStream" +
                                     ',' + "java.io.CharArrayWriter" +
                                     ',' + "java.io.CharArrayReader" +
                                     ',' + "java.io.StringWriter" +
                                     ',' + "java.io.StringReader";
  @SuppressWarnings({"PublicField"})
  public boolean insideTryAllowed = false;

  public static boolean isIOResourceFactoryMethodCall(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"getResourceAsStream".equals(methodName)) {
      return false;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    return qualifier != null &&
           TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_LANG_CLASS, "java.lang.ClassLoader") != null;
  }

  @Override
  @NotNull
  public String getID() {
    return "IOResourceOpenedButNotSafelyClosed";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "i.o.resource.opened.not.closed.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final PsiType type = expression.getType();
    assert type != null;
    final String text = type.getPresentableText();
    return InspectionGadgetsBundle.message(
      "resource.opened.not.closed.problem.descriptor", text);
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(ignoredTypesString, ignoredTypes);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    ignoredTypesString = formatString(ignoredTypes);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IOResourceVisitor();
  }

  public boolean isIOResource(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, IO_TYPES) != null && !isIgnoredType(expression);
  }

  private boolean isIgnoredType(PsiExpression expression) {
    return TypeUtils.expressionHasTypeOrSubtype(expression, ignoredTypes);
  }

  private boolean isArgumentOfResourceCreation(
    PsiVariable boundVariable, PsiElement scope) {
    final UsedAsIOResourceArgumentVisitor visitor =
      new UsedAsIOResourceArgumentVisitor(boundVariable);
    scope.accept(visitor);
    return visitor.isUsedAsArgumentToResourceCreation();
  }

  private class IOResourceVisitor extends BaseInspectionVisitor {

    IOResourceVisitor() {
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (!isIOResourceFactoryMethodCall(expression)) {
        return;
      }
      checkExpression(expression);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isIOResource(expression)) {
        return;
      }
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      final PsiElement parent = getExpressionParent(expression);
      if (parent instanceof PsiReturnStatement || parent instanceof PsiResourceVariable) {
        return;
      }
      if (parent instanceof PsiExpressionList) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiAnonymousClass) {
          grandParent = grandParent.getParent();
        }
        if (grandParent instanceof PsiNewExpression && isIOResource((PsiNewExpression)grandParent)) {
          return;
        }
      }
      final PsiVariable boundVariable = getVariable(parent);
      final PsiElement containingBlock = PsiTreeUtil.getParentOfType(expression, PsiCodeBlock.class);
      if (containingBlock == null) {
        return;
      }
      if (isArgumentOfResourceCreation(boundVariable, containingBlock)) {
        return;
      }
      if (isSafelyClosed(boundVariable, expression, insideTryAllowed)) {
        return;
      }
      if (isResourceEscapedFromMethod(boundVariable, expression)) {
        return;
      }
      registerError(expression, expression);
    }
  }

  private class UsedAsIOResourceArgumentVisitor
    extends JavaRecursiveElementVisitor {

    private boolean usedAsArgToResourceCreation = false;
    private final PsiVariable ioResource;

    UsedAsIOResourceArgumentVisitor(PsiVariable ioResource) {
      this.ioResource = ioResource;
    }

    @Override
    public void visitNewExpression(
      @NotNull PsiNewExpression expression) {
      if (usedAsArgToResourceCreation) {
        return;
      }
      super.visitNewExpression(expression);
      if (!isIOResource(expression)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      final PsiExpression argument = arguments[0];
      if (!(argument instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReference reference = (PsiReference)argument;
      final PsiElement target = reference.resolve();
      if (target == null || !target.equals(ioResource)) {
        return;
      }
      usedAsArgToResourceCreation = true;
    }

    public boolean isUsedAsArgumentToResourceCreation() {
      return usedAsArgToResourceCreation;
    }
  }
}
