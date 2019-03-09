package ru.adelf.idea.dotenv.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class DotEnvPsiUtil {
    public static String getKeyText(DotEnvProperty element) {
        // IMPORTANT: Convert embedded escaped spaces to simple spaces
        return element.getKey().getText().replaceAll("\\\\ ", " ");
    }

    public static String getValueText(DotEnvProperty element) {
        ASTNode valueNode = element.getNode().findChildByType(DotEnvTypes.VALUE);
        if (valueNode != null) {
            return valueNode.getText();
        } else {
            return "";
        }
    }

    public static String getName(DotEnvProperty element) {
        return getKeyText(element);
    }

    public static PsiElement setName(DotEnvProperty element, @NotNull String newName) {
        ASTNode keyNode = element.getNode().findChildByType(DotEnvTypes.KEY);
        if (keyNode != null) {
            DotEnvProperty property = DotEnvElementFactory.createProperty(element.getProject(), newName);
            ASTNode newKeyNode = property.getFirstChild().getNode();
            element.getNode().replaceChild(keyNode, newKeyNode);
        }
        return element;
    }

    public static PsiElement getNameIdentifier(DotEnvProperty element) {
        return element.getKey();
    }
}
