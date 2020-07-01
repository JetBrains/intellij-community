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
import ru.adelf.idea.dotenv.DotEnvFactory;
import ru.adelf.idea.dotenv.psi.DotEnvFile;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;
import ru.adelf.idea.dotenv.psi.DotEnvValue;

public class SpaceInsideNonQuotedInspection extends LocalInspectionTool {

    private AddQuotesQuickFix addQuotesQuickFix = new AddQuotesQuickFix();

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

        PsiTreeUtil.findChildrenOfType(file, DotEnvProperty.class).forEach(dotEnvProperty -> {
            if (dotEnvProperty.getValueText().trim().contains(" ")) {
                DotEnvValue value = dotEnvProperty.getValue();

                if (value != null) {
                    PsiElement firstChild = value.getFirstChild();

                    if (firstChild != null && !firstChild.getText().equals("\"")) {
                        problemsHolder.registerProblem(value, "Space inside allowed only for quoted values", addQuotesQuickFix);
                    }
                }
            }
        });

        return problemsHolder;
    }

    private static class AddQuotesQuickFix implements LocalQuickFix {

        @NotNull
        @Override
        public String getName() {
            return "Add quotes";
        }

        /**
         * Adds quotes to DotEnvValue element
         *
         * @param project    The project that contains the file being edited.
         * @param descriptor A problem found by this inspection.
         */
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            try {
                DotEnvValue valueElement = (DotEnvValue) descriptor.getPsiElement();

                PsiElement newValueElement = DotEnvFactory.createFromText(project, DotEnvTypes.VALUE, "DUMMY=\"" + valueElement.getText() + "\"");

                valueElement.getNode().getTreeParent().replaceChild(valueElement.getNode(), newValueElement.getNode());
            } catch (IncorrectOperationException e) {
                Logger.getInstance(SpaceInsideNonQuotedInspection.class).error(e);
            }
        }

        @NotNull
        public String getFamilyName() {
            return getName();
        }
    }
}
