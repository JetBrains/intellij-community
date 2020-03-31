// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStaticChecker;

/**
 * @author ven
 */
public class CreateFieldFromUsageFix extends GrCreateFromUsageBaseFix {

  private final @NotNull String myReferenceName;

  public CreateFieldFromUsageFix(GrReferenceExpression refExpression, @NotNull String referenceName) {
    super(refExpression);
    myReferenceName = referenceName;
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

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("create.field.from.usage.family.name");
  }

  @Override
  @NotNull
  public String getText() {
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
