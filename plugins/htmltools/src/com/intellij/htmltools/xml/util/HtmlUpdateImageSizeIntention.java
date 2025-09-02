package com.intellij.htmltools.xml.util;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.HtmlUtil;
import org.intellij.images.util.ImageInfo;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HtmlUpdateImageSizeIntention extends BaseIntentionAction {
  private boolean myUseElementToTheLeft = false;
  
  @Override
  public @Nls @NotNull String getFamilyName() {
    return HtmlToolsBundle.message("html.intention.update.image.size");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    final int offset = editor.getCaretModel().getOffset();
    if (isAvailable(getTag(psiFile, offset))) {
      return true;
    }

    if (offset > 0 && isAvailable(getTag(psiFile, offset - 1))) {
      myUseElementToTheLeft = true;
      return true;
    }
    return false;
  }

  public boolean isAvailable(@Nullable XmlTag tag) {
    if (tag == null) {
      return false;
    }
    ImageInfo imageInfo = getImageInfo(tag);
    if (imageInfo == null || imageInfo.height == 0 || imageInfo.width == 0) {
      return false;
    }

    final String widthValue = tag.getAttributeValue(HtmlReferenceProvider.SizeReference.WIDTH_ATTR_NAME);
    final String heightValue = tag.getAttributeValue(HtmlReferenceProvider.SizeReference.HEIGHT_ATTR_NAME);
    setText(widthValue != null || heightValue != null
            ? HtmlToolsBundle.message("html.intention.update.image.size")
            : HtmlToolsBundle.message("html.intention.insert.image.size"));

    return imageInfo.width != HtmlReferenceProvider.SizeReference.getSizeValue(widthValue, tag) ||
           imageInfo.height != HtmlReferenceProvider.SizeReference.getSizeValue(heightValue, tag);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    final XmlTag tag = getTag(psiFile, myUseElementToTheLeft ? offset - 1 : offset);
    if (tag == null) {
      return;
    }

    final ImageInfo imageInfo = getImageInfo(tag);
    if (imageInfo != null && imageInfo.height != 0 && imageInfo.width != 0) {
      tag.setAttribute(HtmlReferenceProvider.SizeReference.WIDTH_ATTR_NAME, String.valueOf(imageInfo.width));
      tag.setAttribute(HtmlReferenceProvider.SizeReference.HEIGHT_ATTR_NAME, String.valueOf(imageInfo.height));
    }
  }

  private static @Nullable ImageInfo getImageInfo(@NotNull XmlTag xmlTag) {
    if (HtmlReferenceProvider.SizeReference.IMAGE_TAG_NAME.equalsIgnoreCase(xmlTag.getName())) {
      return HtmlReferenceProvider.SizeReference.getImageInfo(xmlTag);
    }
    return null;
  }


  private static @Nullable XmlTag getTag(@NotNull PsiFile file, int offset) {
    if (!HtmlUtil.hasHtml(file)) {
      return null;
    }

    return PsiTreeUtil.getNonStrictParentOfType(file.getViewProvider().findElementAt(offset, HTMLLanguage.INSTANCE), XmlTag.class);
  }
}
