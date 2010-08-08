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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Maxim.Medvedev
 */
public class GroovyClassNameInsertHandler implements AllClassesGetter.ClassNameInsertHandler {
  public AllClassesGetter.ClassNameInsertHandlerResult handleInsert(InsertionContext context, JavaPsiClassReferenceElement item) {
    PsiFile file = context.getFile();
    assert GroovyFileType.GROOVY_LANGUAGE.equals(file.getLanguage());
    Editor editor = context.getEditor();
    int endOffset = editor.getCaretModel().getOffset();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, endOffset - 1, GrImportStatement.class, false) != null) {
      return AllClassesGetter.ClassNameInsertHandlerResult.INSERT_FQN;
    }
    PsiElement position = file.findElementAt(endOffset - 1);

    if (position != null && GroovyCompletionContributor.isReferenceInNewExpression(position.getParent())) {
      PsiMethodInsertHandler.insertParentheses(context, item, false, GroovyCompletionUtil.hasConstructorParameters(item.getObject()));
    }

    if (isInVariable(position)) {
      Project project = context.getProject();
      PsiClass psiClass = item.getObject();
      if (!psiClass.isValid()) return AllClassesGetter.ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE;
      String qname = psiClass.getQualifiedName();
      String shortName = psiClass.getName();
      if (qname == null) return AllClassesGetter.ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE;

      PsiClass aClass = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedClass(shortName, position);
      if (aClass == null) {
        ((GroovyFileBase)file).addImportForClass(psiClass);
        new GroovyInsertHandler().handleInsert(context, item);
        return AllClassesGetter.ClassNameInsertHandlerResult.REFERENCE_CORRECTED;
      }
      else if (aClass == psiClass) {
        new GroovyInsertHandler().handleInsert(context, item);
        return AllClassesGetter.ClassNameInsertHandlerResult.REFERENCE_CORRECTED;
      }
    }
    return AllClassesGetter.ClassNameInsertHandlerResult.CHECK_FOR_CORRECT_REFERENCE;
  }

  private static boolean isInVariable(PsiElement position) {
    GrVariable variable = PsiTreeUtil.getParentOfType(position, GrVariable.class);
    return variable != null && variable.getTypeElementGroovy() == null && position == variable.getNameIdentifierGroovy();
  }
}
