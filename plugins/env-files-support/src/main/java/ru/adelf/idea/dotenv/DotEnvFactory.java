package ru.adelf.idea.dotenv;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class DotEnvFactory {
    public static PsiElement createFromText(@NotNull Project project, @NotNull IElementType type, @NotNull String text) {
        final Ref<PsiElement> ret = new Ref<>();
        PsiFile dummyFile = createDummyFile(project, text);
        dummyFile.accept(new PsiRecursiveElementWalkingVisitor() {
            public void visitElement(@NotNull PsiElement element) {
                ASTNode node = element.getNode();
                if (node != null && node.getElementType() == type) {
                    ret.set(element);
                    stopWalking();
                } else {
                    super.visitElement(element);
                }
            }
        });

        assert !ret.isNull() : "cannot create element from text:\n" + dummyFile.getText();

        return ret.get();
    }

    @NotNull
    private static PsiFile createDummyFile(Project project, String fileText) {
        return PsiFileFactory.getInstance(project).createFileFromText("DUMMY__.env", DotEnvFileType.INSTANCE, fileText, System.currentTimeMillis(), false);
    }
}
