package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.GlobalInspectionUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.documentation.mdn.MdnSymbolDocumentation;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.html.dtd.HtmlAttributeDescriptorImpl;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.documentation.mdn.MdnDocumentationKt.getHtmlMdnDocumentation;
import static com.intellij.webSymbols.WebSymbolApiStatus.isDeprecatedOrObsolete;

public final class HtmlDeprecatedAttributeInspection extends HtmlLocalInspectionTool {

  @Override
  protected void checkAttribute(@NotNull XmlAttribute attribute, @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (HtmlUtil.isHtmlTagContainingFile(attribute)) {
      if (!HtmlUtil.isHtml5Context(attribute)) {
        return;
      }
      XmlAttributeDescriptor descriptor = attribute.getDescriptor();
      if (descriptor == null || descriptor.getClass() != HtmlAttributeDescriptorImpl.class) {
        return;
      }

      String name = ((HtmlAttributeDescriptorImpl)descriptor).isCaseSensitive() ? attribute.getName() :
                    StringUtil.toLowerCase(attribute.getName());
      MdnSymbolDocumentation documentation = getHtmlMdnDocumentation(attribute, null);
      boolean deprecatedInHtml5 = "align".equals(name)
                                  || (documentation != null && isDeprecatedOrObsolete(documentation.getApiStatus()));
      if (!deprecatedInHtml5) {
        return;
      }
      holder.registerProblem(attribute.getNameElement(),
                             GlobalInspectionUtil.createInspectionMessage(HtmlToolsBundle.message("html.inspections.deprecated.attribute")),
                             ProblemHighlightType.LIKE_DEPRECATED
      );
    }
  }
}
