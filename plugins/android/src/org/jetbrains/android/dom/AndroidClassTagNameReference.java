package org.jetbrains.android.dom;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.facet.SimpleClassMapConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidClassTagNameReference extends TagNameReference {
  public AndroidClassTagNameReference(ASTNode nameElement, boolean startTagFlag) {
    super(nameElement, startTagFlag);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiClass;
    XmlTag tagElement = getTagElement();
    assert tagElement != null;
    String[] tagNames = SimpleClassMapConstructor.getInstance().getTagNamesByClass((PsiClass)element);
    String tagName = tagNames.length > 0 ? tagNames[0] : null;
    return tagElement.setName(tagName != null ? tagName : "");
  }
}
