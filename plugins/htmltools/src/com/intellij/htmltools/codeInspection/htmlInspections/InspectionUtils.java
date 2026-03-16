package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class InspectionUtils {
  public static void RegisterProblem(@NotNull XmlTag tag,
                                     @NotNull ProblemsHolder holder,
                                     @NotNull List<@NotNull LocalQuickFix> fixes,
                                     @InspectionMessage String text,
                                     @NotNull ProblemHighlightType severity) {
    PsiElement toRegister = XmlTagUtil.getStartTagNameElement(tag);
    if (toRegister == null) return;
    InspectionManager manager = holder.getManager();
    ProblemDescriptor descriptor = manager
      .createProblemDescriptor(toRegister, toRegister, text,
                               severity, holder.isOnTheFly(),
                               fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    holder.registerProblem(descriptor);
  }
}
