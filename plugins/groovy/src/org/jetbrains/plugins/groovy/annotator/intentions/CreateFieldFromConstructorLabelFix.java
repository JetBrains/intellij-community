// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

import static com.intellij.psi.util.PointersKt.createSmartPointer;

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

  @Nullable
  private String getFieldName() {
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

  @NotNull
  @Override
  public String getName() {
    return GroovyBundle.message("create.field.from.usage", getFieldName());
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Create field";
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
    GrNamedArgument namedArgument = myNamedArgumentPointer.getElement();
    if (namedArgument == null) return;
    String fieldName = getFieldName();
    if (fieldName == null) return;
    myFix.doFix(project, ArrayUtil.EMPTY_STRING_ARRAY, fieldName, calculateTypeConstrains(namedArgument), namedArgument);
  }
}
