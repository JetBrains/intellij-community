package com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class ReplaceHtmlTagWithCssAction implements LocalQuickFix {
  private final @NonNls String myName;

  private static final @NonNls String BODY = "body";
  private static final @NonNls String HTML = "html";
  private static final @NonNls String HEAD = "head";

  private static final class Holder {
    private static final @NonNls Map<String, String> ourTagToCssMap = new HashMap<>();

    static {
      ourTagToCssMap.put("center", "text-align: center;");
      ourTagToCssMap.put("i", "font-style: italic;");
      ourTagToCssMap.put("b", "font-weight: bold;");
      ourTagToCssMap.put("big", "font-size: large;");
      ourTagToCssMap.put("small", "font-size: small;");
      ourTagToCssMap.put("tt", "font-family: monospace;");
      ourTagToCssMap.put("u", "text-decoration: underline;");
      ourTagToCssMap.put("s", "text-decoration: line-through;");
      ourTagToCssMap.put("strike", "text-decoration: line-through;");
      ourTagToCssMap.put("xmp", "white-space: pre;");
    }
  }

  public ReplaceHtmlTagWithCssAction(String name) {
    myName = name;
  }

  @Override
  public @NotNull String getName() {
    return HtmlToolsBundle.message("html.replace.tag.with.css.quickfix.text", myName);
  }

  public @NotNull String getText() {
    return getName();
  }

  @Override
  public @NonNls @NotNull String getFamilyName() {
    return HtmlToolsBundle.message("html.replace.tag.with.css.quickfix.family.name");
  }


  @SuppressWarnings("ALL")
  private static PsiElement[] generateContainingElements(@NotNull Project project, boolean blocklevel) {
    final XmlFile xmlFile = HtmlTagReplaceUtil.genereateXmlFileWithSingleTag(project, blocklevel ? "div" : "span");
    return HtmlTagReplaceUtil.getXmlNamesFromSingleTagFile(xmlFile);
  }

  private static void addCssAttribute(XmlTag tag, String name) {
    String s = tag.getAttributeValue(HtmlUtil.STYLE_ATTRIBUTE_NAME);
    String addString = Holder.ourTagToCssMap.get(name);
    if (s == null) {
      s = addString;
    }
    else {
      for (int i = s.length() - 1; i >= 0; i--) {
        if (!Character.isSpaceChar(s.charAt(i))) {
          if (s.charAt(i) != ';') {
            s += ';';
          }
          break;
        }
      }
      s += " " + addString;
    }
    tag.setAttribute(HtmlUtil.STYLE_ATTRIBUTE_NAME, s);
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    PsiElement parent = descriptor.getPsiElement();
    while (parent != null) {
      if (parent instanceof XmlTag && Holder.ourTagToCssMap.containsKey(StringUtil.toLowerCase(((XmlTag)parent).getLocalName()))) {
        break;
      }
      parent = parent.getParent();
    }
    if (parent == null) {
      return;
    }
    XmlTag tag = (XmlTag)parent;
    XmlTag parentTag = PsiTreeUtil.getParentOfType(tag, XmlTag.class, true);
    boolean toReplaceWithSpan = parentTag == null
                                || HtmlUtil.isInlineTagContainer(parentTag.getLocalName(), false);
    if (!toReplaceWithSpan) {
      String name = StringUtil.toLowerCase(parentTag.getLocalName());
      if (BODY.equals(name) || HTML.equals(name) || HEAD.equals(name)) {
        toReplaceWithSpan = true;
      }
      else {
        int childrenTagCount = 0;
        for (PsiElement child : parentTag.getChildren()) {
          if (child instanceof XmlTag) {
            childrenTagCount++;
          }
          if (child instanceof XmlText) {
            childrenTagCount += 2;
          }
        }
        toReplaceWithSpan = childrenTagCount > 1;
      }
    }

    final String tagName = StringUtil.toLowerCase(((XmlTag)parent).getLocalName());
    if (!toReplaceWithSpan) {
      addCssAttribute(parentTag, tagName);
      PsiElement[] elements = tag.getChildren();
      boolean started = false;
      for (PsiElement psiElement : elements) {
        if (psiElement instanceof XmlToken xmlToken) {
          IElementType type = xmlToken.getTokenType();
          if (type == XmlTokenType.XML_TAG_END) {
            started = true;
            continue;
          }
          else if (type == XmlTokenType.XML_END_TAG_START) {
            break;
          }
        }
        if (started) {
          parentTag.addBefore(psiElement, tag);
        }
      }
      tag.delete();
    }
    else {
      PsiElement[] elements = generateContainingElements(project, HtmlUtil.isHtmlBlockTag(tagName, false));
      int cnt = 0;
      for (PsiElement psiElement : tag.getChildren()) {
        if (psiElement instanceof XmlToken) {
          IElementType type = ((XmlToken)psiElement).getTokenType();
          if (type == XmlTokenType.XML_NAME) {
            psiElement.replace(elements[cnt++]);
          }
        }
      }
      addCssAttribute(tag, tagName);
    }
  }
}
