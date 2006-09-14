package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

class AntNameElementImpl extends AntElementImpl {

  public AntNameElementImpl(final AntElement parent, final XmlAttributeValue sourceElement) {
    super(parent, sourceElement);
  }

  @NotNull
  public XmlAttributeValue getSourceElement() {
    return (XmlAttributeValue)super.getSourceElement();
  }

  public String getName() {
    return getSourceElement().getValue();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    final XmlAttribute attr = PsiTreeUtil.getParentOfType(getSourceElement(), XmlAttribute.class);
    if (attr != null) {
      attr.setValue(name);
    }
    return this;
  }

}
