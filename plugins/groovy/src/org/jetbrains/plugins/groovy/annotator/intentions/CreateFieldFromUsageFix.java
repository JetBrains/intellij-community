/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.StaticChecker;

/**
 * @author ven
 */
public class CreateFieldFromUsageFix implements IntentionAction {
  private final CreateFieldFix myFix;
  private final GrReferenceExpression myRefExpression;

  public CreateFieldFromUsageFix(GrReferenceExpression refExpression, PsiClass targetClass) {
    myFix = new CreateFieldFix(targetClass);
    myRefExpression = refExpression;
  }

  @NotNull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myFix.isAvailable() && myRefExpression.isValid();
  }

  @Nullable
  private String getFieldName() {
    return myRefExpression.getReferenceName();
  }

  private String[] generateModifiers() {
    if (myRefExpression != null && StaticChecker.isInStaticContext(myRefExpression, myFix.getTargetClass())) {
      return new String[]{PsiModifier.STATIC};
    }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private TypeConstraint[] calculateTypeConstrains() {
    return GroovyExpectedTypesProvider.calculateTypeConstraints(myRefExpression);
  }

  @NotNull
  public String getText() {
    return GroovyBundle.message("create.field.from.usage", getFieldName());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myFix.doFix(project, generateModifiers(), getFieldName(), calculateTypeConstrains(), myRefExpression);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
