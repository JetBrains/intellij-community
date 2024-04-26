package com.intellij.htmltools.refactoring.lang.html;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.refactoring.lang.ExtractIncludeFileBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtractIncludeFromHTMLHandler extends ExtractIncludeFileBase<XmlTagChild> {
  private static final Logger LOG = Logger.getInstance(ExtractIncludeFromHTMLHandler.class);

  @Override
  public boolean isAvailableForFile(@NotNull PsiFile file) {
    // Shouldn't work for inherited languages like Vue or Angular2HTML
    return file.getLanguage() == HTMLLanguage.INSTANCE || file.getLanguage() == XHTMLLanguage.INSTANCE;
  }

  @Override
  protected void doReplaceRange(final String includePath, final XmlTagChild first, final XmlTagChild last) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final XmlTag parentTag = first.getParentTag();
      LOG.assertTrue(parentTag != null);
      try {
        final XmlElementDescriptor descriptor = parentTag.getDescriptor();
        LOG.assertTrue(descriptor != null);
        final @NonNls String name = descriptor.getName();
        if (HtmlUtil.SCRIPT_TAG_NAME.equals(name)) {
          parentTag.setAttribute("src", includePath);
          LOG.assertTrue(first.getParent() == parentTag);
          parentTag.deleteChildRange(first, last);
        }
        else if (HtmlUtil.STYLE_TAG_NAME.equals(name)) {
          final XmlTag linkTag = parentTag.createChildTag("link", parentTag.getNamespace(), null, false);
          linkTag.setAttribute("href", includePath);
          linkTag.setAttribute("rel", "stylesheet");
          final String type = parentTag.getAttributeValue("type");
          if (type != null) {
            linkTag.setAttribute("type", type);
          }
          final String media = parentTag.getAttributeValue("media");
          if (media != null) {
            linkTag.setAttribute("media", media);
          }
          parentTag.replace(linkTag);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    });
  }

  @Override
  protected boolean verifyChildRange(final XmlTagChild first, final XmlTagChild last) {
    final Language language = first.getLanguage();

    if (first != last ||
        Language.findInstance(HTMLLanguage.class).equals(language) ||
        Language.findInstance(XHTMLLanguage.class).equals(language)
      ) {
      return false;
    }

    final XmlTag parentTag = first.getParentTag();
    return parentTag != null && isAvailableOnDescriptor(parentTag.getDescriptor());
  }

  @Override
  protected @Nullable Pair<XmlTagChild, XmlTagChild> findPairToExtract(final int start, final int end) {
    Pair<XmlTagChild, XmlTagChild> range = XmlUtil.findTagChildrenInRange(myIncludingFile, start, end);
    if (range != null) {
      if (range.first == range.second && range.first instanceof XmlTag) {
        if (isAvailableOnDescriptor(((XmlTag)range.first).getDescriptor())) {
          XmlTagChild[] children = ((XmlTag)range.first).getValue().getChildren();
          if (children.length > 0) {
            return Pair.create(ArrayUtil.getFirstElement(children), ArrayUtil.getLastElement(children));
          }
        }
      }
    }
    return range;
  }

  private static boolean isAvailableOnDescriptor(@Nullable XmlElementDescriptor descriptor) {
    if (descriptor == null) return false;
    final @NonNls String name = descriptor.getName();
    return HtmlUtil.STYLE_TAG_NAME.equals(name) || HtmlUtil.SCRIPT_TAG_NAME.equals(name);
  }
}
