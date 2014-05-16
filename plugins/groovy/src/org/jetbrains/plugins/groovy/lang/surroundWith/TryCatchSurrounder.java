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
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
public class TryCatchSurrounder extends TrySurrounder {
  @Override
  public String getTemplateDescription() {
    return super.getTemplateDescription() + " / catch";
  }

  @Override
  protected GroovyPsiElement doSurroundElements(PsiElement[] elements, PsiElement context) throws IncorrectOperationException {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(elements[0].getProject());
    GrTryCatchStatement tryStatement = (GrTryCatchStatement) factory.createStatementFromText("try {\n} catch(exception){\n}", context);
    addStatements(tryStatement.getTryBlock(), elements);
    return tryStatement;
  }
}
