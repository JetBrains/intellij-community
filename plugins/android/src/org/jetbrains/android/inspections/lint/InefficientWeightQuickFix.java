package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class InefficientWeightQuickFix implements AndroidLintQuickFix {

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    final XmlTag parentTag = tag.getParentTag();
    if (parentTag == null) {
      return;
    }
    String attrName;

    if (AndroidLintUtil.ATTR_VALUE_VERTICAL
      .equals(parentTag.getAttributeValue(AndroidLintUtil.ATTR_ORIENTATION, SdkConstants.NS_RESOURCES))) {
      attrName = AndroidLintUtil.ATTR_LAYOUT_HEIGHT;
    }
    else {
      attrName = AndroidLintUtil.ATTR_LAYOUT_WIDTH;
    }
    tag.setAttribute(attrName, SdkConstants.NS_RESOURCES, "0dp");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return false;
    }
    return tag.getParentTag() != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.inspections.replace.with.zero.dp");
  }
}
