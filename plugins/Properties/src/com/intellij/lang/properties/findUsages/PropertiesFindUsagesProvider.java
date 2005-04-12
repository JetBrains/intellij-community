package com.intellij.lang.properties.findUsages;

import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyKey;
import com.intellij.lang.properties.psi.PropertyValue;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 14, 2005
 * Time: 6:44:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFindUsagesProvider implements FindUsagesProvider {
  public boolean canFindUsagesFor(PsiElement psiElement) {
    return psiElement instanceof PsiNamedElement;
  }

  public String getHelpId(PsiElement psiElement) {
    return null;
  }

  public String getType(PsiElement element) {
    if (element instanceof Property) return "property";
    if (element instanceof PropertyKey) return "property key";
    if (element instanceof PropertyValue) return "property value";
    return "";
  }

  public String getDescriptiveName(PsiElement element) {
    return ((PsiNamedElement)element).getName();
  }

  public String getNodeText(PsiElement element, boolean useFullName) {
    return getDescriptiveName(element);
  }
}
