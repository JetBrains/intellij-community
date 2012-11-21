package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
class SetAttributeQuickFix implements AndroidLintQuickFix {

  private final String myName;
  private final String myAttributeName;
  private final String myValue;

  SetAttributeQuickFix(@NotNull String name, @NotNull String attributeName, @Nullable String value) {
    super();
    myName = name;
    myAttributeName = attributeName;
    myValue = value;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @Nullable Editor editor) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
    
    if (tag == null) {
      return;
    }
    final XmlAttribute attribute = tag.setAttribute(myAttributeName, SdkConstants.NS_RESOURCES, "");

    if (attribute != null) {
      if (myValue != null) {
        attribute.setValue(myValue);
      }
      if (editor != null) {
        final XmlAttributeValue valueElement = attribute.getValueElement();
        final TextRange valueTextRange = attribute.getValueTextRange();

        if (valueElement != null && valueTextRange != null) {
          final int valueElementStart = valueElement.getTextRange().getStartOffset();
          editor.getCaretModel().moveToOffset(valueElementStart + valueTextRange.getStartOffset());

          if (valueTextRange.getStartOffset() < valueTextRange.getEndOffset()) {
            editor.getSelectionModel().setSelection(valueElementStart + valueTextRange.getStartOffset(),
                                                    valueElementStart + valueTextRange.getEndOffset());
          }
        }
      }
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement, @NotNull PsiElement endElement, boolean inBatchMode) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
    if (tag == null) {
      return false;
    }
    return tag.getAttribute(myAttributeName,  SdkConstants.NS_RESOURCES) == null;
  }
}
