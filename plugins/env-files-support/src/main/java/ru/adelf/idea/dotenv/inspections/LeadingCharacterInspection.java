package ru.adelf.idea.dotenv.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvBundle;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.psi.DotEnvKey;

public class LeadingCharacterInspection extends LocalInspectionTool {
    // Change the display name within the plugin.xml
    // This needs to be here as otherwise the tests will throw errors.
    @Override
    public @NotNull String getDisplayName() {
        return DotEnvBundle.message("inspection.name.invalid.leading.character");
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
            // Also accepts lower case chars as keys with lower case chars are handled by another inspection
            // same for dash (-> IncorrectDelimiter
            if (!dotEnvKey.getText().matches("[A-Za-z_-].*")){
                problemsHolder.registerProblem(dotEnvKey,
                                               DotEnvBundle.message("inspection.message.invalid.first.char.for.key.only.z.are.allowed"));
            }
        });

        return problemsHolder;
    }

}
