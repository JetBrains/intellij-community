package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.htmlAddLabelToForm.CreateNewLabelAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HtmlRequiredTitleAttributeInspection extends HtmlLocalInspectionTool {
  private static final Set<String>
    ourElementsWithoutTitle = Set.of("frame", "iframe", "dl", "a", "router-link");
  private static final String TITLE = "title";

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HtmlUtil.isHtmlTagContainingFile(tag)) {
      return;
    }
    final String tagName = StringUtil.toLowerCase(tag.getName());
    if (ourElementsWithoutTitle.contains(tagName)) {

      final XmlAttribute title = tag.getAttribute(TITLE);
      if (title != null) {
        return;
      }
      List<LocalQuickFix> fixes = new ArrayList<>();
      if (!ourElementsWithoutTitle.contains(tag.getName())) {
        fixes.add(new CreateNewLabelAction(tag.getName()));
      }
      if (holder.isOnTheFly()) {
        fixes.add(new InsertRequiredAttributeFix(tag, TITLE) {
          @Override
          public @NotNull String getText() {
            return HtmlToolsBundle.message("html.intention.insert.attribute", TITLE);
          }
        });
      }
      InspectionUtils.RegisterProblem(tag, holder, fixes, HtmlToolsBundle.message("html.inspections.check.required.title"),
                                      ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
  }

  @Override
  public @NotNull String getShortName() {
    return "HtmlRequiredTitleAttribute";
  }
}
