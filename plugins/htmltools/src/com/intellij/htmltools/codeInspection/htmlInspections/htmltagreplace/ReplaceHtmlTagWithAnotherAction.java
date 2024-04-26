package com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class ReplaceHtmlTagWithAnotherAction implements LocalQuickFix {
  private final @NonNls String myName;
  public ReplaceHtmlTagWithAnotherAction(String name) {
    myName = name;
  }

  private static final class Holder {
    private static final @NonNls Map<String, String> ourTagToNewTagMap = new HashMap<>();

    static {
      ourTagToNewTagMap.put("i", "em");
      ourTagToNewTagMap.put("b", "strong");
      ourTagToNewTagMap.put("s", "del");
      ourTagToNewTagMap.put("strike", "del");
      ourTagToNewTagMap.put("tt", "samp");
      ourTagToNewTagMap.put("u", "cite");
      ourTagToNewTagMap.put("xmp", "pre");
      ourTagToNewTagMap.put("menu", "ul");
    }
  }

  @Override
  public @NotNull String getName() {
    return HtmlToolsBundle.message("html.replace.tag.with.another.quickfix.text", myName, Holder.ourTagToNewTagMap.get(myName));
  }

  @Override
  public @NonNls @NotNull String getFamilyName() {
    return HtmlToolsBundle.message("html.replace.tag.with.another.quickfix.family.name");
  }

  @SuppressWarnings("ALL")
  private static PsiElement[] generateContainingElements(@NotNull Project project, String tagName) {
    final XmlFile xmlFile = HtmlTagReplaceUtil.genereateXmlFileWithSingleTag(project, Holder.ourTagToNewTagMap.get(tagName));
    return HtmlTagReplaceUtil.getXmlNamesFromSingleTagFile(xmlFile);
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    PsiElement parent = descriptor.getPsiElement();
    while (parent != null) {
      if (parent instanceof XmlTag && myName.equals(StringUtil.toLowerCase(((XmlTag)parent).getLocalName()))) {
        break;
      }
      parent = parent.getParent();
    }
    if (parent == null) {
      return;
    }
    final String name = StringUtil.toLowerCase(((XmlTag)parent).getLocalName());
    XmlTag tag = (XmlTag)parent;
    PsiElement[] replacePsiElements = generateContainingElements(project, name);
    int cnt = 0;
    for (PsiElement element : tag.getChildren()) {
      if (element instanceof XmlToken token) {
        IElementType type = token.getTokenType();
        if (type == XmlTokenType.XML_NAME) {
          token.replace(replacePsiElements[cnt++]);
        }
      }
    }
  }
}