package ru.adelf.idea.dotenv.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtTreeVisitor;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Set;

class KotlinEnvironmentCallsVisitor extends KtTreeVisitor<Set<KeyUsagePsiElement>> {
    @Override
    public Void visitCallExpression(@NotNull KtCallExpression expression, Set<KeyUsagePsiElement> data) {
        KeyUsagePsiElement keyUsage = KotlinPsiHelper.getKeyUsageFromCall(expression);

        if (keyUsage != null) {
            data.add(keyUsage);
        }

        return super.visitCallExpression(expression, data);
    }

    @Override
    public Void visitArrayAccessExpression(@NotNull KtArrayAccessExpression expression, Set<KeyUsagePsiElement> data) {
        KeyUsagePsiElement keyUsage = KotlinPsiHelper.getKeyUsageFromArrayAccess(expression);

        if (keyUsage != null) {
            data.add(keyUsage);
        }

        return super.visitArrayAccessExpression(expression, data);
    }
}
