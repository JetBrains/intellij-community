package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

class AntNameElementImpl extends AntElementImpl {

  public AntNameElementImpl(AntElement parent, XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public String getText() {
    return getName();
  }

  public String getName() {
    XmlAttribute attr = getAttribute();
    return (attr == null) ? null : attr.getValue();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    final XmlAttribute attr = getAttribute();
    if (attr == null) {
      throw new IncorrectOperationException("AntNameElement should wrap a XmlElement with a XmlAttribute available on the path to root!");
    }
    attr.setValue(name);
    return this;
  }

  @Nullable
  private XmlAttribute getAttribute() {
    return PsiTreeUtil.getParentOfType(getSourceElement(), XmlAttribute.class);
  }
}
