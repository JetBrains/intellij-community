package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class HtmlRequiredSummaryAttributeInspection extends HtmlLocalInspectionTool {

  private static final String TABLE = "table";
  private static final String SUMMARY = "summary";


  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HtmlUtil.isHtmlTagContainingFile(tag)) return;
    final String tagName = StringUtil.toLowerCase(tag.getName());
    if (!tagName.equalsIgnoreCase(TABLE)) return;
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (tag.getAttribute(SUMMARY) != null) return;
    if (isOnTheFly) {
      fixes.add(new InsertRequiredAttributeFix(tag, SUMMARY));
    }

    InspectionUtils
      .RegisterProblem(tag, holder, fixes, HtmlToolsBundle.message("html.inspections.check.required.summary"),
                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public @NotNull String getShortName() {
    return super.getShortName();
  }
}
