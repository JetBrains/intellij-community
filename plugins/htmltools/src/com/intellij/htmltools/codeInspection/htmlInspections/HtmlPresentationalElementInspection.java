package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace.ReplaceHtmlTagWithAnotherAction;
import com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace.ReplaceHtmlTagWithCssAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class HtmlPresentationalElementInspection extends HtmlLocalInspectionTool {
  @NonNls private static final Set<String> ourCssReplaceableTags;
  @NonNls private static final Set<String> ourHtmlReplaceableTagsHtml4;
  @NonNls private static final Set<String> ourHtmlReplaceableTagsHtml5;

  static {
    ourHtmlReplaceableTagsHtml4 = Set.of("i", "b", "tt");
    ourHtmlReplaceableTagsHtml5 = Set.of("i", "b");
    ourCssReplaceableTags = Set.of("i", "b", "big", "small", "tt");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "HtmlPresentationalElement";
  }

  @Override
  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final String name = StringUtil.toLowerCase(tag.getName());
    if (HtmlUtil.isHtmlTagContainingFile(tag)) {
      if (HtmlUtil.isHtml5Context(tag) && !ourHtmlReplaceableTagsHtml5.contains(tag.getName())) {
        return;
      }
      LocalQuickFix[] fixes = null;
      if (ourCssReplaceableTags.contains(name)) {
        fixes = new LocalQuickFix[]{new ReplaceHtmlTagWithCssAction(name)};
        if (ourHtmlReplaceableTagsHtml4.contains(name)) {
          fixes = new LocalQuickFix[]{fixes[0], new ReplaceHtmlTagWithAnotherAction(name)};
        }
      }
      else if (ourHtmlReplaceableTagsHtml4.contains(name)) {
        fixes = new LocalQuickFix[]{new ReplaceHtmlTagWithAnotherAction(name)};
      }
      HtmlDeprecatedTagInspection
        .registerTag(tag, "html.inspections.check.presentational.tag", holder, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
  }
}
