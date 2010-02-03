/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

/**
 * @author Maxim.Medvedev
 */
public class CreateFieldFromConstructorLabelFix extends CreateFieldFix {
  private final GrNamedArgument myNamedArgument;

  public CreateFieldFromConstructorLabelFix(GrMemberOwner targetClass, GrNamedArgument namedArgument) {
    super(targetClass);
    myNamedArgument = namedArgument;
  }

  @Nullable
  @Override
  protected String getFieldName() {
    final GrArgumentLabel label = myNamedArgument.getLabel();
    assert label != null;
    return label.getName();
  }

  @Override
  protected TypeConstraint[] calculateTypeConstrains() {
    final GrExpression expression = myNamedArgument.getExpression();
    PsiType type = null;
    if (expression != null) {
      type = expression.getType();
    }
    return new TypeConstraint[]{SupertypeConstraint.create(type, type)};
  }

  @Override
  protected String[] generateModifiers() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return super.isAvailable(project, editor, file) && myNamedArgument.isValid();
  }
}
