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
package org.jetbrains.plugins.groovy.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;

public class GroovyWhileUnwrapper extends GroovyUnwrapper {
  public GroovyWhileUnwrapper() {
    super(CodeInsightBundle.message("unwrap.while"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof GrWhileStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    GrStatement body = ((GrWhileStatement)element).getBody();

    context.extractFromBlockOrSingleStatement(body, element);
    context.delete(element);
  }
}