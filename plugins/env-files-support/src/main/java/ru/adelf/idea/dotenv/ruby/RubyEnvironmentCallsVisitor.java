package ru.adelf.idea.dotenv.ruby;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RArrayIndexing;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RConstant;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

class RubyEnvironmentCallsVisitor extends PsiRecursiveElementVisitor {
    final private Collection<KeyUsagePsiElement> collectedItems = new HashSet<>();

    @Override
    public void visitElement(PsiElement element) {
        if(element instanceof RArrayIndexing) {
            this.visitFunction((RArrayIndexing) element);
        }

        super.visitElement(element);
    }

    private void visitFunction(RArrayIndexing expression) {
        PsiElement receiver = expression.getReceiver();

        if(!(receiver instanceof RConstant)) {
            return;
        }

        if(receiver.getFirstChild() == null) {
            return;
        }

        if(!Objects.equals(receiver.getFirstChild().getText(), "ENV")) {
            return;
        }

        PsiElement stringLiteral = expression.getIndexes().get(0);

        if(stringLiteral == null) return;

        if(!(stringLiteral instanceof RStringLiteral)) return;

        String key = ((RStringLiteral)stringLiteral).getContent();

        collectedItems.add(new KeyUsagePsiElement(key, stringLiteral));
    }

    @NotNull
    Collection<KeyUsagePsiElement> getCollectedItems() {
        return collectedItems;
    }
}
