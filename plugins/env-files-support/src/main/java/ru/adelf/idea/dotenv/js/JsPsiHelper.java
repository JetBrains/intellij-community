/*
package ru.adelf.idea.dotenv.js;

import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JsPsiHelper {

    static boolean checkPsiElement(@NotNull PsiElement psiElement) {
        if(!(psiElement instanceof LeafPsiElement)) {
            return false;
        }

        IElementType elementType = ((LeafPsiElement) psiElement).getElementType();
        if(!elementType.toString().equals("JS:IDENTIFIER")) {
            return false;
        }

        PsiElement parent = psiElement.getParent();
        if(!(parent instanceof JSReferenceExpression)) {
            return false;
        }

        return ((JSReferenceExpression) parent).getCanonicalText().startsWith("process.env");
    }

    @Nullable
    static String checkReferenceExpression(@NotNull JSReferenceExpression referenceExpression) {
        String text = referenceExpression.getCanonicalText();

        if(!text.startsWith("process.env.") || text.length() < 13) {
            return null;
        }

        return text.substring(12);
    }
}
*/
