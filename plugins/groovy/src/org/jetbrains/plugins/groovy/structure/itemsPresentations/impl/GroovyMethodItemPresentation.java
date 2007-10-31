package org.jetbrains.plugins.groovy.structure.itemsPresentations.impl;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.structure.GroovyElementPresentation;
import org.jetbrains.plugins.groovy.structure.itemsPresentations.GroovyItemPresentation;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 31.10.2007
 */
public class GroovyMethodItemPresentation extends GroovyItemPresentation {
  private final boolean isInherit;

  public GroovyMethodItemPresentation(PsiMethod myElement, boolean isInherit) {
    super(myElement);
    this.isInherit = isInherit;
  }

  public String getPresentableText() {
    return GroovyElementPresentation.getMethodPresentableText(((PsiMethod) myElement));
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public Icon getIcon(boolean open) {
       return myElement.isValid() ? myElement.getIcon(Iconable.ICON_FLAG_OPEN) : null;
     }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return isInherit ? CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES : null;
  }
}
