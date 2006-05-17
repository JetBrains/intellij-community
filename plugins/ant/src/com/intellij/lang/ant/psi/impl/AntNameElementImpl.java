package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;

class AntNameElementImpl extends AntElementImpl {

  public AntNameElementImpl(AntElement parent, XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public String getText() {
    return getName();
  }

  public String getName() {
    XmlElement element = getSourceElement();
    if (element == null) {
      element = null;
    }
    PsiElement parent = element.getParent();
    if (parent == null) {
      parent = null;
    }
    return ((XmlAttribute)parent).getValue();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    ((XmlAttribute)getSourceElement().getParent()).setValue(name);
    subtreeChanged();
    return this;
  }
}
