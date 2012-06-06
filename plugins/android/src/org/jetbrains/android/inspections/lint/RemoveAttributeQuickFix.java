package org.jetbrains.android.inspections.lint;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
class RemoveAttributeQuickFix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
    if (attribute != null) {
      attribute.getParent().setAttribute(attribute.getName(), null);
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.inspections.remove.attribute");
  }
}
