package ru.adelf.idea.dotenv.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtraBlankLineInspection extends LocalInspectionTool {
    // Change the display name within the plugin.xml
    // This needs to be here as otherwise the tests will throw errors.
    @NotNull
    @Override
    public String getDisplayName() {
        return "Extra blank line";
    }

    @Override
    public boolean runForWholeFile() {
        return true;
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

        PsiTreeUtil.findChildrenOfType(file, PsiWhiteSpaceImpl.class).forEach(whiteSpace -> {
            Pattern pattern = Pattern.compile("\r\n|\r|\n");
            Matcher matcher = pattern.matcher(whiteSpace.getText());

            int count = 0;
            while (matcher.find())
                count++;

            if (count > 2) {
                problemsHolder.registerProblem(whiteSpace,
                    "Only one extra line allowed between properties",
                    new RemoveExtraBlankLineQuickFix());
            }
        });

        return problemsHolder;
    }

    private static class RemoveExtraBlankLineQuickFix implements LocalQuickFix {

        @NotNull
        @Override
        public String getName() {
            return "Remove extra blank line";
        }

        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            try {
                PsiElement psiElement = descriptor.getPsiElement();

                PsiElement newPsiElement = DotEnvFactory.createFromText(project, TokenType.WHITE_SPACE, "\n\n");

                psiElement.replace(newPsiElement);
            } catch (IncorrectOperationException e) {
                Logger.getInstance(ExtraBlankLineInspection.class).error(e);
            }
        }

        @NotNull
        public String getFamilyName() {
            return getName();
        }
    }
}
