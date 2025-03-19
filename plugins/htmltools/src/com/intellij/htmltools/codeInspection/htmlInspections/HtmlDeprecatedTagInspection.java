package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.GlobalInspectionUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.htmlInspections.HtmlLocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.SwitchToHtml5Action;
import com.intellij.documentation.mdn.MdnSymbolDocumentation;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace.ReplaceAppletTagAction;
import com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace.ReplaceFontTagAction;
import com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace.ReplaceHtmlTagWithAnotherAction;
import com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace.ReplaceHtmlTagWithCssAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.documentation.mdn.MdnDocumentationKt.getHtmlMdnDocumentation;
import static com.intellij.webSymbols.WebSymbolApiStatus.isDeprecatedOrObsolete;

public final class HtmlDeprecatedTagInspection extends HtmlLocalInspectionTool {
  private static final @NonNls Set<String> ourHtmlReplaceableTags;
  private static final @NonNls Set<String> ourCssReplaceableTags;

  static {
    ourHtmlReplaceableTags = new HashSet<>();
    ourHtmlReplaceableTags.addAll(Arrays.asList("s", "strike", "u", "menu"));
    ourCssReplaceableTags = new HashSet<>();
    ourCssReplaceableTags.addAll(Arrays.asList("center", "u", "s", "strike", "xmp"));
  }

  static void registerTag(@NotNull XmlTag tag,
                          @NotNull String message,
                          @NotNull ProblemsHolder holder,
                          LocalQuickFix[] fixes,
                          @NotNull ProblemHighlightType type) {
    if (fixes == null) {
      return;
    }
    final PsiElement startTagNameElement = XmlTagUtil.getStartTagNameElement(tag);
    String description = GlobalInspectionUtil.createInspectionMessage(HtmlToolsBundle.message(message));
    if (startTagNameElement != null) {
      holder.registerProblem(startTagNameElement, description, type, fixes);
    }
    final PsiElement endTagNameElement = XmlTagUtil.getEndTagNameElement(tag);
    if (endTagNameElement != null) {
      holder.registerProblem(endTagNameElement, description, type, fixes);
    }
  }

  @Override
  protected void checkTag(final @NotNull XmlTag tag, final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    if (HtmlUtil.isHtmlTagContainingFile(tag)) {
      XmlElementDescriptor descriptor = tag.getDescriptor();
      if (!(descriptor instanceof HtmlElementDescriptorImpl) || !((HtmlElementDescriptorImpl)descriptor).isDeprecated()) {
        return;
      }
      String name = tag.isCaseSensitive() ?
                    tag.getName() :
                    StringUtil.toLowerCase(tag.getName());

      LocalQuickFix[] fixes;
      if ("font".equals(name)) {
        fixes = new LocalQuickFix[]{new ReplaceFontTagAction()};
      }
      else if ("applet".equals(name)) {
        fixes = new LocalQuickFix[]{new ReplaceAppletTagAction()};
      }
      else if (ourCssReplaceableTags.contains(name)) {
        fixes = new LocalQuickFix[]{new ReplaceHtmlTagWithCssAction(name)};
        if (ourHtmlReplaceableTags.contains(name)) {
          fixes = new LocalQuickFix[]{fixes[0], new ReplaceHtmlTagWithAnotherAction(name)};
        }
      }
      else if (ourHtmlReplaceableTags.contains(name)) {
        fixes = new LocalQuickFix[]{new ReplaceHtmlTagWithAnotherAction(name)};
      }
      else {
        fixes = LocalQuickFix.EMPTY_ARRAY;
      }

      MdnSymbolDocumentation documentation = getHtmlMdnDocumentation(tag, null);
      boolean deprecatedInHtml5 = documentation != null && isDeprecatedOrObsolete(documentation.getApiStatus());
      boolean inHtml5 = HtmlUtil.isHtml5Context(tag);
      if (!inHtml5 && !deprecatedInHtml5 && !HtmlUtil.hasNonHtml5Doctype(tag)) {
        fixes = ArrayUtil.append(fixes, new SwitchToHtml5Action());
      }

      registerTag(tag, "html.inspections.deprecated.tag", holder, fixes, ProblemHighlightType.LIKE_DEPRECATED);
    }
  }
}
