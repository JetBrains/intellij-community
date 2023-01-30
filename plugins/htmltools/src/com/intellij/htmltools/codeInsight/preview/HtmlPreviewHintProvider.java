package com.intellij.htmltools.codeInsight.preview;

import com.intellij.codeInsight.preview.ColorPreviewComponent;
import com.intellij.codeInsight.preview.ImagePreviewComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.io.URLUtil;
import com.intellij.xml.util.ColorMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;


public class HtmlPreviewHintProvider extends BaseHtmlPreviewHintProvider {
  @Override
  public JComponent getPreviewComponent(@NotNull PsiElement element) {
    if (element.getParent() instanceof XmlAttributeValue) {
      final PsiElement parentParent = element.getParent().getParent();

      if (parentParent instanceof XmlAttribute) {
        XmlAttribute attribute = ((XmlAttribute)parentParent);
        final String attrName = attribute.getName();

        if ("alink".equalsIgnoreCase(attrName) ||
            "link".equalsIgnoreCase(attrName) ||
            "text".equalsIgnoreCase(attrName) ||
            "vlink".equalsIgnoreCase(attrName) ||
            StringUtil.containsIgnoreCase(attrName, "color")) {
          String s = element.getText();  // TODO: support [#FFF, #FFF]

          if (s.length() > 0) {
            final String hexColor = (s.charAt(0) == '#') ? s : ColorMap.getHexCodeForColorName(StringUtil.toLowerCase(s));
            if (hexColor != null) {
              try {
                return new ColorPreviewComponent(Color.decode("0x" + hexColor.substring(1)));
              }
              catch (NumberFormatException e) {
                return null;
              }
            }
          }
        }

        final PsiElement attributeValue = element.getParent();
        if (attributeValue.getParent() instanceof XmlAttribute) {
          if ("background".equalsIgnoreCase(attrName) || "src".equalsIgnoreCase(attrName) || "href".equalsIgnoreCase(attrName)) {
            final String attrValue = attribute.getValue();
            if (attrValue != null && URLUtil.isDataUri(attrValue)) {
              return getPreviewFromDataUri(attrValue);
            }

            PsiElement parent = element;
            while (parent != null && parent != attribute) {
              final JComponent c = ImagePreviewComponent.getPreviewComponent(parent);
              if (c != null) {
                return c;
              }

              parent = parent.getParent();
            }
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static JComponent getPreviewFromDataUri(@NotNull String dataUri) {
    JComponent preview = null;
    final byte[] imageBytes = URLUtil.getBytesFromDataUri(dataUri);
    if (imageBytes != null) {
      try {
        preview = ImagePreviewComponent.getPreviewComponent(ImagePreviewComponent.readImageFromBytes(imageBytes), imageBytes.length);
      }
      catch (IOException ignored) {
      }
    }
    return preview;
  }
}
