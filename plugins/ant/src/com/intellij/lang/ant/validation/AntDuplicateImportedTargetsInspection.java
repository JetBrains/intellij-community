package com.intellij.lang.ant.validation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AntDuplicateImportedTargetsInspection extends AntInspection {

  @NonNls private static final String SHORT_NAME = "AntDuplicateImportedTargetsInspection";

  @Nls
  @NotNull
  public String getDisplayName() {
    return AntBundle.message("ant.duplicate.imported.targets.inspection");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return SHORT_NAME;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof AntFile) {
      final AntProject project = ((AntFile)file).getAntProject();
      if (project != null) {
        final AntTarget[] targets = project.getTargets();
        if (targets.length > 0) {
          final HashMap<String, AntTarget> name2Target = new HashMap<String, AntTarget>();
          for (final AntTarget target : targets) {
            name2Target.put(target.getName(), target);
          }
          final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
          final AntTarget[] importedTargets = project.getImportedTargets();
          for (final AntTarget target : importedTargets) {
            final String name = target.getName();
            final AntTarget t = name2Target.get(name);
            if (t != null) {
              final String duplicatedMessage =
                AntBundle.message("target.is.duplicated.in.imported.file", name, target.getAntFile().getName());
              problems
                .add(manager.createProblemDescriptor(t, duplicatedMessage, EMPTY_FIXES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
          final int prolemCount = problems.size();
          if (prolemCount > 0) {
            return problems.toArray(new ProblemDescriptor[prolemCount]);
          }
        }
      }
    }
    return null;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }
}
