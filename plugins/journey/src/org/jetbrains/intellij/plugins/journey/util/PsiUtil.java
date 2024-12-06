package org.jetbrains.intellij.plugins.journey.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public final class PsiUtil {

  @Contract("null, _ -> null")
  public static @Nullable PsiElement tryFindParentOrNull(@Nullable PsiElement element, Predicate<@NotNull PsiElement> test) {
    if (element == null) return null;
    return ReadAction.compute(() -> {
      var element1 = element;
      while (element1 != null) {
        if (test.test(element1)) {
          return element1;
        }
        element1 = element1.getParent();
      }
      return null;
    });
  }

  @Contract("null -> null")
  public static @Nullable PsiElement tryFindIdentifier(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element instanceof PsiNameIdentifierOwner nameIdentifierOwner) {
      return ReadAction.compute(() -> nameIdentifierOwner.getNameIdentifier());
    }
    return null;
  }

  public static @Nullable @NlsSafe String tryGetPresentableTitle(PsiElement element) {
    if (element instanceof PsiClass psiClass) {
      return ReadAction.compute(() -> psiClass.getName());
    }
    if (element instanceof PsiMember psiMember) {
      return ReadAction.compute(() -> {
        String name = psiMember.getName();
        PsiClass containingClass = psiMember.getContainingClass();
        if (containingClass == null) {
          return name;
        }
        else return containingClass.getName() + "." + name;
      });
    }
    return getQualifiedName(element);
  }

  public static SmartPsiElementPointer createSmartPointer(PsiElement element) {
    return ReadAction.compute(() -> {
      return SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    });
  }

  public static boolean contains(SmartPsiElementPointer element, SmartPsiElementPointer child) {
    return ReadAction.compute(() -> {
      PsiElement psiElement = Objects.requireNonNull(element.getElement());
      PsiElement childElement = Objects.requireNonNull(child.getElement());
      return psiElement.getContainingFile().equals(childElement.getContainingFile()) &&
             psiElement.getTextRange().contains(childElement.getTextRange());
    });
  }

  private static final String MEMBER_DELIMITER = "#";
  private static final String EXPR_DELIMITER = ":";

  public static @Nullable String getQualifiedName(@Nullable PsiElement element) {
    if (element instanceof PsiClass psiClass) return psiClass.getQualifiedName();
    if (element instanceof PsiMethod psiMethod) {
      if (psiMethod.getContainingClass() != null) {
        return psiMethod.getContainingClass().getQualifiedName() + MEMBER_DELIMITER + psiMethod.getName();
      }
    }
    if (element instanceof PsiField psiField) {
      if (psiField.getContainingClass() != null) {
        return psiField.getContainingClass().getQualifiedName() + MEMBER_DELIMITER + psiField.getName();
      }
    }
    if (element instanceof PsiReferenceExpression psiReferenceExpression) {
      var member = tryFindParentOrNull(psiReferenceExpression, it -> it instanceof PsiMember);
      if (member == null) {
        return null;
      }
      return getQualifiedName(member) + EXPR_DELIMITER + psiReferenceExpression.getTextRange().shiftLeft(member.getTextRange().getStartOffset());
    }
    return null;
  }

  public static @Nullable PsiElement resolveElementByFQN(@NotNull String fqn, @NotNull Project project) {
    return ReadAction.compute(() -> {
      if (fqn.contains(EXPR_DELIMITER)) {
        PsiMember member = findMemberByName(fqn.substring(0, fqn.indexOf(EXPR_DELIMITER)), project);
        String expression = fqn.substring(fqn.indexOf(EXPR_DELIMITER) + EXPR_DELIMITER.length());
        String[] parts = expression.replace("(", "").replace(")", "").split(",");
        TextRange elementTextRange = TextRange.create(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        PsiElement element = member.getContainingFile().
          findElementAt(member.getTextRange().getStartOffset() + elementTextRange.getStartOffset()).getParent();
        if (element.getTextRange().getLength() == elementTextRange.getLength()) {
          return element;
        }
        return null;
      }
      if (fqn.contains(MEMBER_DELIMITER)) {
        return findMemberByName(fqn, project);
      }
      return resolveClassByFQN(fqn, project);
    });
  }

  private static @Nullable PsiMethod findMemberByName(String fqn, @NotNull Project project) {
    int delimiterPosition = fqn.indexOf(MEMBER_DELIMITER);
    String classFQN = fqn.substring(0, delimiterPosition);
    PsiClass psiClass = resolveClassByFQN(classFQN, project);
    String methodName = fqn.substring(delimiterPosition + MEMBER_DELIMITER.length());
    if (psiClass == null) {
      return null;
    }
    PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
    if (methods.length == 0) {
//      LOG.warn("No method named " + methodName + " in class " + psiClass.getQualifiedName());
    }
    if (methods.length > 1) {
//      LOG.warn("More than one method named " + methodName + " in class " + psiClass.getQualifiedName());
    }
    if (methods.length > 0) {
      return methods[0];
    }
    return null;
  }

  private static @Nullable PsiClass resolveClassByFQN(@NotNull String fqn, @NotNull Project project) {
    return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
  }
}
