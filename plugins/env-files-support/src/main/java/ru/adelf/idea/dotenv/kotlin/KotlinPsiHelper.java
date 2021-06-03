package ru.adelf.idea.dotenv.kotlin;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.*;
import ru.adelf.idea.dotenv.java.JavaEnvironmentClasses;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Arrays;
import java.util.List;

class KotlinPsiHelper {
    /**
     * Checks that this element environment string
     *
     * @param literal Checking psi element
     */
    static boolean isEnvStringLiteral(KtLiteralStringTemplateEntry literal) {
        if (!(literal.getParent() instanceof KtStringTemplateExpression)) {
            return false;
        }

        PsiElement parent = literal.getParent().getParent();
        if (parent instanceof KtValueArgument) {
            return isMethodCallLiteral((KtValueArgument) parent);
        }

        if (parent instanceof KtContainerNode) {
            return isArrayAccessLiteral((KtContainerNode) parent);
        }

        return false;
    }

    private static boolean isMethodCallLiteral(KtValueArgument valueArgument) {
        PsiElement valueArgumentList = valueArgument.getParent();

        if (!(valueArgumentList instanceof KtValueArgumentList)) {
            return false;
        }

        if (((KtValueArgumentList) valueArgumentList).getArguments().get(0) != valueArgument) {
            return false;
        }

        PsiElement methodCall = valueArgumentList.getParent();

        if (!(methodCall instanceof KtCallExpression)) return false;

        return isEnvMethodCall((KtCallExpression) methodCall);
    }

    private static boolean isArrayAccessLiteral(KtContainerNode containerNode) {
        if (!(containerNode.getParent() instanceof KtArrayAccessExpression)) {
            return false;
        }

        return isEnvArrayAccess((KtArrayAccessExpression) containerNode.getParent());
    }

    /**
     * Checks whether this function reference is reference for env functions, like env or getenv
     *
     * @param methodCallExpression Checking reference
     * @return true if condition filled
     */
    static boolean isEnvMethodCall(KtCallExpression methodCallExpression) {
        PsiElement nameElement = methodCallExpression.getCalleeExpression();

        if (!(nameElement instanceof KtNameReferenceExpression)) {
            return false;
        }

        String methodName = ((KtNameReferenceExpression) nameElement).getReferencedName();

        if (JavaEnvironmentClasses.isDirectMethodCall(methodName)) {
            return true;
        }

        List<String> classNames = JavaEnvironmentClasses.getClassNames(methodName);

        if (classNames == null) {
            return false;
        }

        return checkReferences(methodCallExpression.getCalleeExpression(), classNames);
    }

    @Nullable
    static KeyUsagePsiElement getKeyUsageFromCall(@NotNull KtCallExpression expression) {
        if (!isEnvMethodCall(expression)) {
            return null;
        }

        KtValueArgumentList valueArgumentList = expression.getValueArgumentList();

        if (valueArgumentList == null || valueArgumentList.getArguments().isEmpty()) {
            return null;
        }

        KtValueArgument valueArgument = valueArgumentList.getArguments().get(0);

        if (valueArgument == null) {
            return null;
        }

        return getKeyUsageFromStringTemplate(valueArgument.getFirstChild());
    }

    /**
     * Checks whether this array access is environment call
     *
     * @param arrayAccess Checking array
     * @return true if condition filled
     */
    static boolean isEnvArrayAccess(KtArrayAccessExpression arrayAccess) {
        List<String> classNames = JavaEnvironmentClasses.getClassNames("get");

        if (classNames == null) {
            return false;
        }

        return checkReferences(arrayAccess, classNames);
    }

    @Nullable
    static KeyUsagePsiElement getKeyUsageFromArrayAccess(@NotNull KtArrayAccessExpression expression) {
        if (!isEnvArrayAccess(expression)) {
            return null;
        }

        List<KtExpression> indexExpressions = expression.getIndexExpressions();

        if (indexExpressions.isEmpty()) {
            return null;
        }

        return getKeyUsageFromStringTemplate(indexExpressions.get(0));
    }

    @Nullable
    private static KeyUsagePsiElement getKeyUsageFromStringTemplate(PsiElement element) {
        if (!(element instanceof KtStringTemplateExpression)) {
            return null;
        }

        KtLiteralStringTemplateEntry literal = PsiTreeUtil.findChildOfType(element, KtLiteralStringTemplateEntry.class);

        if (literal == null) {
            return null;
        }

        return new KeyUsagePsiElement(literal.getText(), literal);
    }

    private static boolean checkReferences(PsiElement element, List<String> classNames) {
        return Arrays.stream(element.getReferences()).anyMatch(psiReference -> {
            PsiElement method = psiReference.resolve();

            if (method instanceof KtNamedFunction) {
                KtClass ktClass = PsiTreeUtil.getParentOfType(method, KtClass.class);

                return ktClass != null && classNames.contains(ktClass.getName());
            } else if (method instanceof PsiMethod) {
                // Maybe it's a Java reference?
                PsiClass psiClass = ((PsiMethod) method).getContainingClass();

                return psiClass != null && classNames.contains(psiClass.getName());
            }

            return false;
        });
    }
}
