/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class DataPointHolderConversionIntention extends PsiElementBaseIntentionAction {
  private static final String THEORIES_PACKAGE = "org.junit.experimental.theories";
  private static final String DATA_POINT_FQN = THEORIES_PACKAGE + ".DataPoint";
  private static final String DATA_POINTS_FQN = THEORIES_PACKAGE + ".DataPoints";

  private static final String REPLACE_BY_TEMPLATE = "Replace by @%s %s";

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    final PsiElement holder = element.getParent();
    PsiModifierListOwner createdElement =
      holder instanceof PsiField ? convertToMethod((PsiField)holder) : convertToField((PsiMethod)holder);

    final PsiModifierListOwner oldElement = (PsiModifierListOwner)holder;
    final PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotation(oldElement, DATA_POINT_FQN, DATA_POINTS_FQN);
    assert psiAnnotation != null;
    final String annotation = psiAnnotation.getQualifiedName();
    assert annotation != null;
    final PsiModifierList modifierList = createdElement.getModifierList();
    assert modifierList != null;
    modifierList.addAnnotation(annotation);
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
    modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
    createdElement = (PsiModifierListOwner)oldElement.replace(createdElement);

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    final TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(createdElement);
    final PsiNameIdentifierOwner asNameIdOwner = (PsiNameIdentifierOwner)createdElement;
    templateBuilder.replaceElement(asNameIdOwner.getNameIdentifier(), asNameIdOwner.getName());
    templateBuilder.run(editor, false);
  }

  private static PsiField convertToField(final PsiMethod method) {
    final Project project = method.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    final String fieldName = codeStyleManager.propertyNameToVariableName(method.getName(), VariableKind.STATIC_FIELD);
    final PsiType returnType = method.getReturnType();
    assert returnType != null;
    final PsiField field = elementFactory.createField(fieldName, returnType);
    final PsiStatement returnStatement = PsiTreeUtil.findChildOfType(method, PsiStatement.class);
    if (returnStatement != null) {
      field.setInitializer(((PsiReturnStatement)returnStatement).getReturnValue());
    }
    return field;
  }

  private static PsiMethod convertToMethod(final PsiField field) {
    final Project project = field.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    final PsiExpression fieldInitializer = field.getInitializer();

    final PsiMethod method =
      elementFactory.createMethod(codeStyleManager.variableNameToPropertyName(field.getName(), VariableKind.STATIC_FIELD), field.getType());
    PsiCodeBlock body = method.getBody();
    assert body != null;

    final PsiStatement methodCode =
      elementFactory.createStatementFromText(PsiKeyword.RETURN + " " + fieldInitializer.getText() + ";", null);
    body.add(methodCode);
    return method;
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    final Pair<PsiMember, PsiAnnotation> dataPointsHolder = extractDataPointsHolder(element);
    if (dataPointsHolder != null && isConvertible(dataPointsHolder.getFirst())) {
      final String replaceType = dataPointsHolder.getFirst() instanceof PsiMethod ? "field" : "method";
      final String annotation = StringUtil.getShortName(dataPointsHolder.getSecond().getQualifiedName());
      setText(String.format(REPLACE_BY_TEMPLATE, annotation, replaceType));
      return true;
    }
    return false;
  }

  private static Pair<PsiMember, PsiAnnotation> extractDataPointsHolder(@NotNull final PsiElement element) {
    if (!(element instanceof PsiIdentifier)) {
      return null;
    }
    final PsiElement maybeHolder = element.getParent();
    if (!(maybeHolder instanceof PsiMethod || maybeHolder instanceof PsiField)) {
      return null;
    }
    final PsiMember holder = (PsiMember)maybeHolder;
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(holder, DATA_POINT_FQN, DATA_POINTS_FQN);
    return annotation == null ? null : Pair.create(holder, annotation);
  }

  private static boolean isConvertible(@NotNull final PsiMember member) {
    if (!(member instanceof PsiMethod)) {
      return ((PsiField)member).getInitializer() != null;
    }
    final PsiMethod method = (PsiMethod)member;
    final PsiType returnType = method.getReturnType();
    if (returnType == null || returnType.equals(PsiType.VOID) || method.getParameterList().getParametersCount() != 0) {
      return false;
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    final PsiStatement[] methodStatements = body.getStatements();
    switch (methodStatements.length) {
      case 1:
        final PsiStatement methodStatement = methodStatements[0];
        return methodStatement instanceof PsiReturnStatement;
      case 0:
        return true;
      default:
        return false;
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Convert @DataPoint(s) annotation holder";
  }
}
