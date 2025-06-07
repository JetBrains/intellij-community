package com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class ReplaceFontTagAction implements LocalQuickFix {
  private static final class Holder {
    private static final @NonNls Map<String, String> ourSizesMap = new HashMap<>();

    static {
      ourSizesMap.put("-3", "59%");
      ourSizesMap.put("-2", "70%");
      ourSizesMap.put("-1", "smaller");
      ourSizesMap.put("+1", "larger");
      ourSizesMap.put("+2", "144%");
      ourSizesMap.put("+3", "172%");

      ourSizesMap.put("1", "xx-small");
      ourSizesMap.put("2", "x-small");
      ourSizesMap.put("3", "small");
      ourSizesMap.put("4", "medium");
      ourSizesMap.put("5", "large");
      ourSizesMap.put("6", "x-large");
      ourSizesMap.put("7", "xx-large");
    }
  }

  @Override
  public @NotNull String getName() {
    return HtmlToolsBundle.message("html.replace.tag.with.css.quickfix.text", "font");
  }

  @Override
  public @NonNls @NotNull String getFamilyName() {
    return "ReplaceDepracatedTag";
  }

  @SuppressWarnings("ALL")
  private static PsiElement[] generateContainingElements(@NotNull Project project, String tagName) {
    final XmlFile xmlFile = HtmlTagReplaceUtil.genereateXmlFileWithSingleTag(project, "span");
    return HtmlTagReplaceUtil.getXmlNamesFromSingleTagFile(xmlFile);
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    PsiElement parent = descriptor.getPsiElement();
    while (parent != null) {
      if (parent instanceof XmlTag && "font".equals(StringUtil.toLowerCase(((XmlTag)parent).getLocalName()))) {
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

    StringBuilder style = new StringBuilder();
    for (XmlAttribute attribute : tag.getAttributes()) {
      if ("face".equals(attribute.getName())) {
        String value = attribute.getValue();
        attribute.delete();
        style.append("font-family: ");
        style.append(value);
        style.append("; ");
      }
      else if ("color".equals(attribute.getName())) {
        String value = attribute.getValue();
        attribute.delete();
        style.append("color: ");
        style.append(value);
        style.append("; ");
      } else if ("size".equals(attribute.getName())) {
        String value = attribute.getValue();
        attribute.delete();
        if (Holder.ourSizesMap.containsKey(value)) {
          style.append("font-size: ");
          style.append(Holder.ourSizesMap.get(value));
          style.append("; ");
        }
      }
    }

    if (!style.isEmpty()) {
      boolean found = false;
      for (XmlAttribute attribute : tag.getAttributes()) {
        if (HtmlUtil.STYLE_ATTRIBUTE_NAME.equals(attribute.getName())) {
          found = true;
          attribute.setValue(attribute.getValue() + " " + style);
          break;
        }
      }
      if (!found) {
        tag.setAttribute(HtmlUtil.STYLE_ATTRIBUTE_NAME, style.toString());
      }
    }
  }
}