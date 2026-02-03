package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlCustomElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HtmlRequiredTitleElementInspection extends HtmlLocalInspectionTool {
  private static final String TITLE = "title";
  private static final String HEAD = "head";

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (InjectedLanguageManager.getInstance(holder.getProject()).isInjectedFragment(holder.getFile())) {
      // not inside injected code
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return super.buildVisitor(holder, isOnTheFly);
  }

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HtmlUtil.isHtmlTagContainingFile(tag)) return;
    if (!tag.getName().equalsIgnoreCase(HEAD)) return;
    if (XmlCustomElementDescriptor.isCustomElement(tag)) return;

    XmlTag[] subTags = tag.getSubTags();
    long count = Arrays.stream(subTags).filter((element) -> element.getName().equalsIgnoreCase(TITLE)).count();
    if (count > 0) return;
    List<LocalQuickFix> fixes = new ArrayList<>();
    fixes.add(new CreateRequiredSubElement(tag.getOriginalElement(), TITLE));

    InspectionUtils.RegisterProblem(tag, holder, fixes, HtmlToolsBundle.message("html.inspections.check.required.title.element"),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }
}
