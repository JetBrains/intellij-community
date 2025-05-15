// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;

import java.util.List;

public class GrCreateFieldFromUsageFix extends GrCreateFromUsageBaseFix {

  private final @NotNull String myReferenceName;

  public GrCreateFieldFromUsageFix(GrReferenceExpression refExpression, @NotNull String referenceName) {
    super(refExpression);
    myReferenceName = referenceName;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    final List<PsiClass> classes = getTargetClasses();
    if (classes.isEmpty()) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiClass targetClass = classes.get(0);
    final CreateFieldFix fix = new CreateFieldFix(targetClass);
    PsiField representation =
      fix.getFieldRepresentation(targetClass.getProject(), generateModifiers(targetClass), myReferenceName, getRefExpr(), true);
    PsiElement parent = representation == null ? null : representation.getParent();
    if (parent == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, "", parent.getText());
  }

  private String[] generateModifiers(@NotNull PsiClass targetClass) {
    final GrReferenceExpression myRefExpression = getRefExpr();
    if (myRefExpression != null && GrStaticChecker.isInStaticContext(myRefExpression, targetClass)) {
      return new String[]{PsiModifier.STATIC};
    }
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  private TypeConstraint[] calculateTypeConstrains() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints(getRefExpr());
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("create.field.from.usage.family.name");
  }

  @Override
  public @NotNull String getText() {
    return GroovyBundle.message("create.field.from.usage", myReferenceName);
  }

  @Override
  protected void invokeImpl(Project project, @NotNull PsiClass targetClass) {
    final CreateFieldFix fix = new CreateFieldFix(targetClass);
    fix.doFix(targetClass.getProject(), generateModifiers(targetClass), myReferenceName, calculateTypeConstrains(), getRefExpr());
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return super.canBeTargetClass(psiClass) && !psiClass.isInterface() && !psiClass.isAnnotationType();
  }
}
