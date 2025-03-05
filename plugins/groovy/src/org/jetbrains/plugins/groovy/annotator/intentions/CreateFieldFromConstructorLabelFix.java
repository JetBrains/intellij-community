// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

import static com.intellij.psi.SmartPointersKt.createSmartPointer;

/**
 * @author Maxim.Medvedev
 */
public class CreateFieldFromConstructorLabelFix extends GroovyFix {
  private final CreateFieldFix myFix;
  private final SmartPsiElementPointer<GrNamedArgument> myNamedArgumentPointer;

  public CreateFieldFromConstructorLabelFix(@NotNull GrTypeDefinition targetClass, @NotNull GrNamedArgument namedArgument) {
    myFix = new CreateFieldFix(targetClass);
    myNamedArgumentPointer = createSmartPointer(namedArgument);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiClass targetClass = myFix.getTargetClass();
    final CreateFieldFix fix = new CreateFieldFix(targetClass);
    String name = getFieldName();
    if (name == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    PsiField representation =
      fix.getFieldRepresentation(targetClass.getProject(), ArrayUtilRt.EMPTY_STRING_ARRAY, name, targetClass, true);
    PsiElement parent = representation == null ? null : representation.getParent();
    if (parent == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    String className = targetClass.getName();
    String classKind;
    if (targetClass.isInterface()) {
      classKind = "interface";
    } else if (targetClass.isEnum()) {
     classKind = "enum";
    } else if (targetClass.isAnnotationType()) {
      classKind = "@interface";
    } else if (targetClass.isRecord()) {
      classKind = "record";
    } else if (targetClass instanceof GrTypeDefinition && ((GrTypeDefinition)targetClass).isTrait()) {
      classKind = "trait";
    } else {
      classKind = "class";
    }
    return new IntentionPreviewInfo.CustomDiff(GroovyFileType.GROOVY_FILE_TYPE, classKind + " " + className, "", parent.getText());
  }

  private @Nullable String getFieldName() {
    GrNamedArgument namedArgument = myNamedArgumentPointer.getElement();
    if (namedArgument == null) return null;
    final GrArgumentLabel label = namedArgument.getLabel();
    assert label != null;
    return label.getName();
  }

  private static TypeConstraint[] calculateTypeConstrains(@NotNull GrNamedArgument namedArgument) {
    final GrExpression expression = namedArgument.getExpression();
    PsiType type = null;
    if (expression != null) {
      type = expression.getType();
    }
    if (type != null) {
      return new TypeConstraint[]{SupertypeConstraint.create(type, type)};
    }
    else {
      return TypeConstraint.EMPTY_ARRAY;
    }
  }

  @Override
  public @NotNull String getName() {
    return GroovyBundle.message("create.field.from.usage", getFieldName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return GroovyBundle.message("intention.family.name.create.field");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    GrNamedArgument namedArgument = myNamedArgumentPointer.getElement();
    if (namedArgument == null) return;
    String fieldName = getFieldName();
    if (fieldName == null) return;
    myFix.doFix(project, ArrayUtilRt.EMPTY_STRING_ARRAY, fieldName, calculateTypeConstrains(namedArgument), namedArgument);
  }
}
