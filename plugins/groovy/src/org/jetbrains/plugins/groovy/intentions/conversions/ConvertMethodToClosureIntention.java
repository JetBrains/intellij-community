/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;

import static com.intellij.openapi.util.text.StringUtil.isJavaIdentifier;


/**
 * @author Maxim.Medvedev
 */
public class ConvertMethodToClosureIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.conversions.ConvertMethodToclosureIntention");

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final GrMethod method;
    if (element.getParent() instanceof GrMethod) {
      method = (GrMethod)element.getParent();
    }
    else {
      final PsiReference ref = element.getReference();
      LOG.assertTrue(ref != null);
      final PsiElement resolved = ref.resolve();
      LOG.assertTrue(resolved instanceof GrMethod);
      method = (GrMethod)resolved;
    }

    final PsiClass containingClass = method.getContainingClass();
    final String methodName = method.getName();
    final PsiField field = containingClass.findFieldByName(methodName, true);

    if (field != null) {
      conflicts.putValue(field, GroovyIntentionsBundle.message("field.already.exists", methodName));
    }

    final Collection<PsiReference> references = MethodReferencesSearch.search(method).findAll();
    final Collection<GrReferenceExpression> usagesToConvert = new HashSet<>(references.size());
    for (PsiReference ref : references) {
      final PsiElement psiElement = ref.getElement();
      if (!GroovyLanguage.INSTANCE.equals(psiElement.getLanguage())) {
        conflicts.putValue(psiElement, GroovyIntentionsBundle.message("method.is.used.outside.of.groovy"));
      }
      else if (!PsiUtil.isMethodUsage(psiElement)) {
        if (psiElement instanceof GrReferenceExpression) {
          if (((GrReferenceExpression)psiElement).hasMemberPointer()) {
            usagesToConvert.add((GrReferenceExpression)psiElement);
          }
        }
      }
    }
    if (!conflicts.isEmpty()) {
      ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts, () -> execute(method, usagesToConvert));
      conflictsDialog.show();
      if (conflictsDialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
    }
    execute(method, usagesToConvert);
  }

  private static void execute(final GrMethod method, final Collection<GrReferenceExpression> usagesToConvert) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

      StringBuilder builder = new StringBuilder(method.getTextLength());
      String modifiers = method.getModifierList().getText();
      if (modifiers.trim().isEmpty()) {
        modifiers = GrModifier.DEF;
      }
      builder.append(modifiers).append(' ');
      builder.append(method.getName()).append("={");
      builder.append(method.getParameterList().getText()).append(" ->");
      final GrOpenBlock block = method.getBlock();
      builder.append(block.getText().substring(1));
      final GrVariableDeclaration variableDeclaration =
        GroovyPsiElementFactory.getInstance(method.getProject()).createFieldDeclarationFromText(builder.toString());
      method.replace(variableDeclaration);

      for (GrReferenceExpression element : usagesToConvert) {
        final PsiElement qualifier = element.getQualifier();
        final StringBuilder text = new StringBuilder(qualifier.getText());
        element.setQualifier(null);
        text.append('.').append(element.getText());
        element.replace(factory.createExpressionFromText(text.toString()));
      }
    });
  }

  private static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      if (element.getLanguage() != GroovyLanguage.INSTANCE) return false;

      GrMethod method;
      final PsiReference ref = element.getReference();
      if (ref != null) {
        final PsiElement resolved = ref.resolve();
        if (!(resolved instanceof GrMethod)) return false;
        method = (GrMethod)resolved;
      }
      else {
        final PsiElement parent = element.getParent();
        if (!(parent instanceof GrMethod)) return false;
        if (((GrMethod)parent).getNameIdentifierGroovy() != element) return false;
        method = (GrMethod)parent;
      }
      return !method.isConstructor() &&
             isJavaIdentifier(method.getName()) &&
             method.getBlock() != null &&
             method.getParent() instanceof GrTypeDefinitionBody;
    }
  }
}


