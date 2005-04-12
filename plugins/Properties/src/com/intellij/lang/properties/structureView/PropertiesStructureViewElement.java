package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:26:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesStructureViewElement implements StructureViewTreeElement {
  private PsiElement myElement;

  protected PropertiesStructureViewElement(final PsiElement element) {
    myElement = element;
  }

  public Object getValue() {
    return myElement;
  }

  public void navigate(boolean requestFocus) {
    ((NavigationItem)myElement).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return ((NavigationItem)myElement).canNavigate();
  }

  public boolean canNavigateToSource() {
    return ((NavigationItem)myElement).canNavigateToSource();
  }

  public StructureViewTreeElement[] getChildren() {
    final List<PsiElement> childrenElements = new ArrayList<PsiElement>();
    final PsiElement[] children = myElement.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child instanceof Property) {
        childrenElements.add(child);
      }
    }
    StructureViewTreeElement[] elems = new StructureViewTreeElement[childrenElements.size()];
    for (int i = 0; i < childrenElements.size(); i++) {
      final PsiElement element = childrenElements.get(i);
      elems[i] = new PropertiesStructureViewElement(element);
    }

    return elems;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return ((PsiNamedElement)myElement).getName();
      }

      public TextAttributesKey getTextAttributesKey() {
        if (myElement instanceof Property) {
          return PropertiesHighlighter.PROPERTY_KEY;
        }
        return null;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return myElement.getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }
}
