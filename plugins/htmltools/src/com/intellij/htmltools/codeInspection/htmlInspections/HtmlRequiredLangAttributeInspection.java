package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.InsertRequiredAttributeFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class HtmlRequiredLangAttributeInspection extends HtmlLocalInspectionTool {

  private static final String LANG = "lang";
  private static final String HTML = "html";
  private static final String XML_LANG = "xml:lang";
  private static final String XHTML_1 = "DTD XHTML 1.0";
  private static final String XHTML_11 = "DTD XHTML 1.1";

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HtmlUtil.isHtmlTagContainingFile(tag)) return;
    final XmlAttribute lang = tag.getAttribute(LANG);
    final XmlAttribute xmlLang = tag.getAttribute(XML_LANG);
    final String tagName = tag.getName();
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (!tagName.equalsIgnoreCase(HTML)) return;
    //HTML only offers the use of the lang attribute, while XHTML 1.0 allows both attributes, and XHTML 1.1 allows only xml:lang.
    if (tag.getContainingFile().getFileType() == XHtmlFileType.INSTANCE) {
      XmlDocument doc = PsiTreeUtil.getContextOfType(tag, XmlDocument.class, true);
      //Do not inspect the xhtml file without doctype
      if (doc == null || doc.getProlog() == null || doc.getProlog().getDoctype() == null || doc.getProlog().getDoctype().getPublicId() == null) return;
      if (doc.getProlog().getDoctype().getPublicId().contains(XHTML_1)) {
        if (xmlLang != null || lang != null) return;
        if (isOnTheFly) fixes.add(new InsertRequiredAttributeFix(tag, XML_LANG));
      }
      else if (doc.getProlog().getDoctype().getPublicId().contains(XHTML_11)) {
        if (tag.getAttribute(XML_LANG) != null) return;
        if (isOnTheFly) fixes.add(new InsertRequiredAttributeFix(tag, XML_LANG));
      }
    }
    else if (tag.getContainingFile().getFileType() == HtmlFileType.INSTANCE) {
      if (tag.getAttribute(LANG) != null) return;
      if (isOnTheFly) fixes.add(new InsertRequiredAttributeFix(tag, LANG));
    }
    else {
      return;
    }

    InspectionUtils.RegisterProblem(tag, holder, fixes, HtmlToolsBundle.message("html.inspections.check.required.lang"),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  public @NotNull String getShortName() {
    return "HtmlRequiredLangAttribute";
  }
}
