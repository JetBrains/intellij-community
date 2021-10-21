package ru.adelf.idea.dotenv.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvFactory;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;

import java.util.regex.Pattern;

public class SpaceAroundSeparatorInspection extends LocalInspectionTool {
    // Change the display name within the plugin.xml
    // This needs to be here as otherwise the tests will throw errors.
    @NotNull
    @Override
    public String getDisplayName() {
        return "Extra spaces surrounding '='";
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

        PsiTreeUtil.findChildrenOfType(file, DotEnvProperty.class).forEach(dotEnvProperty -> {
            
            String key = dotEnvProperty.getKey().getText();
            String value = dotEnvProperty.getValue() != null ? dotEnvProperty.getValue().getText() : "";
            String separator = dotEnvProperty.getText()
                    .replaceFirst("^" + Pattern.quote(key) , "")
                    .replaceFirst(Pattern.quote(value) + "$", "");

            if (separator.matches("([ \t]+=.*)|(.*=[ \t]+)")) {
                problemsHolder.registerProblem(dotEnvProperty,
                        new TextRange(
                                dotEnvProperty.getKey().getText().length(), 
                                dotEnvProperty.getKey().getText().length() + separator.length()
                        ),
                        "Extra spaces surrounding '='",
                        new RemoveSpaceAroundSeparatorQuickFix()
                );
            }
        });

        return problemsHolder;
    }

    private static class RemoveSpaceAroundSeparatorQuickFix implements LocalQuickFix {

        @NotNull
        @Override
        public String getName() {
            return "Remove spaces surrounding '='";
        }

        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            try {
                DotEnvProperty dotEnvProperty = (DotEnvProperty) descriptor.getPsiElement();

                String key = dotEnvProperty.getKey().getText();
                String value = dotEnvProperty.getValue() != null ? dotEnvProperty.getValue().getText() : "";

                PsiElement newPsiElement = DotEnvFactory.createFromText(
                        project,
                        DotEnvTypes.PROPERTY,
                        key + "=" + value
                );

                dotEnvProperty.replace(newPsiElement);
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
