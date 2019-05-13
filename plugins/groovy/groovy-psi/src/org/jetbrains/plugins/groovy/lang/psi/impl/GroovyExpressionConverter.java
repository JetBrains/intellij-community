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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ExpressionConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

/**
 * @author Maxim.Medvedev
 */
public class GroovyExpressionConverter extends ExpressionConverter {
  @Override
  protected PsiElement convert(PsiElement expression, Project project) {
    return GroovyPsiElementFactory.getInstance(project).createExpressionFromText(expression.getText(), expression);
  }
}
