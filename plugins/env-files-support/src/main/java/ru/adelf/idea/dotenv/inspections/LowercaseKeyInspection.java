package ru.adelf.idea.dotenv.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvBundle;
import ru.adelf.idea.dotenv.DotEnvFactory;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.psi.DotEnvKey;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;

import java.util.Locale;

public class LowercaseKeyInspection extends LocalInspectionTool {
    // Change the display name within the plugin.xml
    // This needs to be here as otherwise the tests will throw errors.
    @Override
    public @NotNull String getDisplayName() {
        return DotEnvBundle.message("inspection.name.key.uses.lowercase.chars");
    }

    @Override
    public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        if (!(file instanceof DotEnvFile)) {
            return null;
        }

        return analyzeFile(file, manager, isOnTheFly).getResultsArray();
    }

    private static @NotNull ProblemsHolder analyzeFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        ProblemsHolder problemsHolder = new ProblemsHolder(manager, file, isOnTheFly);

        PsiTreeUtil.findChildrenOfType(file, DotEnvKey.class).forEach(dotEnvKey -> {
            if (dotEnvKey.getText().matches(".*[a-z].*")) {
                problemsHolder.registerProblem(dotEnvKey,
                                               DotEnvBundle.message("inspection.message.key.uses.lowercase.chars.only.keys.with.uppercase.chars.are.allowed")/*,
                        new ForceUppercaseQuickFix()*/
                );
            }
        });

        return problemsHolder;
    }

    private static class ForceUppercaseQuickFix implements LocalQuickFix {

        @Override
        public @NotNull String getName() {
            return DotEnvBundle.message("intention.name.change.to.uppercase");
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            try {
                PsiElement psiElement = descriptor.getPsiElement();

                PsiElement newPsiElement = DotEnvFactory.createFromText(project, DotEnvTypes.KEY,
                                                                        psiElement.getText().toUpperCase(Locale.ROOT) + "=dummy");

                psiElement.replace(newPsiElement);
            } catch (IncorrectOperationException e) {
                Logger.getInstance(IncorrectDelimiterInspection.class).error(e);
            }
        }

        @Override
        public @NotNull String getFamilyName() {
            return getName();
        }
    }
}
