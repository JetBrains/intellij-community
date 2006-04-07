package com.intellij.lang.ant.psi;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public interface AntTask extends AntElement, PsiNamedElement {

  @NotNull
  XmlTag getSourceElement();

  String[] getAttributeNames();

  Class getAttributeType(final String attributeName);

  String[] getNestedElements();

  Class getNestedElementType(final String nestedElementName);
}
