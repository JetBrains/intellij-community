package com.intellij.codeInspection.booleanIsAlwaysInverted;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * User: anna
 * Date: 06-Jan-2006
 */
public class BooleanMethodIsAlwaysInvertedInspection extends GlobalJavaInspectionTool {
  private static final Key<Boolean> ALWAYS_INVERTED = Key.create("ALWAYS_INVERTED_METHOD");

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("boolean.method.is.always.inverted.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DATA_FLOW_ISSUES;
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "BooleanMethodIsAlwaysInverted";
  }

  @Nullable
  public RefGraphAnnotator getAnnotator(final RefManager refManager) {
    return new BooleanInvertedAnnotator();
  }

  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, final InspectionManager manager, final GlobalInspectionContext globalContext) {
    if (refEntity instanceof RefMethod) {
      RefMethod refMethod = (RefMethod)refEntity;
      if (!refMethod.isReferenced()) return null;
      if (hasNonInvertedCalls(refMethod)) return null;
      if (!refMethod.getSuperMethods().isEmpty()) return null;
      final PsiMethod psiMethod = (PsiMethod)refMethod.getElement();
      final PsiIdentifier psiIdentifier = psiMethod.getNameIdentifier();
      if (psiIdentifier != null) {
        return new ProblemDescriptor[] { manager.createProblemDescriptor(psiIdentifier,
                                                                         InspectionsBundle.message("boolean.method.is.always.inverted.problem.descriptor"),
                                                                         new InvertMethodFix(),
                                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false)};
      }
    }
    return null;
  }

  private static boolean hasNonInvertedCalls(final RefMethod refMethod) {
    final Boolean alwaysInverted = refMethod.getUserData(ALWAYS_INVERTED);
    if (alwaysInverted == null) return true;
    if (refMethod.isExternalOverride()) return true;
    if (refMethod.isReferenced() && !alwaysInverted.booleanValue()) return true;
    final Collection<RefMethod> superMethods = refMethod.getSuperMethods();
    for (RefMethod superMethod : superMethods) {
      if (hasNonInvertedCalls(superMethod)) return true;
    }
    return false;
  }

  protected boolean queryExternalUsagesRequests(final RefManager manager, final GlobalJavaInspectionContext context,
                                                final ProblemDescriptionsProcessor descriptionsProcessor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitMethod(final RefMethod refMethod) {
        if (descriptionsProcessor.getDescriptions(refMethod) != null) { //suspicious method -> need to check external usages
          final GlobalJavaInspectionContext.UsagesProcessor usagesProcessor = new GlobalJavaInspectionContext.UsagesProcessor() {
            public boolean process(PsiReference psiReference) {
              final PsiElement psiReferenceExpression = psiReference.getElement();
              if (psiReferenceExpression instanceof PsiReferenceExpression &&
                  !isInvertedMethodCall((PsiReferenceExpression) psiReferenceExpression)) {
                descriptionsProcessor.ignoreElement(refMethod);
              }
              return false;
            }
          };
          traverseSuperMethods(refMethod, context, usagesProcessor);
        }
      }
    });
    return false;
  }

  private static void traverseSuperMethods(RefMethod refMethod, GlobalJavaInspectionContext globalContext, GlobalJavaInspectionContext.UsagesProcessor processor){
    final Collection<RefMethod> superMethods = refMethod.getSuperMethods();
    for (RefMethod superMethod : superMethods) {
      traverseSuperMethods(superMethod, globalContext, processor);
    }
    globalContext.enqueueMethodUsagesProcessor(refMethod, processor);
  }

  private static void checkMethodCall(RefElement refWhat, final PsiElement element) {
    if (!(refWhat instanceof RefMethod)) return;
    final RefMethod refMethod = (RefMethod)refWhat;
    final PsiElement psiElement = refMethod.getElement();
    if (!(psiElement instanceof PsiMethod)) return;
    final PsiMethod psiMethod = (PsiMethod)psiElement;
    if (!PsiType.BOOLEAN.equals(psiMethod.getReturnType())) return;
    element.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression.isReferenceTo(psiMethod)) {
          if (isInvertedMethodCall(methodExpression)) return;
          refMethod.putUserData(ALWAYS_INVERTED, Boolean.FALSE);
        }
      }
    });
  }

  private static boolean isInvertedMethodCall(final PsiReferenceExpression methodExpression) {
    final PsiPrefixExpression prefixExpression = PsiTreeUtil.getParentOfType(methodExpression, PsiPrefixExpression.class);
    if (methodExpression.getQualifierExpression() instanceof PsiSuperExpression) return true; //don't flag super calls
    if (prefixExpression != null) {
      final PsiJavaToken sign = prefixExpression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      if (tokenType.equals(JavaTokenType.EXCL)) {
        return true;
      }
    }
    return false;
  }

  private static class BooleanInvertedAnnotator extends RefGraphAnnotator {
    public void onInitialize(RefElement refElement) {
      if (refElement instanceof RefMethod) {
        final PsiElement element = refElement.getElement();
        if (!(element instanceof PsiMethod)) return;
        if (((PsiMethod)element).getReturnType() != PsiType.BOOLEAN) return;
        refElement.putUserData(ALWAYS_INVERTED, Boolean.TRUE); //initial mark boolean methods
      }
    }

    public void onMarkReferenced(RefElement refWhat, RefElement refFrom, boolean referencedFromClassInitializer) {
      checkMethodCall(refWhat, refFrom.getElement());
    }
  }

  @Override
  public QuickFix getQuickFix(final String hint) {
    return new InvertMethodFix();
  }

  private static class InvertMethodFix implements LocalQuickFix {

    @NotNull
    public String getName() {
      return "Invert method";
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      assert psiMethod != null;
      final RefactoringActionHandler invertBooleanHandler = JavaRefactoringActionHandlerFactory.getInstance().createInvertBooleanHandler();
      invertBooleanHandler.invoke(project, new PsiElement[]{psiMethod}, DataManager.getInstance().getDataContext());
    }
  }
}
