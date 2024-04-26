package com.intellij.htmltools.codeInspection.htmlInspections.htmltagreplace;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class ReplaceAppletTagAction implements LocalQuickFix {
  private static final class Holder {
    private static final @NonNls Set<String> ourObjectAttributes = new HashSet<>();

    static {
      ourObjectAttributes.addAll(Arrays.asList("align", "height", "hspace", "title", "vspace", "width"));
    }
  }

  @Override
  public @NotNull String getName() {
    return HtmlToolsBundle.message("html.replace.tag.with.another.quickfix.text", "applet", "object");
  }

  @Override
  public @NonNls @NotNull String getFamilyName() {
    return "ReplaceDeprecatedTag";
  }

  @SuppressWarnings("ALL")
  private static PsiElement[] generateContainingElements(@NotNull Project project, String tagName) {
    final XmlFile xmlFile = HtmlTagReplaceUtil.genereateXmlFileWithSingleTag(project, "object");
    return HtmlTagReplaceUtil.getXmlNamesFromSingleTagFile(xmlFile);
  }

  @Override
  public void applyFix(final @NotNull Project project, final @NotNull ProblemDescriptor descriptor) {
    PsiElement parent = descriptor.getPsiElement();
    while (parent != null) {
      if (parent instanceof XmlTag && "applet".equals(StringUtil.toLowerCase(((XmlTag)parent).getLocalName()))) {
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

    for (XmlAttribute attribute : tag.getAttributes()) {
      String attName = attribute.getName();
      if (!"object".equals(attName) && !Holder.ourObjectAttributes.contains(attName)) {
        final String attributeValue = attribute.getValue();
        String value = attributeValue != null ? attributeValue.trim() : "";
        if ("code".equals(attName) && value.endsWith(".class")) {
          value = value.substring(0, value.length() - ".class".length());
        }
        XmlFile file = HtmlTagReplaceUtil.generateXmlFile(project, "<param name=\""+ attName + "\" value=\""+value+"\" />");
        PsiElement element  = file.getDocument().getRootTag();
        assert element != null;

        for (PsiElement psiElement : tag.getChildren()) {
          if (psiElement instanceof XmlToken) {
            IElementType type = ((XmlToken)psiElement).getTokenType();
            if (type == XmlTokenType.XML_TAG_END) {
              tag.add(element);
              break;
            }
          }
        }

        if (!"name".equals(attName)) {
          attribute.delete();
        }
      } else if ("object".equals(attName)){
        attribute.setName("data");
      }
    }

    StringBuilder builder = new StringBuilder();
    builder.append("<a>");
    builder.append("\n<!--[if !IE]> -->");
    builder.append("<object");
    for (XmlAttribute attribute : tag.getAttributes()) {
      if (!"classid".equals(StringUtil.toLowerCase(attribute.getName())) && !"codebase".equals(StringUtil.toLowerCase(attribute.getName()))) {
        builder.append(" ");
        builder.append(StringUtil.toLowerCase(attribute.getName()));
        builder.append("=\"");
        builder.append(attribute.getValue());
        builder.append("\"");
      }
    }
    builder.append(">");
    boolean contentStarted = false;
    for (PsiElement element : tag.getChildren()) {
      if (element instanceof XmlToken) {
        if (((XmlToken)element).getTokenType() == XmlTokenType.XML_TAG_END) {
          contentStarted = true;
        } else if (((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
          break;
        }
      } else if (contentStarted) {
        builder.append(element.getText());
      }
    }
    builder.append("</object>");
    builder.append("\n<!-- <![endif]-->\n");
    builder.append("</a>");

    XmlFile file = HtmlTagReplaceUtil.generateXmlFile(project, builder.toString());
    XmlTag additionalTagElements  = file.getDocument().getRootTag();
    assert additionalTagElements != null;

    for (PsiElement element : tag.getChildren()) {
      if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
        contentStarted = false;
        for (PsiElement psiElement : additionalTagElements.getChildren()) {
          if (psiElement instanceof XmlToken) {
            if (((XmlToken)psiElement).getTokenType() == XmlTokenType.XML_TAG_END) {
              contentStarted = true;
            } else if (((XmlToken)psiElement).getTokenType() == XmlTokenType.XML_END_TAG_START) {
              break;
            }
          } else if (contentStarted) {
            if (psiElement instanceof XmlTag) {
              ((XmlTag)psiElement).setAttribute("type", "application/x-java-applet");
            }
            tag.addBefore(psiElement, element);
          }
        }
      }
    }

    tag.setAttribute("classid", "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93");
    tag.setAttribute("codebase", "http://java.sun.com/products/plugin/autodl/jinstall-1_4-windows-i586.cab#Version=1,4,0,0");
  }
}
