package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlCustomElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HtmlRequiredAltAttributeInspection extends HtmlLocalInspectionTool {
  private static final @NonNls Set<String> htmlTagsWithRequiredAltAttribute;
  private static final String ALT = "alt";

  static {
    htmlTagsWithRequiredAltAttribute = Set.of("area", "img", "input", "applet");
  }

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HtmlUtil.isHtmlTagContainingFile(tag)) return;
    if (tag.getAttribute(ALT) != null) return;
    if (XmlExtension.getExtension(tag.getContainingFile()).isRequiredAttributeImplicitlyPresent(tag, ALT)) return;
    if (XmlCustomElementDescriptor.isCustomElement(tag)) return;
    if (!htmlTagsWithRequiredAltAttribute.contains(tag.getName())) return;
    //H36
    if (tag.getName().equalsIgnoreCase("input")) {
      final XmlAttribute buttonWithImageType = tag.getAttribute("type");
      if (buttonWithImageType != null && buttonWithImageType.getValue() != null) {
        if (!buttonWithImageType.getValue().equalsIgnoreCase("image")) return;
      }
      else {
        return;
      }
    }
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (isOnTheFly) fixes.add(new InsertRequiredAttributeFix(tag, ALT));

    InspectionUtils.RegisterProblem(tag, holder, fixes, HtmlToolsBundle.message("html.inspections.check.required.alt"),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public @NotNull String getShortName() {
    return "HtmlRequiredAltAttribute";
  }
}
