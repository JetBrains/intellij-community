// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.*;

public class MissingAccessibleContextInspection extends DevKitUastInspectionBase {

  public static final int MAX_EXPRESSIONS_TO_PROCESS = 16;

  public MissingAccessibleContextInspection() {
  }

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    @SuppressWarnings("unchecked")
    Class<? extends UElement>[] hint = new Class[]{UMethod.class, ULambdaExpression.class};
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new MissingAccessibleContextVisitor(holder), hint);
  }

  static class MissingAccessibleContextVisitor extends AbstractUastNonRecursiveVisitor {
    private final ProblemsHolder myHolder;

    MissingAccessibleContextVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public boolean visitLambdaExpression(@NotNull ULambdaExpression lambda) {
      List<UParameter> parameters = lambda.getValueParameters();
      if (parameters.size() == 5 || parameters.size() == 6) {
        PsiClass lambdaClass = PsiUtil.resolveClassInClassTypeOnly(lambda.getFunctionalInterfaceType());
        if (lambdaClass != null) {
          String qualifiedName = lambdaClass.getQualifiedName();
          if ("javax.swing.ListCellRenderer".equals(qualifiedName) ||
              "javax.swing.table.TableCellRenderer".equals(qualifiedName)) {
            processBody(lambda.getBody(), lambda);
          }
        }
      }
      return true;
    }

    @Override
    public boolean visitMethod(@NotNull UMethod method) {
      if (method.getName().equals("getListCellRendererComponent") ||
          method.getName().equals("getTableCellRendererComponent")) {
        PsiMethod psi = method.getJavaPsi();
        PsiMethod[] methods = psi.findDeepestSuperMethods();
        if (methods.length == 1) {
          PsiClass aClass = methods[0].getContainingClass();
          if (aClass != null) {
            String qualifiedName = aClass.getQualifiedName();
            if ("javax.swing.ListCellRenderer".equals(qualifiedName) ||
                "javax.swing.table.TableCellRenderer".equals(qualifiedName)) {
              processBody(method.getUastBody(), method);
            }
          }
        }
      }
      return true;
    }

    private void processBody(UExpression body, UElement context) {
      List<PsiElement> anchors = new ArrayList<>();
      for (UExpression expression : findReturnedExpressions(body, context)) {
        PsiClass panelClass = findReturnedClass(expression);
        if (panelClass == null || hasRedefinedContext(panelClass)) continue;
        ContainerUtil.addIfNotNull(anchors, expression.getSourcePsi());
      }
      if (!anchors.isEmpty() && !hasAccessibilityMethodCalls(body)) {
        for (PsiElement anchor : anchors) {
          myHolder.registerProblem(anchor, DevKitBundle.message("inspection.message.accessible.context.not.defined.for.jpanel"));
        }
      }
    }

    private static boolean hasAccessibilityMethodCalls(UExpression body) {
      var visitor = new AbstractUastVisitor() {
        boolean myHasAccessibilityMethodCall = false;
        
        private boolean isAccessibilityMethod(@NotNull UCallExpression call) {
          String methodName = call.getMethodName();
          if (methodName == null) return false;
          if (methodName.equals("setAccessibleName") || methodName.equals("setAccessibleDescription")) {
            return true;
          }
          PsiMethod target = call.resolve();
          if (target != null) {
            PsiClass aClass = target.getContainingClass();
            if (aClass != null && "com.intellij.util.ui.accessibility.AccessibleContextUtil".equals(aClass.getQualifiedName())) {
              return true;
            }
          }
          return false;
        }
        
        @Override
        public boolean visitCallExpression(@NotNull UCallExpression call) {
          if (isAccessibilityMethod(call)) {
            myHasAccessibilityMethodCall = true;
            return false;
          }
          return true;
        }
      };
      body.accept(visitor);
      return visitor.myHasAccessibilityMethodCall;
    }

    private static Set<UExpression> findReturnedExpressions(@NotNull UExpression body, @NotNull UElement context) {
      Queue<UExpression> workList = findDirectExpressions(body, context);
      Set<UExpression> processed = new HashSet<>();
      Set<UExpression> leafs = new HashSet<>();
      while (!workList.isEmpty() && processed.size() < MAX_EXPRESSIONS_TO_PROCESS) {
        UExpression next = workList.poll();
        next = UastUtils.skipParenthesizedExprDown(next);
        if (next == null || !processed.add(next)) continue;
        if (next instanceof UIfExpression) {
          ContainerUtil.addIfNotNull(workList, ((UIfExpression)next).getThenExpression());
          ContainerUtil.addIfNotNull(workList, ((UIfExpression)next).getElseExpression());
        }
        else if (next instanceof UBlockExpression) {
          ContainerUtil.addIfNotNull(workList, ContainerUtil.getLastItem(((UBlockExpression)next).getExpressions()));
        }
        else if (next instanceof UReferenceExpression) {
          PsiElement psiVar = ((UReferenceExpression)next).resolve();
          ULocalVariable uVar = UastContextKt.toUElement(psiVar, ULocalVariable.class);
          if (uVar != null) {
            ContainerUtil.addIfNotNull(workList, uVar.getUastInitializer());
            PsiElement bodyPsi = body.getSourcePsi();
            if (bodyPsi != null) {
              for (PsiReference ref : ReferencesSearch.search(psiVar, new LocalSearchScope(bodyPsi))) {
                UExpression lValue = UastContextKt.toUElement(ref.getElement(), UExpression.class);
                if (lValue != null) {
                  UBinaryExpression parent = ObjectUtils.tryCast(lValue.getUastParent(), UBinaryExpression.class);
                  if (parent != null && parent.getOperator() == UastBinaryOperator.ASSIGN &&
                      lValue.equals(parent.getLeftOperand())) {
                    ContainerUtil.addIfNotNull(workList, parent.getRightOperand());
                  }
                }
              }
            }
          }
        }
        else if (next instanceof UCallExpression) {
          leafs.add(next);
        }
      }
      return leafs;
    }

    @NotNull
    private static Queue<UExpression> findDirectExpressions(@NotNull UExpression body, @NotNull UElement context) {
      Queue<UExpression> direct = new ArrayDeque<>();
      if (body instanceof UBlockExpression) {
        body.accept(new AbstractUastVisitor() {
          @Override
          public boolean visitReturnExpression(@NotNull UReturnExpression node) {
            if (context.equals(node.getJumpTarget())) {
              ContainerUtil.addIfNotNull(direct, node.getReturnExpression());
            }
            return true;
          }
        });
      }
      else if (body instanceof UReturnExpression) {
        direct.add(((UReturnExpression)body).getReturnExpression());
      }
      return direct;
    }

    private static boolean hasRedefinedContext(PsiClass panelClass) {
      MethodSignature signature = MethodSignatureUtil
        .createMethodSignature("getAccessibleContext", PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      PsiMethod found = MethodSignatureUtil.findMethodBySignature(panelClass, signature, true);
      if (found != null) {
        PsiClass containingClass = found.getContainingClass();
        if (containingClass != null && !"javax.swing.JPanel".equals(containingClass.getQualifiedName())) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    private static PsiClass findReturnedClass(UExpression result) {
      PsiClass panelClass = null;
      if (result instanceof UObjectLiteralExpression) {
        panelClass = ((UObjectLiteralExpression)result).getDeclaration().getJavaPsi();
      }
      else if (result instanceof UCallExpression) {
        UCallExpression call = (UCallExpression)result;
        if (call.getKind() == UastCallKind.CONSTRUCTOR_CALL) {
          UReferenceExpression ref = call.getClassReference();
          if (ref != null) {
            panelClass = ObjectUtils.tryCast(ref.resolve(), PsiClass.class);
          }
        }
      }
      return panelClass;
    }
  }
}
