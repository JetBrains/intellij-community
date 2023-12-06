// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.inspections;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProcessingInstruction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.codeInsight.JavaFxImportsOptimizer;

import java.util.*;

public final class JavaFxUnusedImportsInspection extends XmlSuppressableInspectionTool {
  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, final boolean isOnTheFly) {
    if (!JavaFxFileTypeFactory.isFxml(file)) return null;
    final XmlDocument document = ((XmlFile)file).getDocument();
    if (document == null) return null;
    final Set<String> usedNames = new HashSet<>();
    file.accept(new JavaFxImportsOptimizer.JavaFxUsedClassesVisitor() {
      @Override
      protected void appendClassName(String fqn) {
        usedNames.add(fqn);
        final String packageName = StringUtil.getPackageName(fqn);
        if (!StringUtil.isEmpty(packageName)) {
          usedNames.add(packageName);
        }
      }

      @Override
      protected void appendDemandedPackageName(@NotNull String packageName) {
        usedNames.add(packageName);
      }
    });

    final InspectionManager inspectionManager = InspectionManager.getInstance(file.getProject());

    final List<ProblemDescriptor> problems = new ArrayList<>();
    final Collection<XmlProcessingInstruction> instructions =
      PsiTreeUtil.findChildrenOfType(document.getProlog(), XmlProcessingInstruction.class);
    final Map<String, XmlProcessingInstruction> targetProcessingInstructions = new LinkedHashMap<>();
    for (XmlProcessingInstruction instruction : instructions) {
      final String target = JavaFxPsiUtil.getInstructionTarget("import", instruction);
      if (target != null) {
        targetProcessingInstructions.put(target, instruction);
      }
    }
    for (String target : targetProcessingInstructions.keySet()) {
      final XmlProcessingInstruction instruction = targetProcessingInstructions.get(target);
      if (target.endsWith(".*")) {
        if (!usedNames.contains(StringUtil.trimEnd(target, ".*"))) {
          problems.add(inspectionManager
                         .createProblemDescriptor(instruction, JavaFXBundle.message("inspection.javafx.unused.imports.problem"), new JavaFxOptimizeImportsFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
        }
      }
      else if (!usedNames.contains(target) || targetProcessingInstructions.containsKey(StringUtil.getPackageName(target) + ".*")) {
        problems.add(inspectionManager
                       .createProblemDescriptor(instruction, JavaFXBundle.message("inspection.javafx.unused.imports.problem"), new JavaFxOptimizeImportsFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly));
      }
    }
    return problems.isEmpty() ? null : problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static final class JavaFxOptimizeImportsFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return QuickFixBundle.message("optimize.imports.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement == null) return;
      final PsiFile file = psiElement.getContainingFile();
      if (file == null || !JavaFxFileTypeFactory.isFxml(file)) return;
      new JavaFxImportsOptimizer().processFile(file).run();
    }
  }
}
