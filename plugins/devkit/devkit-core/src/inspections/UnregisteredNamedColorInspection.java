// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uast.UastVisitorAdapter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.completion.UiDefaultsHardcodedKeys;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.List;

/**
 * @see UiDefaultsHardcodedKeys
 */
public class UnregisteredNamedColorInspection extends DevKitUastInspectionBase {
  private static final String JB_COLOR_FQN = JBColor.class.getCanonicalName();
  private static final String NAMED_COLOR_METHOD_NAME = "namedColor";

  private static final String UI_DEFAULTS_HARDCODED_KEYS_FQN = UiDefaultsHardcodedKeys.class.getCanonicalName();
  private static final String FIELD_UI_DEFAULTS_KEYS = "UI_DEFAULTS_KEYS";
  private static final String FIELD_NAMED_COLORS = "NAMED_COLORS";

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isIdeaProject(holder.getProject())) return PsiElementVisitor.EMPTY_VISITOR;

    return new UastVisitorAdapter(new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitExpression(@NotNull UExpression node) {
        if (node instanceof UCallExpression) {
          handleCallExpression(holder, (UCallExpression)node);
        }
        else if (node instanceof UQualifiedReferenceExpression) {
          UElement parent = node.getUastParent();
          if (parent instanceof UCallExpression) {
            handleCallExpression(holder, (UCallExpression)parent);
          }
        }

        return true;
      }
    }, true);
  }

  private static void handleCallExpression(@NotNull ProblemsHolder holder, @NotNull UCallExpression expression) {
    if (!isNamedColorCall(expression)) return;

    String key = getKey(expression);
    if (key == null) return;

    Project project = holder.getProject();
    PsiClass hardcodedKeysClass = JavaPsiFacade.getInstance(project)
      .findClass(UI_DEFAULTS_HARDCODED_KEYS_FQN, GlobalSearchScope.allScope(project));
    if (hardcodedKeysClass == null) return;

    if (!isIncludedInHardcodedNamedColors(hardcodedKeysClass, key)) {
      registerProblem(key, holder, expression, createFix(hardcodedKeysClass, key));
    }
  }

  private static boolean isNamedColorCall(@NotNull UCallExpression expression) {
    if (!NAMED_COLOR_METHOD_NAME.equals(expression.getMethodName())) return false;
    PsiMethod resolved = expression.resolve();
    if (resolved == null) return false;
    PsiClass containingClass = resolved.getContainingClass();
    return containingClass != null && JB_COLOR_FQN.equals(containingClass.getQualifiedName());
  }

  @Nullable
  private static String getKey(@NotNull UCallExpression expression) {
    List<UExpression> arguments = expression.getValueArguments();
    if (arguments.isEmpty()) return null;
    UExpression firstArgument = arguments.get(0);
    Object evaluated = firstArgument.evaluate();
    if (!(evaluated instanceof String)) return null;
    return (String)evaluated;
  }

  //TODO consider caching here
  private static boolean isIncludedInHardcodedNamedColors(@NotNull PsiClass hardcodedKeysClass, @NotNull String key) {
    PsiField[] fields = hardcodedKeysClass.getFields();
    for (PsiField field : fields) {
      String fieldName = field.getName();
      if (!FIELD_UI_DEFAULTS_KEYS.equals(fieldName) && !FIELD_NAMED_COLORS.equals(fieldName)) continue;

      PsiExpression initializer = field.getInitializer();
      if (!(initializer instanceof PsiMethodCallExpression)) continue;

      PsiExpressionList argumentList = ((PsiMethodCallExpression)initializer).getArgumentList();
      for (PsiExpression expression : argumentList.getExpressions()) {
        if (!(expression instanceof PsiLiteralExpression)) continue;
        String registeredKey = StringUtil.trimStart(StringUtil.trimEnd(expression.getText(), "\""), "\"");
        if (key.equals(registeredKey)) return true;
      }
    }
    return false;
  }

  private static void registerProblem(@NotNull String key,
                                      @NotNull ProblemsHolder holder,
                                      @NotNull UCallExpression expression,
                                      @Nullable LocalQuickFix fix) {
    UIdentifier identifier = expression.getMethodIdentifier();
    if (identifier == null) return;
    PsiElement identifierPsi = identifier.getPsi();
    if (identifierPsi == null) return;

    holder.registerProblem(identifierPsi,
                           DevKitBundle.message("inspections.unregistered.named.color", key),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           fix);
  }

  @Nullable
  private static LocalQuickFix createFix(@NotNull PsiClass hardcodedKeysClass, @NotNull String absentKey) {
    PsiField field = hardcodedKeysClass.findFieldByName(FIELD_NAMED_COLORS, false);
    if (field == null) return null;

    PsiExpression initializer = field.getInitializer();
    if (!(initializer instanceof PsiMethodCallExpression)) return null;

    return new LocalQuickFix() {

      @Override
      public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        return hardcodedKeysClass;
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return DevKitBundle.message("inspections.unregistered.named.color.quickfix");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiExpressionList argumentList = ((PsiMethodCallExpression)initializer).getArgumentList();
        PsiExpression additionalArgument = JavaPsiFacade.getElementFactory(project)
          .createExpressionFromText("\"" + absentKey + "\"", null);
        PsiElement addedArgument = argumentList.add(additionalArgument);
        PsiElement newLineElement = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n");
        argumentList.addAfter(newLineElement, addedArgument);
      }
    };
  }
}
