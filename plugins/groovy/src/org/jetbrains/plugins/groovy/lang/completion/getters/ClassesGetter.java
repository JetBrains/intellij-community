/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;

/**
 * @author ven
 */
public class ClassesGetter implements ContextGetter {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.completion.getters.ClassesGetter");

  public Object[] get(PsiElement context, CompletionContext completionContext) {
    if (context != null) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
      GroovyFile file = factory.createGroovyFile("def XXX xxx", false, context);
      GrClassTypeElement typeElement = (GrClassTypeElement) ((GrVariableDeclaration) file.getTopStatements()[0]).getVariables()[0].getTypeElementGroovy();
      LOG.assertTrue(typeElement != null);
      return typeElement.getReferenceElement().getVariants();
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}
