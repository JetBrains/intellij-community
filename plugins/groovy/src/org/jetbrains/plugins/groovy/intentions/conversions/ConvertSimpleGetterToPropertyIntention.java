/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Max Medvedev
 */
public class ConvertSimpleGetterToPropertyIntention extends Intention {
  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    GrMethod method = (GrMethod)element.getParent();

    GrStatement statement = method.getBlock().getStatements()[0];

    GrExpression value;
    if (statement instanceof GrReturnStatement) {
      value = ((GrReturnStatement)statement).getReturnValue();
    }
    else {
      value = (GrExpression)statement;
    }

    String fieldName = GroovyPropertyUtils.getPropertyNameByGetter(method);

    String[] modifiers;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      modifiers = new String[]{PsiModifier.STATIC, PsiModifier.FINAL, };
    }
    else {
      modifiers = new String[]{PsiModifier.FINAL};
    }
    GrVariableDeclaration declaration = GroovyPsiElementFactory.getInstance(project)
      .createFieldDeclaration(modifiers, fieldName, value, method.getReturnType());

    PsiClass aClass = method.getContainingClass();
    PsiElement replaced = aClass.addBefore(declaration, method);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);

    method.delete();
  }


  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        PsiElement parent = element.getParent();
        if (!(parent instanceof GrMethod) || ((GrMethod)parent).getNameIdentifierGroovy() != element) return false;

        GrMethod method = (GrMethod)parent;

        GrOpenBlock block = method.getBlock();
        if (block == null) return false;

        GrStatement[] statements = block.getStatements();
        if (statements.length != 1) return false;

        if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) return false;
        if (GroovyPropertyUtils.findFieldForAccessor(method, true) != null) return false;


        GrStatement statement = statements[0];
        if (!(statement instanceof GrReturnStatement && ((GrReturnStatement)statement).getReturnValue() != null ||
              statement instanceof GrExpression && !PsiType.VOID.equals(((GrExpression)statement).getType()))) {
          return false;
        }
        return true;
      }
    };
  }
}
