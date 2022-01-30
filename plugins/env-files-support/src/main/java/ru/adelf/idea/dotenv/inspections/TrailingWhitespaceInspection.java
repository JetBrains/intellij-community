package ru.adelf.idea.dotenv.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvFactory;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;
import ru.adelf.idea.dotenv.psi.DotEnvValue;
import ru.adelf.idea.dotenv.psi.impl.DotEnvValueImpl;

public class TrailingWhitespaceInspection extends LocalInspectionTool {
    // Change the display name within the plugin.xml
    // This needs to be here as otherwise the tests will throw errors.
    @NotNull
    @Override
    public String getDisplayName() {
        return "Value has trailing whitespace";
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        if (!(file instanceof DotEnvFile)) {
            return null;
        }

        return analyzeFile(file, manager, isOnTheFly).getResultsArray();
    }

    @NotNull
    private ProblemsHolder analyzeFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        ProblemsHolder problemsHolder = new ProblemsHolder(manager, file, isOnTheFly);

        PsiTreeUtil.findChildrenOfType(file, DotEnvValue.class).forEach(dotEnvValue -> {
            if (dotEnvValue.getText().matches(".*[ \\t]+")) {
                problemsHolder.registerProblem(dotEnvValue,
                    new TextRange(dotEnvValue.getText().stripTrailing().length(), dotEnvValue.getText().length()),
                    "Line has trailing whitespace.",
                    new TrailingWhitespaceInspection.RemoveTrailingWhitespaceQuickFix()
                );
            }
        });

        PsiTreeUtil.findChildrenOfType(file, PsiWhiteSpaceImpl.class).forEach(whiteSpace -> {
            if (whiteSpace.getText().matches("\\s*[ \\t]\\n\\s*")) {
                problemsHolder.registerProblem(whiteSpace,
                    "Line has trailing whitespace.",
                    new TrailingWhitespaceInspection.RemoveTrailingWhitespaceQuickFix()
                );
            }
        });

        return problemsHolder;
    }

    private static class RemoveTrailingWhitespaceQuickFix implements LocalQuickFix {

        @NotNull
        @Override
        public String getName() {
            return "Remove trailing whitespace";
        }

        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            try {
                PsiElement psiElement = descriptor.getPsiElement();

                if (psiElement instanceof DotEnvValueImpl) {
                    PsiElement newPsiElement = DotEnvFactory.createFromText(project, DotEnvTypes.VALUE,
                        "DUMMY_KEY=" + psiElement.getText().stripTrailing());
                    psiElement.replace(newPsiElement);
                } else if (psiElement instanceof PsiWhiteSpaceImpl) {
                    PsiElement newPsiElement = DotEnvFactory.createFromText(project, TokenType.WHITE_SPACE,
                        "DUMMY_KEY='VALUE'" + psiElement.getText().replaceAll("[ \\t]*\\n", "\n"));
                    psiElement.replace(newPsiElement);
                }
            } catch (IncorrectOperationException e) {
                Logger.getInstance(IncorrectDelimiterInspection.class).error(e);
            }
        }

        @NotNull
        public String getFamilyName() {
            return getName();
        }
    }
}
