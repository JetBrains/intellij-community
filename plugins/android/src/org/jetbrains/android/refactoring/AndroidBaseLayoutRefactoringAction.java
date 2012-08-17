package org.jetbrains.android.refactoring;

import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidBaseLayoutRefactoringAction extends AndroidBaseXmlRefactoringAction {

  @Override
  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    for (XmlTag tag : tags) {
      if (getLayoutViewElement(tag) == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean isMyFile(PsiFile file) {
    return DomManager.getDomManager(file.getProject()).getDomFileDescription((XmlFile)file)
      instanceof LayoutDomFileDescription;
  }

  @Nullable
  public static LayoutViewElement getLayoutViewElement(@NotNull XmlTag tag) {
    final DomElement domElement = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
    return domElement instanceof LayoutViewElement
           ? (LayoutViewElement)domElement
           : null;
  }
}
