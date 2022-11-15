// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.generate.UastElementFactory;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.List;
import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class UseCoupleInspection extends DevKitUastInspectionBase {
  private static final String PAIR_CLASS_NAME = Pair.class.getName();
  private static final String COUPLE_CLASS_NAME = Couple.class.getName();

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UDeclaration.class, UCallExpression.class};

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitDeclaration(@NotNull UDeclaration node) {
        UTypeReferenceExpression typeReferenceExpression = getDeclarationTypeReferenceExpression(node);
        if (typeReferenceExpression != null) {
          String pairTypeName = getPairTypeParameterNameIfBothTheSame(typeReferenceExpression);
          if (pairTypeName != null) {
            PsiElement sourcePsi = typeReferenceExpression.getSourcePsi();
            if (sourcePsi != null) {
              holder.registerProblem(sourcePsi,
                                     DevKitBundle.message("inspections.use.couple.type", pairTypeName),
                                     node.getLang().is(JavaLanguage.INSTANCE) ? new LocalQuickFix[]{new UseCoupleTypeFix(pairTypeName)}
                                                                              : LocalQuickFix.EMPTY_ARRAY);
            }
          }
        }
        return super.visitDeclaration(node);
      }

      @Nullable
      private static String getPairTypeParameterNameIfBothTheSame(@NotNull UTypeReferenceExpression typeReferenceExpression) {
        PsiType type = typeReferenceExpression.getType();
        if (PsiTypesUtil.classNameEquals(type, PAIR_CLASS_NAME)) {
          PsiClassType classType = (PsiClassType)type;
          PsiType[] parameters = classType.getParameters();
          if (parameters.length == 2 && parameters[0].equals(parameters[1])) {
            return parameters[0].getPresentableText();
          }
        }
        return null;
      }

      private static UTypeReferenceExpression getDeclarationTypeReferenceExpression(@NotNull UDeclaration declaration) {
        if (declaration instanceof UVariable v) return v.getTypeReference();
        if (declaration instanceof UMethod m) return m.getReturnTypeReference();
        return null;
      }

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression expression) {
        if (expression.getKind() == UastCallKind.METHOD_CALL) {
          if (isPairFactoryMethodWithTheSameArgumentTypes(expression)) {
            PsiElement sourcePsi = getMethodCallSourcePsi(expression);
            if (sourcePsi != null) {
              holder.registerProblem(sourcePsi, DevKitBundle.message("inspections.use.couple.of"), new ConvertToCoupleFactoryMethodFix());
            }
          }
        }
        return super.visitCallExpression(expression);
      }

      private static boolean isPairFactoryMethodWithTheSameArgumentTypes(@NotNull UCallExpression methodExpression) {
        String methodName = methodExpression.getMethodName();
        if ("create".equals(methodName) || "pair".equals(methodName)) {
          PsiMethod method = methodExpression.resolve();
          if (method == null) return false;
          PsiClass psiClass = method.getContainingClass();
          if (psiClass != null && PAIR_CLASS_NAME.equals(psiClass.getQualifiedName()) && methodExpression.getValueArgumentCount() == 2) {
            List<UExpression> arguments = methodExpression.getValueArguments();
            PsiType type1 = arguments.get(0).getExpressionType();
            if (type1 == null) return false;
            PsiType type2 = arguments.get(1).getExpressionType();
            return Objects.equals(type1, type2);
          }
        }
        return false;
      }

      @Nullable
      private static PsiElement getMethodCallSourcePsi(UCallExpression expression) {
        UElement parent = expression.getUastParent();
        if (parent instanceof UQualifiedReferenceExpression) {
          return parent.getSourcePsi();
        }
        return expression.getSourcePsi();
      }
    }, HINTS);
  }

  private static class ConvertToCoupleFactoryMethodFix implements LocalQuickFix {

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      UCallExpression callExpression = getUCallExpression(element);
      if (callExpression == null) return;
      UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(element.getLanguage());
      if (generationPlugin == null) return;
      UastElementFactory pluginElementFactory = generationPlugin.getElementFactory(project);
      UCallExpression coupleFactoryMethodCall = pluginElementFactory.createCallExpression(
        pluginElementFactory.createQualifiedReference(COUPLE_CLASS_NAME, element),
        "of",
        callExpression.getValueArguments(),
        null,
        UastCallKind.METHOD_CALL,
        element
      );
      if (coupleFactoryMethodCall == null) return;
      generationPlugin.replace(UastUtils.getQualifiedParentOrThis(callExpression), coupleFactoryMethodCall, UCallExpression.class);
    }

    @Nullable
    private static UCallExpression getUCallExpression(PsiElement element) {
      UElement expression = UastContextKt.toUElement(element);
      if (expression instanceof UCallExpression callExpression) {
        return callExpression;
      }
      if (expression instanceof UQualifiedReferenceExpression qualifiedReferenceExpression) {
        UExpression selector = qualifiedReferenceExpression.getSelector();
        if (selector instanceof  UCallExpression callExpression) {
          return callExpression;
        }
      }
      return null;
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return DevKitBundle.message("inspections.use.couple.of");
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.couple.family.name");
    }
  }

  private static class UseCoupleTypeFix implements LocalQuickFix {

    private final String mySimpleTypeParameterName;

    UseCoupleTypeFix(String simpleTypeParameterName) {
      mySimpleTypeParameterName = simpleTypeParameterName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      if (element instanceof PsiTypeElement) {
        PsiTypeElement typeElement = (PsiTypeElement)element;
        PsiClassType type1 = (PsiClassType)typeElement.getType();
        PsiType[] parameters = type1.getParameters();
        if (parameters.length != 2) {
          return;
        }
        PsiTypeElement newType =
          factory.createTypeElementFromText(COUPLE_CLASS_NAME + "<" + parameters[0].getCanonicalText() + ">", element.getContext());
        PsiElement newElement = element.replace(newType);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
      }
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return DevKitBundle.message("inspections.use.couple.type", mySimpleTypeParameterName);
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.use.couple.family.name");
    }
  }
}
