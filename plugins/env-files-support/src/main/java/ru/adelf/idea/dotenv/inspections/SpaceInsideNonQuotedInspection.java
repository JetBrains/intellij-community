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
import ru.adelf.idea.dotenv.psi.DotEnvTypes;
import ru.adelf.idea.dotenv.psi.DotEnvValue;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class SpaceInsideNonQuotedInspection extends LocalInspectionTool {
    // Change the display name within the plugin.xml
    // This needs to be here as otherwise the tests will throw errors.
    @NotNull
    @Override
    public String getDisplayName() {
        return "Space inside non-quoted value";
    }

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

        PsiTreeUtil.findChildrenOfType(file, DotEnvValue.class).forEach(dotEnvValue -> {
            // first child VALUE_CHARS -> non quoted value
            // first child QUOTE -> quoted value
            if(dotEnvValue.getFirstChild().getNode().getElementType() == DotEnvTypes.VALUE_CHARS) {
                if (dotEnvValue.getText().trim().contains(" ")) {
                    problemsHolder.registerProblem(dotEnvValue, "Space inside allowed only for quoted values", addQuotesQuickFix);
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

            // counting each quote type " AND '. The quickfix will use the most common quote type.
            String quote;
            Supplier<Stream<DotEnvValue>> supplier = () -> PsiTreeUtil.findChildrenOfType(descriptor.getPsiElement().getContainingFile(), DotEnvValue.class)
                    .stream()
                    .filter(dotEnvValue -> dotEnvValue.getFirstChild().getNode().getElementType() == DotEnvTypes.QUOTE);
            long total = supplier.get().count();
            long doubleQuoted = supplier.get().filter(dotEnvValue -> dotEnvValue.getFirstChild().getText().contains("\"")).count();
            long singleQuoted = total - doubleQuoted;
            if (doubleQuoted > singleQuoted) {
                quote = "\"";
            } else {
                quote = "'";
            }

            try {
                DotEnvValue valueElement = (DotEnvValue) descriptor.getPsiElement();

                PsiElement newValueElement = DotEnvFactory.createFromText(project, DotEnvTypes.VALUE, "DUMMY=" + quote + valueElement.getText() + quote);

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
