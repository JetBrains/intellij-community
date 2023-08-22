// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.SignaturesKt;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ConvertClosureToMethodIntention extends Intention {
  private static final Logger LOG =
    Logger.getInstance(ConvertClosureToMethodIntention.class);

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull Project project, Editor editor) throws IncorrectOperationException {
    final PsiElement parent = element.getParent();
    if (!(parent instanceof GrField field)) return;

    final HashSet<PsiReference> usages = new HashSet<>();
    usages.addAll(ReferencesSearch.search(field).findAll());
    final GrAccessorMethod[] getters = field.getGetters();
    for (GrAccessorMethod getter : getters) {
      usages.addAll(MethodReferencesSearch.search(getter).findAll());
    }
    final GrAccessorMethod setter = field.getSetter();
    if (setter != null) {
      usages.addAll(MethodReferencesSearch.search(setter).findAll());
    }

    final String fieldName = field.getName();
    final Collection<PsiElement> fieldUsages = new HashSet<>();
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    for (PsiReference usage : usages) {
      final PsiElement psiElement = usage.getElement();
      if (PsiUtil.isMethodUsage(psiElement)) continue;
      if (!GroovyLanguage.INSTANCE.equals(psiElement.getLanguage())) {
        conflicts.putValue(psiElement, GroovyBundle.message("closure.is.accessed.outside.of.groovy", fieldName));
      }
      else {
        if (psiElement instanceof GrReferenceExpression) {
          fieldUsages.add(psiElement);
          if (PsiUtil.isAccessedForWriting((GrExpression)psiElement)) {
            conflicts.putValue(psiElement, GroovyBundle.message("write.access.to.closure.variable", fieldName));
          }
        }
        else if (psiElement instanceof GrArgumentLabel) {
          conflicts.putValue(psiElement, GroovyBundle.message("field.is.used.in.argument.label", fieldName));
        }
      }
    }
    final PsiClass containingClass = field.getContainingClass();
    final GrExpression initializer = field.getInitializerGroovy();
    LOG.assertTrue(initializer != null);
    final PsiType type = initializer.getType();
    LOG.assertTrue(type instanceof GroovyClosureType);
    final List<MethodSignature> signatures = SignaturesKt.generateAllMethodSignaturesBySignature(
      fieldName, ((GroovyClosureType)type).getSignatures()
    );
    for (MethodSignature s : signatures) {
      final PsiMethod method = MethodSignatureUtil.findMethodBySignature(containingClass, s, true);
      if (method != null) {
        conflicts.putValue(method, GroovyBundle.message("method.with.signature.already.exists",
                                                                  GroovyPresentationUtil.getSignaturePresentation(s)));
      }
    }
    if (!conflicts.isEmpty()) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts, () -> execute(field, fieldUsages));
      conflictsDialog.show();
      if (conflictsDialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) return;
    }
    execute(field, fieldUsages);
  }

  private static void execute(final GrField field, final Collection<PsiElement> fieldUsages) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(field.getProject());

    final StringBuilder builder = new StringBuilder(field.getTextLength());
    final GrClosableBlock block = (GrClosableBlock)field.getInitializerGroovy();

    final GrModifierList modifierList = field.getModifierList();
    if (modifierList.getModifiers().length > 0 || modifierList.getAnnotations().length > 0) {
      builder.append(modifierList.getText());
    }
    else {
      builder.append(GrModifier.DEF);
    }
    builder.append(' ').append(field.getName());

    builder.append('(');
    if (block.hasParametersSection()) {
      builder.append(block.getParameterList().getText());
    }
    else {
      builder.append("def it = null");
    }
    builder.append(") {");


    ApplicationManager.getApplication().runWriteAction(() -> {
      block.getParameterList().delete();
      block.getLBrace().delete();
      final PsiElement psiElement = PsiUtil.skipWhitespacesAndComments(block.getFirstChild(), true);
      if (psiElement != null && "->".equals(psiElement.getText())) {
        psiElement.delete();
      }
      builder.append(block.getText());
      final GrMethod method = GroovyPsiElementFactory.getInstance(field.getProject()).createMethodFromText(builder.toString());
      field.getParent().replace(method);
      for (PsiElement usage : fieldUsages) {
        if (usage instanceof GrReferenceExpression) {
          final PsiElement parent = usage.getParent();
          StringBuilder newRefText = new StringBuilder();
          if (parent instanceof GrReferenceExpression &&
              usage == ((GrReferenceExpression)parent).getQualifier() &&
              "call".equals(((GrReferenceExpression)parent).getReferenceName())) {
            newRefText.append(usage.getText());
            usage = parent;
          }
          else {
            PsiElement qualifier = ((GrReferenceExpression)usage).getQualifier();
            if (qualifier == null) {
              if (parent instanceof GrReferenceExpression &&
                  ((GrReferenceExpression)parent).getQualifier() != null &&
                  usage != ((GrReferenceExpression)parent).getQualifier()) {
                qualifier = ((GrReferenceExpression)parent).getQualifier();
                usage = parent;
              }
            }

            if (qualifier != null) {
              newRefText.append(qualifier.getText()).append('.');
              ((GrReferenceExpression)usage).setQualifier(null);
            }
            else {
              newRefText.append("this.");
            }
            newRefText.append('&').append(usage.getText());
          }
          usage.replace(factory.createReferenceExpressionFromText(newRefText.toString()));
        }
      }
    });
  }

  private static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(@NotNull PsiElement element) {
      if (element.getLanguage() != GroovyLanguage.INSTANCE) return false;

      final PsiElement parent = element.getParent();
      if (!(parent instanceof GrField field)) return false;

      if (field.getNameIdentifierGroovy() != element) return false;

      final PsiElement varDeclaration = field.getParent();
      if (!(varDeclaration instanceof GrVariableDeclaration)) return false;
      if (((GrVariableDeclaration)varDeclaration).getVariables().length != 1) return false;

      final GrExpression expression = field.getInitializerGroovy();
      return expression instanceof GrClosableBlock;
    }
  }
}
