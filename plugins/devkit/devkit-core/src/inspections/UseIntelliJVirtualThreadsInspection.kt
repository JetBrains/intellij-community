// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

/**
 * Suggest using IntelliJVirtualThreads.ofVirtual instead of Thread.ofVirtual
 */
public final class UseIntelliJVirtualThreadsInspection extends DevKitUastInspectionBase implements CleanupLocalInspectionTool {

  private static final String THREAD_CLASS = "java.lang.Thread";
  private static final String INTELLIJ_VIRTUAL_THREADS_FQN = "com.intellij.virtualThreads.IntelliJVirtualThreads";

  @SuppressWarnings("unchecked")
  private static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class};

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return DevKitInspectionUtil.isAllowed(holder.getFile()) &&
           DevKitInspectionUtil.isClassAvailable(holder, INTELLIJ_VIRTUAL_THREADS_FQN);
  }

  @Override
  public @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        if (!"ofVirtual".equals(node.getMethodName())) return super.visitCallExpression(node);
        PsiMethod method = node.resolve();
        boolean isThreadOfVirtual = false;
        if (method != null) {
          if (!method.hasModifierProperty(PsiModifier.STATIC)) return super.visitCallExpression(node);
          PsiClass containingClass = method.getContainingClass();
          if (containingClass == null) return super.visitCallExpression(node);
          String qName = containingClass.getQualifiedName();
          isThreadOfVirtual = THREAD_CLASS.equals(qName) || "Thread".equals(containingClass.getName());
        }
        else {
          // Fallback for environments where JDK doesn't provide Thread.ofVirtual (e.g., tests on older JDK):
          PsiElement sp = node.getSourcePsi();
          if (sp instanceof PsiMethodCallExpression) {
            PsiExpression qualifier = ((PsiMethodCallExpression)sp).getMethodExpression().getQualifierExpression();
            String qualifierText = qualifier == null ? null : qualifier.getText();
            isThreadOfVirtual = THREAD_CLASS.equals(qualifierText) || "Thread".equals(qualifierText);
          }
        }
        if (!isThreadOfVirtual) return super.visitCallExpression(node);

        PsiElement psi = node.getSourcePsi();
        if (psi != null) {
          LocalQuickFix[] fixes = psi.getLanguage().is(JavaLanguage.INSTANCE)
                                      ? new LocalQuickFix[]{new ReplaceWithIntelliJVirtualThreadsFix()}
                                      : LocalQuickFix.EMPTY_ARRAY;
          holder.registerProblem(psi, DevKitBundle.message("inspection.use.intellij.virtual.threads.message"), fixes);
        }
        return super.visitCallExpression(node);
      }
    }, HINTS);
  }

  private static class ReplaceWithIntelliJVirtualThreadsFix implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspection.use.intellij.virtual.threads.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
      if (call == null && element instanceof PsiMethodCallExpression) {
        call = (PsiMethodCallExpression) element;
      }
      if (call == null) return;

      PsiReferenceExpression methodExpression = call.getMethodExpression();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression newMethodRef = factory.createExpressionFromText(
        INTELLIJ_VIRTUAL_THREADS_FQN + ".ofVirtual", call
      );
      PsiElement replaced = methodExpression.replace(newMethodRef);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
    }
  }
}
