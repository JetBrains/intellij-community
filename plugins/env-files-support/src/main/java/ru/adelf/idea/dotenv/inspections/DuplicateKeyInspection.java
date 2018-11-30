package ru.adelf.idea.dotenv.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvPsiElementsVisitor;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;
import ru.adelf.idea.dotenv.psi.DotEnvFile;

import java.util.*;

public class DuplicateKeyInspection extends LocalInspectionTool {
    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        if(!(file instanceof DotEnvFile)) {
            return null;
        }

        return analyzeFile(file, manager, isOnTheFly).getResultsArray();
    }

    @NotNull
    private ProblemsHolder analyzeFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        DotEnvPsiElementsVisitor visitor = new DotEnvPsiElementsVisitor();
        file.acceptChildren(visitor);

        ProblemsHolder problemsHolder = new ProblemsHolder(manager, file, isOnTheFly);

        Map<String, PsiElement> existingKeys = new HashMap<>();
        Set<PsiElement> markedElements = new HashSet<>();
        for(KeyValuePsiElement keyValue : visitor.getCollectedItems()) {
            if(existingKeys.containsKey(keyValue.getKey())) {
                problemsHolder.registerProblem(keyValue.getElement(), "Duplicate key");

                PsiElement markedElement = existingKeys.get(keyValue.getKey());
                if(!markedElements.contains(markedElement)) {
                    problemsHolder.registerProblem(markedElement, "Duplicate key");
                    markedElements.add(markedElement);
                }
            } else {
                existingKeys.put(keyValue.getKey(), keyValue.getElement());
            }
        }

        return problemsHolder;
    }
}
