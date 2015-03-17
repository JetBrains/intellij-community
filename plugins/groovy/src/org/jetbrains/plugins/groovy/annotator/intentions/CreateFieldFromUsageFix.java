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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.util.ArrayUtil;
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
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private TypeConstraint[] calculateTypeConstrains() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints(getRefExpr());
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
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return super.canBeTargetClass(psiClass) && !psiClass.isInterface() && !psiClass.isAnnotationType();
  }
}
