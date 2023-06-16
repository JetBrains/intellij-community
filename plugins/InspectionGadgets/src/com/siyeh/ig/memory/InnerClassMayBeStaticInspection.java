/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.memory;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public class InnerClassMayBeStaticInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public OrderedSet<String> ignorableAnnotations =
    new OrderedSet<>(Collections.singletonList(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED));

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inner.class.may.be.static.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                 new JavaClassValidator().annotationsOnly()));
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> fixes = new ArrayList<>();
    fixes.add(new InnerClassMayBeStaticFix());
    final PsiClass aClass = (PsiClass)infos[0];
    AddToIgnoreIfAnnotatedByListQuickFix.build(aClass, ignorableAnnotations, fixes);
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class InnerClassMayBeStaticFix extends InspectionGadgetsFix implements BatchQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.static.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      applyFix(project, new ProblemDescriptor[] {descriptor}, List.of(), null);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      PsiClass innerClass = ObjectUtils.tryCast(previewDescriptor.getStartElement().getParent(), PsiClass.class);
      if (innerClass == null) {
        return IntentionPreviewInfo.EMPTY;
      }
      Handler handler = new Handler(innerClass);
      handler.collectLocalReferences();
      handler.makeStatic();
      return IntentionPreviewInfo.DIFF;
    }

    @Override
    public void applyFix(@NotNull Project project,
                         CommonProblemDescriptor @NotNull [] descriptors,
                         @NotNull List psiElementsToIgnore, @Nullable Runnable refreshViews) {
      final List<Handler> handlers = new SmartList<>();
      for (CommonProblemDescriptor descriptor : descriptors) {
        final PsiElement element = ((ProblemDescriptor)descriptor).getPsiElement().getParent();
        if (!(element instanceof PsiClass)) continue;
        handlers.add(new Handler((PsiClass)element));
      }
      ProgressManager.getInstance().runProcessWithProgressSynchronously(
        () -> {
          handlers.forEach(Handler::collectReferences);
          List<PsiElement> elements = ContainerUtil.flatMap(handlers, handler -> handler.getElements());
          WriteCommandAction.writeCommandAction(project, elements)
            .withName(InspectionGadgetsBundle.message("make.static.quickfix"))
            .withGlobalUndo()
            .run(() -> handlers.forEach(Handler::makeStatic));
        }, InspectionGadgetsBundle.message("make.static.quickfix"), true, project);
    }

    private static class Handler {

      private final PsiClass innerClass;
      private List<PsiElement> references = null;

      Handler(PsiClass innerClass) {
        this.innerClass = innerClass;
      }

      public List<PsiElement> getElements() {
        final List<PsiElement> elements = new SmartList<>();
        elements.add(innerClass);
        elements.addAll(references);
        return elements;
      }

      /** Should be called under progress */
      void collectReferences() {
        ReadAction.run(() -> {
          final Collection<PsiReference> references = ReferencesSearch.search(innerClass, innerClass.getUseScope()).findAll();
          this.references = ContainerUtil.map(references, PsiReference::getElement);
        });
      }

      void collectLocalReferences() {
        this.references = SyntaxTraverser.psiTraverser(innerClass.getContainingFile())
          .filter(e -> e instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)e).isReferenceTo(innerClass)).toList();
      }

      void makeStatic() {
        final PsiModifierList modifiers = innerClass.getModifierList();
        if (modifiers == null) {
          return;
        }
        modifiers.setModifierProperty(PsiModifier.STATIC, true);
        final Project project = innerClass.getProject();
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        references.stream()
          .sorted((r1, r2) -> PsiUtilCore.compareElementsByPosition(r2, r1))
          .forEach(reference -> {
            final PsiElement parent = reference.getParent();
            if (parent instanceof PsiNewExpression newExpression) {
              final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
              if (classReference == null) {
                return;
              }
              final PsiExpressionList argumentList = newExpression.getArgumentList();
              if (argumentList == null) {
                return;
              }
              final PsiReferenceParameterList parameterList = classReference.getParameterList();
              final String genericParameters = parameterList != null ? parameterList.getText() : "";
              final String text = "new " + classReference.getQualifiedName() + genericParameters + argumentList.getText();
              final PsiExpression expression = factory.createExpressionFromText(text, innerClass);
              codeStyleManager.shortenClassReferences(newExpression.replace(expression));
            }
            else if (reference instanceof PsiJavaCodeReferenceElement ref) {
              removeTypeArguments(ref);
            }
          });
      }

      private static void removeTypeArguments(PsiJavaCodeReferenceElement ref) {
        if (ref == null || !(ref.getQualifier() instanceof PsiJavaCodeReferenceElement qualifier)) {
          return;
        }
        removeTypeArguments(qualifier);
        PsiReferenceParameterList parameterList = qualifier.getParameterList();
        if (parameterList != null && parameterList.getFirstChild() != null) {
          parameterList.delete();
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassMayBeStaticVisitor();
  }

  private class InnerClassMayBeStaticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC) ||
          PsiUtil.isLocalOrAnonymousClass(aClass)) {
        if (!HighlightingFeature.INNER_STATICS.isAvailable(aClass)) {
          return;
        }
      }
      for (PsiClass innerClass : aClass.getInnerClasses()) {
        if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        if (AnnotationUtil.isAnnotated(innerClass, ignorableAnnotations, 0)) {
          continue;
        }
        final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(innerClass);
        innerClass.accept(visitor);
        if (!visitor.canInnerClassBeStatic()) {
          continue;
        }
        registerClassError(innerClass, innerClass);
      }
    }
  }
}