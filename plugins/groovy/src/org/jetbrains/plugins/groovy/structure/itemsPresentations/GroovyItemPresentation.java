package org.jetbrains.plugins.groovy.structure.itemsPresentations;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
* Date: 30.10.2007
*/
public abstract class GroovyItemPresentation implements ItemPresentation {
  protected final PsiElement myElement;

  protected GroovyItemPresentation(PsiElement myElement) {
    this.myElement = myElement;
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
    return null;
  }
}
